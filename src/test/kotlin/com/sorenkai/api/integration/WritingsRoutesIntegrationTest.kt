package com.sorenkai.api.integration

import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.writingsRoutes
import com.sorenkai.api.services.StoryService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryStoryRepo2 : StoryRepository() {
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

class WritingsRoutesIntegrationTest {

    @Test
    fun `GET writings with enforcement OFF succeeds without headers`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo2()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { writingsRoutes(service = svc, enforce = false, allowedOrigins = emptySet()) }
        }

        val res = client.get("/v1/writings/stories")
        assertEquals(HttpStatusCode.OK, res.status)
        val text = runBlocking { res.bodyAsText() }
        assertTrue(text.contains("\"items\":[]"))
    }

    @Test
    fun `GET writings with enforcement ON and missing token returns 401`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo2()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { writingsRoutes(service = svc, enforce = true, allowedOrigins = emptySet()) }
        }

        val res = client.get("/v1/writings/stories")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `GET writings with token but disallowed origin returns 403`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo2()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { writingsRoutes(service = svc, enforce = true, allowedOrigins = setOf("https://allowed.example")) }
        }

        val res = client.get("/v1/writings/stories") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `GET writings with token and allowed origin returns 200`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepo2()
            val svc = StoryService(repo, AllowAllLimiter)
            routing { writingsRoutes(service = svc, enforce = true, allowedOrigins = setOf("https://allowed.example")) }
        }

        val res = client.get("/v1/writings/stories") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://allowed.example")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val text = runBlocking { res.bodyAsText() }
        assertTrue(text.contains("\"items\":[]"))
    }
}
