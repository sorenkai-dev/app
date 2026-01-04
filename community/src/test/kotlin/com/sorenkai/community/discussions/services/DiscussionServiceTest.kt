package com.sorenkai.community.discussions.services

import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.dto.discussions.DiscussionResponsePublic
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private class FakeLikeRepository : ILikeRepository {
    val store = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun like(
        targetId: String,
        userId: UID,
    ) {
        store.computeIfAbsent(targetId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlike(
        targetId: String,
        userId: UID,
    ) {
        store[targetId]?.remove(userId)
    }

    override suspend fun countLikes(targetIds: List<String>): Map<String, Long> =
        targetIds.associateWith { tid -> store[tid]?.size?.toLong() ?: 0L }

    override suspend fun getLikedTargets(
        targetIds: List<String>,
        userId: UID,
    ): Set<String> = targetIds.filter { tid -> store[tid]?.contains(userId) == true }.toSet()
}

@OptIn(ExperimentalTime::class)
private class FakeDiscussionRepository : IDiscussionRepository {
    private val store = ConcurrentHashMap<String, Discussion>()

    override suspend fun insert(discussion: Discussion): Discussion {
        store[discussion.id] = discussion
        return discussion
    }

    override suspend fun findById(id: String): Discussion? = store[id]

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> =
        store.values
            .filter { it.kind == Kind.POST }
            .filter { statuses.contains(it.status) }
            .filter { if (writingId == null) it.writingId.isNullOrBlank() else it.writingId == writingId }
            .filter { if (lang == null) true else it.lang == lang }
            .sortedWith(compareByDescending<Discussion> { it.isPinned }.thenByDescending { it.createdAt })
            .drop(offset)
            .take(limit)

    override suspend fun listComments(
        parentId: String,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> =
        store.values
            .filter { it.kind == Kind.COMMENT && it.parentId == parentId }
            .filter { statuses.contains(it.status) }
            .sortedBy { it.createdAt }
            .drop(offset)
            .take(limit)

    @OptIn(ExperimentalTime::class)
    override suspend fun updateBody(
        id: String,
        body: String,
        updatedAt: kotlin.time.Instant,
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(body = body, updatedAt = updatedAt)
        return true
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun setStatus(
        id: String,
        status: DiscussionsStatus,
        moderatedBy: String?,
        moderatedAt: kotlin.time.Instant?,
        reason: String?,
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(status = status, moderatedBy = moderatedBy, moderatedAt = moderatedAt, reason = reason)
        return true
    }

    override suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult {
        val cur = store[id] ?: return UpdateResult.Failure("Discussion not found")
        store[id] = cur.copy(isPinned = pinned)
        return UpdateResult.Success
    }

    override suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult {
        val cur = store[id] ?: return UpdateResult.Failure("Discussion not found")
        store[id] = cur.copy(isLocked = locked)
        return UpdateResult.Success
    }
}

@OptIn(ExperimentalTime::class)
class DiscussionServiceTest {
    @OptIn(ExperimentalTime::class)
    @Test
    fun create_post_success_and_projection() =
        runBlocking {
            val repo = FakeDiscussionRepository()
            val likes = FakeLikeRepository()
            val service = DiscussionService(repo, likes, writings = null, postRateLimiter = null)
            val resp: DiscussionResponsePublic =
                service.create(
                    uid = "user-1",
                    req = DiscussionCreateRequest(kind = Kind.POST, body = "Hello world"),
                )
            assertEquals("Hello world", resp.body)
            // Public projection should not expose moderatedBy/At (not present in type)
            assertNotNull(resp.createdAt)
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun admin_set_status_sets_moderatedAt_internally() =
        runBlocking {
            val repo = FakeDiscussionRepository()
            val likes = FakeLikeRepository()
            val service = DiscussionService(repo, likes, null)
            val d =
                repo.insert(
                    Discussion(
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            val ok =
                service.setStatus(
                    actorUid = "mod-1",
                    id = d.id,
                    status = DiscussionsStatus.HIDDEN_BY_MODERATOR,
                    reason = "spam",
                )
            assertTrue(ok)
            val after = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.HIDDEN_BY_MODERATOR, after.status)
            assertEquals("mod-1", after.moderatedBy)
            assertNotNull(after.moderatedAt)
            assertEquals("spam", after.reason)
        }

    @Test
    fun like_unlike_idempotent() =
        runBlocking {
            val repo = FakeDiscussionRepository()
            val likes = FakeLikeRepository()
            val service = DiscussionService(repo, likes, null)
            val d = repo.insert(Discussion(kind = Kind.POST, userId = "u1", body = "x", createdAt = Clock.System.now()))
            val r1 = service.like("user-1", d.id)
            assertEquals(1, r1.likesCount)
            val r2 = service.like("user-1", d.id)
            assertEquals(1, r2.likesCount)
            val r3 = service.unlike("user-1", d.id)
            assertEquals(0, r3.likesCount)
            val r4 = service.unlike("user-1", d.id)
            assertEquals(0, r4.likesCount)
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun edit_and_delete_own() =
        runBlocking {
            val repo = FakeDiscussionRepository()
            val likes = FakeLikeRepository()
            val service = DiscussionService(repo, likes, null)
            val d =
                repo.insert(
                    Discussion(
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            val edited = service.editOwn("u1", d.id, "y")
            assertTrue(edited)
            val deleted = service.deleteOwn("u1", d.id)
            assertTrue(deleted)
            val after = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.DELETED_BY_AUTHOR, after.status)
        }

    @Test
    fun listRoot_filters_by_language() =
        runBlocking {
            val repo = FakeDiscussionRepository()
            val likes = FakeLikeRepository()
            val service = DiscussionService(repo, likes, null)

            repo.insert(Discussion(kind = Kind.POST, body = "En", lang = "en", userId = "u1", createdAt = Clock.System.now()))
            repo.insert(Discussion(kind = Kind.POST, body = "Es", lang = "es", userId = "u1", createdAt = Clock.System.now()))

            val enResult = service.listRoot(10, null, lang = "en").first
            assertEquals(1, enResult.size)
            assertEquals("En", enResult[0].body)

            val esResult = service.listRoot(10, null, lang = "es").first
            assertEquals(1, esResult.size)
            assertEquals("Es", esResult[0].body)

            val allResult = service.listRoot(10, null, lang = null).first
            assertEquals(2, allResult.size)
        }
}
