package com.sorenkai.api.integration

import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.storiesRoutes
import com.sorenkai.api.services.StoryService
import io.ktor.client.request.post
import io.ktor.client.request.header
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
import kotlin.test.*

private class InMemoryStoryRepoAudit : StoryRepository() {
    val items = mutableListOf<Story>()
    override suspend fun ensureIndexes() {}
    override suspend fun create(story: Story): Story { items.add(story); return story }
    override suspend fun recent(limit: Int): List<Story> = items.take(limit)
}

private class RecordingAuditRepo2 : AuditLogRepository() {
    val created = mutableListOf<com.sorenkai.api.model.AuditLog>()
    override suspend fun create(entry: com.sorenkai.api.model.AuditLog): com.sorenkai.api.model.AuditLog {
        created.add(entry)
        return entry
    }
}

class StoriesAuditIntegrationTest {

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
    fun `POST story writes audit log`() = testApplication {
        lateinit var audit: RecordingAuditRepo2
        application {
            install(ContentNegotiation) { json() }
            val repo = InMemoryStoryRepoAudit()
            audit = RecordingAuditRepo2()
            val svc = StoryService(repo, AllowAllLimiter, audit)
            routing { storiesRoutes(svc) }
        }
        val res = client.post("/v1/stories") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"T","url":"https://e.com"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
        // verify audit recorded
        assertEquals(1, audit.created.size, "Expected one audit entry for story create")
        assertEquals(com.sorenkai.api.model.AuditLog.EntityType.STORY, audit.created[0].entityType)
    }
}
