package com.sorenkai.api.integration

import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.storiesRoutes
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryStoryRepo : StoryRepository() {
    private val items = mutableListOf<Story>()

    override suspend fun ensureIndexes() { /* no-op */ }

    override suspend fun create(story: Story): Story {
        items.add(story)
        return story
    }

    override suspend fun findById(id: String): Story? = items.find { it.id == id }

    override suspend fun recent(limit: Int): List<Story> = items.take(limit)

    override suspend fun recentPage(pageSize: Int, afterCursor: String?) =
        (if (items.size <= pageSize) items else items.take(pageSize)) to null
}

class StoriesRoutesIntegrationTest {

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
    fun `POST v1-stories creates story and returns 201`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { this.storiesRoutes(svc) }
        }

        val response = client.post("/v1/stories") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hello","url":"https://example.com"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = runBlocking { response.bodyAsText() }
        assertTrue(body.contains("\"title\":\"Hello\""))
        assertTrue(body.contains("\"url\":\"https://example.com\""))
    }

    @Test
    fun `GET v1-stories returns empty page`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { this.storiesRoutes(svc) }
        }

        val res = client.get("/v1/stories")
        assertEquals(HttpStatusCode.OK, res.status)
        val text = runBlocking { res.bodyAsText() }
        assertTrue(text.contains("\"items\":[]"))
    }
}
