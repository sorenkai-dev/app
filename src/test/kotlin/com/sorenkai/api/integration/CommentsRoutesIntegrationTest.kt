package com.sorenkai.api.integration

import com.sorenkai.api.model.Comment
import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.CommentRepository
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.storiesRoutes
import com.sorenkai.api.services.CommentService
import com.sorenkai.api.services.StoryService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CommentsRoutesIntegrationTest {

    @BeforeTest
    fun setupAuthBypass() {
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "test-user")
        System.setProperty("TEST_AUTH_ROLES", "user")
    }

    @AfterTest
    fun clearAuthBypass() {
        System.clearProperty("ENV")
        System.clearProperty("TEST_AUTH_UID")
        System.clearProperty("TEST_AUTH_ROLES")
    }

    @Test
    fun `POST v1-stories-id-comments creates comment and returns 201`() = testApplication {
        val seeded = Story(title = "Hello", url = "https://example.com", createdBy = "seed")
        application {
            install(ContentNegotiation) { json() }
            val storyRepo = object : StoryRepository() {
                private val items = mutableListOf<Story>(seeded)
                override suspend fun ensureIndexes() {}
                override suspend fun create(story: Story): Story { items.add(story); return story }
                override suspend fun findById(id: String): Story? = items.find { it.id == id }
                override suspend fun recent(limit: Int): List<Story> = items.take(limit)
                override suspend fun recentPage(pageSize: Int, afterCursor: String?) =
                    (if (items.size <= pageSize) items else items.take(pageSize)) to null
            }
            val commentRepo = object : CommentRepository() {
                private val items = mutableListOf<Comment>()
                override suspend fun ensureIndexes() {}
                override suspend fun create(comment: Comment): Comment { items.add(comment); return comment }
                override suspend fun listByStoryPage(storyId: String, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.storyId == storyId }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val storyService = StoryService(storyRepo, AllowAllLimiter)
            val commentService = CommentService(commentRepo, storyRepo, AllowAllLimiter)
            routing { this.storiesRoutes(storyService, commentService) }
        }

        val response = client.post("/v1/stories/${seeded.id}/comments") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"Hello there"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = runBlocking { response.bodyAsText() }
        assertTrue(body.contains(seeded.id), "Response should contain the storyId")
        assertTrue(body.contains("\"body\":\"Hello there\""))
    }

    @Test
    fun `GET v1-stories-id-comments returns empty page`() = testApplication {
        val seeded = Story(title = "Hello", url = "https://example.com", createdBy = "seed")
        application {
            install(ContentNegotiation) { json() }
            val storyRepo = object : StoryRepository() {
                private val items = mutableListOf<Story>(seeded)
                override suspend fun ensureIndexes() {}
                override suspend fun create(story: Story): Story { items.add(story); return story }
                override suspend fun findById(id: String): Story? = items.find { it.id == id }
                override suspend fun recent(limit: Int): List<Story> = items.take(limit)
                override suspend fun recentPage(pageSize: Int, afterCursor: String?) =
                    (if (items.size <= pageSize) items else items.take(pageSize)) to null
            }
            val commentRepo = object : CommentRepository() {
                private val items = mutableListOf<Comment>()
                override suspend fun ensureIndexes() {}
                override suspend fun create(comment: Comment): Comment { items.add(comment); return comment }
                override suspend fun listByStoryPage(storyId: String, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.storyId == storyId }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val storyService = StoryService(storyRepo, AllowAllLimiter)
            val commentService = CommentService(commentRepo, storyRepo, AllowAllLimiter)
            routing { this.storiesRoutes(storyService, commentService) }
        }

        val res = client.get("/v1/stories/${'$'}{seeded.id}/comments")
        assertEquals(HttpStatusCode.OK, res.status)
        val text = runBlocking { res.bodyAsText() }
        assertTrue(text.contains("\"items\":[]"))
    }
}
