package com.sorenkai.infrastructure.repo

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import com.sorenkai.infrastructure.mongo.MongoClientFactory
import com.sorenkai.infrastructure.mongo.MongoConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Integration tests for MongoDiscussionRepository backed by Testcontainers MongoDB.
 */
@OptIn(ExperimentalTime::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoDiscussionRepositoryTest {
    private lateinit var container: MongoDBContainer

    private class TestMongoProvider(
        uri: String,
        dbName: String,
    ) : IMongoDatabaseProvider {
        private val client =
            MongoClientFactory.create(
                MongoConfig(
                    uri = "$uri/$dbName",
                    maxPool = 5,
                    minPool = 0,
                    waitQueueMs = 1000,
                    connectTimeoutMs = 2000,
                    socketTimeoutMs = 2000,
                    serverSelectionMs = 2000,
                    retryWrites = false,
                ),
            )
        override val database: MongoDatabase = client.getDatabase(dbName)

        override fun <T : Any> getCollection(
            name: String,
            clazz: Class<T>,
        ) = database.getCollection(name, clazz)
    }

    @BeforeAll
    fun startMongo() {
        container = MongoDBContainer(DockerImageName.parse("mongo:6.0"))
        container.start()
    }

    @AfterAll
    fun stopMongo() {
        container.stop()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `insert and findById roundtrip`() =
        runBlocking {
            val repo = MongoDiscussionRepository(TestMongoProvider(container.connectionString, "testdb1"))
            val now = Clock.System.now()
            val d =
                Discussion(
                    kind = Kind.POST,
                    userId = "u1",
                    body = "hello",
                    createdAt = now,
                    updatedAt = now,
                )
            repo.insert(d)
            val loaded = repo.findById(d.id)
            assertNotNull(loaded)
            assertEquals(d.body, loaded!!.body)
            assertEquals(d.createdAt.epochSeconds, loaded.createdAt.epochSeconds)
        }

    @Test
    fun `listPosts respects pinned and createdAt desc`() =
        runBlocking {
            val repo = MongoDiscussionRepository(TestMongoProvider(container.connectionString, "testdb2"))
            val base = Clock.System.now()
            // insert 3 posts, one pinned, different times
            val p1 = repo.insert(Discussion(kind = Kind.POST, userId = "u", body = "a", createdAt = base, updatedAt = base))
            val p2 =
                repo.insert(
                    Discussion(
                        kind = Kind.POST,
                        userId = "u",
                        body = "b",
                        createdAt = base + 1_000.milliseconds,
                        updatedAt =
                            base + 1_000.milliseconds,
                    ),
                )
            val p3 =
                repo.insert(
                    Discussion(
                        kind = Kind.POST,
                        userId = "u",
                        body = "c",
                        createdAt = base + 2_000.milliseconds,
                        updatedAt =
                            base + 2_000.milliseconds,
                    ),
                )
            // pin p1
            repo.setPinned(p1.id, true)
            val list = repo.listPosts(null, null, setOf(DiscussionsStatus.VISIBLE), 10, 0)
            assertEquals(list.first().id, p1.id, "Pinned first")
            // remaining should be by createdAt desc (p3 then p2)
            assertEquals(list[1].id, p3.id)
            assertEquals(list[2].id, p2.id)
        }

    @Test
    fun `listComments respects createdAt asc and pagination`() =
        runBlocking {
            val repo = MongoDiscussionRepository(TestMongoProvider(container.connectionString, "testdb3"))
            val base = Clock.System.now()
            val parent = repo.insert(Discussion(kind = Kind.POST, userId = "u", body = "p", createdAt = base, updatedAt = base))
            val c1 =
                repo.insert(
                    Discussion(
                        kind = Kind.COMMENT,
                        parentId = parent.id,
                        userId = "u",
                        body = "c1",
                        createdAt =
                            base + 1_000.milliseconds,
                        updatedAt = base + 1_000.milliseconds,
                    ),
                )
            val c2 =
                repo.insert(
                    Discussion(
                        kind = Kind.COMMENT,
                        parentId = parent.id,
                        userId = "u",
                        body = "c2",
                        createdAt =
                            base + 2_000.milliseconds,
                        updatedAt = base + 2_000.milliseconds,
                    ),
                )
            val c3 =
                repo.insert(
                    Discussion(
                        kind = Kind.COMMENT,
                        parentId = parent.id,
                        userId = "u",
                        body = "c3",
                        createdAt =
                            base + 3_000.milliseconds,
                        updatedAt = base + 3_000.milliseconds,
                    ),
                )

            val page1 = repo.listComments(parent.id, setOf(DiscussionsStatus.VISIBLE), 2, 0)
            assertEquals(listOf(c1.id, c2.id), page1.map { it.id })

            val page2 = repo.listComments(parent.id, setOf(DiscussionsStatus.VISIBLE), 2, 2)
            assertEquals(listOf(c3.id), page2.map { it.id })
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `setStatus sets and unsets moderation fields`() =
        runBlocking {
            val repo = MongoDiscussionRepository(TestMongoProvider(container.connectionString, "testdb4"))
            val d = repo.insert(Discussion(kind = Kind.POST, userId = "u", body = "x", createdAt = Clock.System.now()))
            val now = Clock.System.now()
            // set status with moderator and reason
            val ok = repo.setStatus(d.id, DiscussionsStatus.HIDDEN_BY_MODERATOR, moderatedBy = "mod1", moderatedAt = now, reason = "spam")
            assertTrue(ok)
            val after = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.HIDDEN_BY_MODERATOR, after.status)
            assertEquals("mod1", after.moderatedBy)
            assertEquals("spam", after.reason)
            assertNotNull(after.moderatedAt)
            // unset reason and moderator
            val ok2 = repo.setStatus(d.id, DiscussionsStatus.VISIBLE, moderatedBy = null, moderatedAt = null, reason = null)
            assertTrue(ok2)
            val after2 = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.VISIBLE, after2.status)
            assertNull(after2.moderatedBy)
            assertNull(after2.moderatedAt)
            assertNull(after2.reason)
        }
}
