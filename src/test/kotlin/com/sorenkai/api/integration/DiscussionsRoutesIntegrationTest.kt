package com.sorenkai.api.integration

import com.sorenkai.api.model.Discussion
import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.DiscussionRepository
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.discussionsRoutes
import com.sorenkai.api.services.DiscussionService
import io.ktor.client.request.delete
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
import io.ktor.server.response.respondText
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*

private class InMemoryDiscussionRepo : DiscussionRepository() {
    val items = mutableListOf<Discussion>()

    override suspend fun ensureIndexes() { /* no-op */ }

    override suspend fun create(item: Discussion): Discussion {
        items.add(item)
        return item
    }

    override suspend fun findById(id: String): Discussion? = items.find { it.id == id }

    override suspend fun deleteOwn(id: String, uid: String): Boolean {
        val idx = items.indexOfFirst { it.id == id && it.userId == uid }
        if (idx >= 0) {
            items.removeAt(idx)
            return true
        }
        return false
    }

    override suspend fun listRoot(pageSize: Int, cursor: String?) =
        (items.filter { it.kind == Discussion.Kind.POST }.take(pageSize)) to null

    override suspend fun listByStory(storyId: String, pageSize: Int, cursor: String?) =
        (items.filter { it.storyId == storyId }.take(pageSize)) to null

    override suspend fun listByParent(parentId: String, pageSize: Int, cursor: String?) =
        (items.filter { it.parentId == parentId }.take(pageSize)) to null
}

private class InMemoryStoryRepo3 : StoryRepository() {
    val items = mutableListOf<Story>()
    override suspend fun ensureIndexes() {}
    override suspend fun create(story: Story): Story { items.add(story); return story }
    override suspend fun findById(id: String): Story? = items.find { it.id == id }
}

private class RecordingAuditRepo : AuditLogRepository() {
    val created = mutableListOf<com.sorenkai.api.model.AuditLog>()
    override suspend fun create(entry: com.sorenkai.api.model.AuditLog): com.sorenkai.api.model.AuditLog {
        created.add(entry)
        return entry
    }
}

private class FailingAuditRepo : AuditLogRepository() {
    override suspend fun create(entry: com.sorenkai.api.model.AuditLog): com.sorenkai.api.model.AuditLog {
        throw RuntimeException("audit down")
    }
}

class DiscussionsRoutesIntegrationTest {

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
    fun `POST discussions creates post and writes audit`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            install(io.ktor.server.plugins.statuspages.StatusPages) {
                exception<com.sorenkai.api.services.DiscussionService.ValidationException> { call, _ ->
                    val status = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.http
                    call.respondText("{\"ok\":false}", io.ktor.http.ContentType.Application.Json, status)
                }
                exception<com.sorenkai.api.services.DiscussionService.NotFoundException> { call, _ ->
                    val status = com.sorenkai.api.errors.ErrorCode.NOT_FOUND.http
                    call.respondText("{\"ok\":false}", io.ktor.http.ContentType.Application.Json, status)
                }
            }
            val dRepo = InMemoryDiscussionRepo()
            val sRepo = InMemoryStoryRepo3()
            val aRepo = RecordingAuditRepo()
            val svc = DiscussionService(dRepo, sRepo, aRepo)
            routing { discussionsRoutes(svc) }
        }
        val res = client.post("/v1/discussions") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"POST","body":"Hello world"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
        val body = runBlocking { res.bodyAsText() }
        assertTrue(body.contains("Hello world"))
    }

    @Test
    fun `POST discussions with empty body returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(InMemoryDiscussionRepo(), InMemoryStoryRepo3(), RecordingAuditRepo())
            routing { discussionsRoutes(svc) }
        }
        val res = client.post("/v1/discussions") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"POST","body":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("VALIDATION_FAILED"))
    }

    @Test
    fun `POST comment without parentId returns 400`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(InMemoryDiscussionRepo(), InMemoryStoryRepo3(), RecordingAuditRepo())
            routing { discussionsRoutes(svc) }
        }
        val res = client.post("/v1/discussions") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"COMMENT","body":"reply"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `POST post with non-existent storyId returns 404`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(InMemoryDiscussionRepo(), InMemoryStoryRepo3(), RecordingAuditRepo())
            routing { discussionsRoutes(svc) }
        }
        val res = client.post("/v1/discussions") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"POST","body":"x","storyId":"nope"}""")
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `GET discussions list root and by parent`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val dRepo = InMemoryDiscussionRepo()
            val svc = DiscussionService(dRepo, InMemoryStoryRepo3(), RecordingAuditRepo())
            routing { discussionsRoutes(svc) }
            // seed two posts and one comment
            runBlocking {
                dRepo.create(Discussion(kind = Discussion.Kind.POST, userId = "test-user", body = "p1"))
                dRepo.create(Discussion(kind = Discussion.Kind.POST, userId = "test-user", body = "p2"))
                val parent = dRepo.items.first()
                dRepo.create(Discussion(kind = Discussion.Kind.COMMENT, parentId = parent.id, userId = "test-user", body = "c1"))
            }
        }
        val root = client.get("/v1/discussions")
        assertEquals(HttpStatusCode.OK, root.status)
        val text = root.bodyAsText()
        assertTrue(text.contains("\"items\":"))
        val parentId = Regex("\"id\":\"([^\"]+)\"").find(text)!!.groupValues[1]
        val replies = client.get("/v1/discussions?parentId=$parentId")
        assertEquals(HttpStatusCode.OK, replies.status)
        assertTrue(replies.bodyAsText().contains("c1"))
    }

    @Test
    fun `DELETE discussions own item logs audit and returns ok`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val dRepo = InMemoryDiscussionRepo()
            val audit = RecordingAuditRepo()
            val svc = DiscussionService(dRepo, InMemoryStoryRepo3(), audit)
            routing { discussionsRoutes(svc) }
            // seed owned discussion
            runBlocking { dRepo.create(Discussion(kind = Discussion.Kind.POST, userId = "test-user", body = "p1")) }
        }
        val list = client.get("/v1/discussions")
        val id = Regex("\"id\":\"([^\"]+)\"").find(list.bodyAsText())!!.groupValues[1]
        val res = client.delete("/v1/discussions/$id") { header(HttpHeaders.Authorization, "Bearer test") }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `DELETE not owned returns 404`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val dRepo = InMemoryDiscussionRepo()
            val svc = DiscussionService(dRepo, InMemoryStoryRepo3(), RecordingAuditRepo())
            routing { discussionsRoutes(svc) }
            runBlocking { dRepo.create(Discussion(kind = Discussion.Kind.POST, userId = "other", body = "p1")) }
        }
        val list = client.get("/v1/discussions")
        val id = Regex("\"id\":\"([^\"]+)\"").find(list.bodyAsText())!!.groupValues[1]
        val res = client.delete("/v1/discussions/$id") { header(HttpHeaders.Authorization, "Bearer test") }
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `audit failure does not crash request`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val svc = DiscussionService(InMemoryDiscussionRepo(), InMemoryStoryRepo3(), FailingAuditRepo())
            routing { discussionsRoutes(svc) }
        }
        val res = client.post("/v1/discussions") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"POST","body":"ok"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }
}
