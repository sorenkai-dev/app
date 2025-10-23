package com.sorenkai.api.integration

import com.sorenkai.api.model.Writing
import com.sorenkai.api.repo.WritingRepository
import com.sorenkai.api.routes.writingsRoutes
import com.sorenkai.api.services.WritingService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class InMemoryWritingRepoInt : WritingRepository() {
    val items = mutableListOf<Writing>()
    override suspend fun ensureIndexes() {}
    override suspend fun listPublished(limit: Int, offset: Int): List<Writing> {
        val start = offset.coerceAtLeast(0)
        val end = (start + limit.coerceAtLeast(1)).coerceAtMost(items.size)
        // Already sorted order expected by tests
        return if (start >= end) emptyList() else items.subList(start, end)
    }
    override suspend fun getPublishedBySlug(slug: String): Writing? = items.find { it.slug == slug && it.status == Writing.Status.PUBLISHED }
}

class WritingsRoutesIntegrationTest {

    @Test
    fun `GET writings list supports limit offset and sorting`() = testApplication {
        val repo = InMemoryWritingRepoInt()
        application {
            install(ContentNegotiation) { json() }
            val svc = WritingService(repo)
            routing { writingsRoutes(service = svc, enforce = false) }
        }
        // seed 3 published writings with descending publishedAt order expected by API
        val t3 = kotlinx.datetime.Instant.fromEpochMilliseconds(3)
        val t2 = kotlinx.datetime.Instant.fromEpochMilliseconds(2)
        val t1 = kotlinx.datetime.Instant.fromEpochMilliseconds(1)
        runBlocking {
            repo.items.addAll(listOf(
                Writing(title = "C", slug = "c", synopsis = "c syn", content = "c body", status = Writing.Status.PUBLISHED, authorId = "a", publishedAt = t3, language="en", updatedAt= t3, createdAt = t3),
                Writing(title = "B", slug = "b", synopsis = "b syn", content = "b body", status = Writing.Status.PUBLISHED, authorId = "a", publishedAt = t2, language="en", updatedAt = t2, createdAt = t2),
                Writing(title = "A", slug = "a", synopsis = "a syn", content = "a body", status = Writing.Status.PUBLISHED, authorId = "a", publishedAt = t1, language="en", updatedAt = t1, createdAt = t1)
            ))
        }
        // full list (default limit=12)
        val resAll = client.get("/v1/writings")
        assertEquals(HttpStatusCode.OK, resAll.status)
        val bodyAll = runBlocking { resAll.bodyAsText() }
        // Should return metadata only (no content field) and in order C, B, A
        assertTrue(bodyAll.indexOf("\"slug\":\"c\"") < bodyAll.indexOf("\"slug\":\"b\"") && bodyAll.indexOf("\"slug\":\"b\"") < bodyAll.indexOf("\"slug\":\"a\""))
        assertTrue(!bodyAll.contains("\"content\":"))

        // limit=1&offset=1 should return only the second item (B)
        val resPage = client.get("/v1/writings?limit=1&offset=1")
        assertEquals(HttpStatusCode.OK, resPage.status)
        val bodyPage = runBlocking { resPage.bodyAsText() }
        assertTrue(bodyPage.contains("\"slug\":\"b\""))
        assertTrue(!bodyPage.contains("\"slug\":\"c\""))
        assertTrue(!bodyPage.contains("\"slug\":\"a\""))
    }

    @Test
    fun `GET writings detail returns full article and 404 when missing`() = testApplication {
        val repo = InMemoryWritingRepoInt()
        application {
            install(ContentNegotiation) { json() }
            val svc = WritingService(repo)
            routing { writingsRoutes(service = svc, enforce = false) }
        }
        val publishedAt = Instant.fromEpochMilliseconds(10)
        runBlocking {
            repo.items.add(Writing(title = "Hello", slug = "hello-world", synopsis = "s", content = "# Markdown", status = Writing.Status.PUBLISHED, authorId = "u", language = "en", createdAt=publishedAt, publishedAt = publishedAt, updatedAt = publishedAt))
        }
        val ok = client.get("/v1/writings/hello-world")
        assertEquals(HttpStatusCode.OK, ok.status)
        val okBody = runBlocking { ok.bodyAsText() }
        assertTrue(okBody.contains("\"content\":\"# Markdown\""))
        assertTrue(okBody.contains("\"status\":\"PUBLISHED\""))

        val notFound = client.get("/v1/writings/nope")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }
}
