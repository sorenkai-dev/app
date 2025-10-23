package com.sorenkai.api.integration

import com.sorenkai.api.model.Discussion
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.DiscussionRepository
import com.sorenkai.api.repo.WritingRepository
import com.sorenkai.api.routes.discussionsRoutes
import com.sorenkai.api.services.DiscussionService
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
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
import kotlinx.datetime.Instant
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

private class InMemoryDiscussionRepoEdit : DiscussionRepository() {
    val items = mutableListOf<Discussion>()
    override suspend fun create(item: Discussion): Discussion { items.add(item); return item }
    override suspend fun updateOwn(id: String, uid: String, body: String, hashtags: List<String>, updatedAt: Instant): Boolean {
        val idx = items.indexOfFirst { it.id == id && it.userId == uid }
        if (idx < 0) return false
        items[idx] = items[idx].copy(body = body, hashtags = hashtags, updatedAt = updatedAt)
        return true
    }
}

private class RecordingAuditRepoEdit : AuditLogRepository() {
    val created = mutableListOf<com.sorenkai.api.model.AuditLog>()
    override suspend fun create(entry: com.sorenkai.api.model.AuditLog): com.sorenkai.api.model.AuditLog {
        created.add(entry); return entry
    }
}

class DiscussionsEditIntegrationTest {

    @BeforeEach
    fun setupAuth() {
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_ENABLED", "true")
        System.setProperty("TEST_AUTH_UID", "u1")
        System.setProperty("TEST_AUTH_ROLES", "user")
    }

    @AfterEach
    fun clearAuth() {
        System.clearProperty("ENV")
        System.clearProperty("TEST_AUTH_ENABLED")
        System.clearProperty("TEST_AUTH_UID")
        System.clearProperty("TEST_AUTH_ROLES")
    }

    @Test
    fun `PATCH updates own post, extracts hashtags, writes audit`() = testApplication {
        val repo = InMemoryDiscussionRepoEdit()
        val audit = RecordingAuditRepoEdit()
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(repo, WritingRepository(), audit)
            routing { discussionsRoutes(svc) }
        }
        // seed item owned by u1
        runBlocking { repo.create(Discussion(kind = Discussion.Kind.POST, userId = "u1", body = "old")) }
        val id = repo.items.first().id
        val res = client.patch("/v1/discussions/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"Updated #Tag"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val updated = repo.items.first()
        assertTrue(updated.body.contains("Updated"))
        assertEquals(listOf("tag"), updated.hashtags)
        assertTrue(audit.created.any { it.action == com.sorenkai.api.model.AuditLog.Action.EDIT })
    }

    @Test
    fun `PATCH with empty body returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(InMemoryDiscussionRepoEdit(), WritingRepository(), RecordingAuditRepoEdit())
            routing { discussionsRoutes(svc) }
        }
        val res = client.patch("/v1/discussions/any") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `PATCH by non-owner returns 404`() = testApplication {
        val repo = InMemoryDiscussionRepoEdit()
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(repo, WritingRepository(), RecordingAuditRepoEdit())
            routing { discussionsRoutes(svc) }
        }
        // seed item owned by someone else
        runBlocking { repo.create(Discussion(kind = Discussion.Kind.POST, userId = "other", body = "x")) }
        val id = repo.items.first().id
        val res = client.patch("/v1/discussions/$id") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"new"}""")
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }
}
