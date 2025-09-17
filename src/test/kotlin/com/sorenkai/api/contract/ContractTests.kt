package com.sorenkai.api.contract

import com.sorenkai.api.integration.AllowAllLimiter
import com.sorenkai.api.model.Comment
import com.sorenkai.api.model.Report
import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.model.Story
import com.sorenkai.api.repo.CommentRepository
import com.sorenkai.api.repo.ReportRepository
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.routes.reportsRoutes
import com.sorenkai.api.routes.storiesRoutes
import com.sorenkai.api.services.CommentService
import com.sorenkai.api.services.ReportService
import com.sorenkai.api.services.StoryService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.respond
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.*

/**
 * Snapshot-style contract tests to ensure responses follow the documented shapes.
 * We normalize dynamic fields (ids, timestamps, uids) before comparing to snapshots.
 */
class ContractTests {

    private fun Application.installErrorMapping() {
        install(StatusPages) {
            // Comment validation -> 400
            exception(com.sorenkai.api.services.CommentService.ValidationException::class) { call, e ->
                val code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED
                val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = code.name,
                        message = e.message ?: "Bad request",
                        details = null
                    )
                )
                call.respond(code.http, body)
            }
            // Fallback -> 500 (keeps structure consistent if something slips through)
            exception(Throwable::class) { call, cause ->
                val code = com.sorenkai.api.errors.ErrorCode.INTERNAL
                val body = com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = code.name,
                        message = cause.message ?: "Internal server error",
                        details = null
                    )
                )
                call.respond(code.http, body)
            }
        }
    }

    @BeforeTest
    fun setupAuthBypass() {
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "contract-user")
        System.setProperty("TEST_AUTH_ROLES", "mod") // mod so that mod endpoints are accessible in tests that need them
    }

    @AfterTest
    fun clearAuthBypass() {
        System.clearProperty("ENV")
        System.clearProperty("TEST_AUTH_UID")
        System.clearProperty("TEST_AUTH_ROLES")
    }

    private fun normalizeJson(json: String): String {
        // Replace common dynamic fields with placeholders
        var s = json
        val patterns = listOf(
            "\"id\":\"(.*?)\"" to "\"id\":\"<id>\"",
            "\"storyId\":\"(.*?)\"" to "\"storyId\":\"<storyId>\"",
            "\"targetId\":\"(.*?)\"" to "\"targetId\":\"<targetId>\"",
            "\"createdBy\":\"(.*?)\"" to "\"createdBy\":\"<uid>\"",
            "\"createdAt\":\"(.*?)\"" to "\"createdAt\":\"<timestamp>\"",
            // Cursor is opaque base64
            "\"nextCursor\":\"(.*?)\"" to "\"nextCursor\":\"<cursor>\""
        )
        for ((pattern, repl) in patterns) {
            s = Regex(pattern).replace(s, repl)
        }
        // Sort keys within small objects is non-trivial without a JSON parser; we rely on stable Ktor/Kotlinx ordering
        return s
    }

    private fun readSnapshot(name: String): String {
        val path = Path.of("src", "test", "resources", "snapshots", name)
        assertTrue(path.toFile().exists(), "Snapshot missing: $name")
        return Files.readString(path).trim()
    }

    private fun writeSnapshotIfMissing(name: String, content: String) {
        val dir = Path.of("src", "test", "resources", "snapshots")
        if (!dir.exists()) dir.createDirectories()
        val path = dir.resolve(name)
        if (!path.toFile().exists()) {
            Files.writeString(path, content)
        }
    }

    @Test
    fun story_contracts() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installErrorMapping()
            val repo = object : StoryRepository() {
                private val items = mutableListOf<Story>()
                override suspend fun ensureIndexes() {}
                override suspend fun create(story: Story): Story { items.add(story); return story }
                override suspend fun recent(limit: Int): List<Story> = items.take(limit)
                override suspend fun recentPage(pageSize: Int, afterCursor: String?) =
                    (if (items.size <= pageSize) items else items.take(pageSize)) to null
            }
            val svc = StoryService(repo, AllowAllLimiter)
            routing { storiesRoutes(service = svc) }
        }

        // 401 when missing auth
        val unauth = client.post("/v1/stories") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hello","url":"https://example.com"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)
        val unauthBody = normalizeJson(runBlocking { unauth.bodyAsText() })
        writeSnapshotIfMissing("stories_post_401.json", unauthBody)
        assertEquals(readSnapshot("stories_post_401.json"), unauthBody)

        // 201 with auth
        val created = client.post("/v1/stories") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hello","url":"https://example.com"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdBody = normalizeJson(runBlocking { created.bodyAsText() })
        writeSnapshotIfMissing("stories_post_201.json", createdBody)
        assertEquals(readSnapshot("stories_post_201.json"), createdBody)

        // GET page (empty or one item depending on ordering) — our in-memory returns exactly what we inserted
        val page = client.get("/v1/stories")
        assertEquals(HttpStatusCode.OK, page.status)
        val pageBody = normalizeJson(runBlocking { page.bodyAsText() })
        writeSnapshotIfMissing("stories_get_200.json", pageBody)
        assertEquals(readSnapshot("stories_get_200.json"), pageBody)
    }

    @Test
    fun comment_contracts() = testApplication {
        val seeded = Story(title = "T", url = "https://e.com", createdBy = "seed")
        application {
            install(ContentNegotiation) { json() }
            installErrorMapping()
            val storyRepo = object : StoryRepository() {
                private val items = mutableListOf(seeded)
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
            routing { storiesRoutes(service = storyService, commentService = commentService) }
        }

        // 401 when missing auth
        val unauthComment = client.post("/v1/stories/${seeded.id}/comments") {
            contentType(ContentType.Application.Json)
            setBody("""{"body":"hello"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthComment.status)
        val unauthCommentBody = normalizeJson(runBlocking { unauthComment.bodyAsText() })
        writeSnapshotIfMissing("comments_post_401.json", unauthCommentBody)
        assertEquals(readSnapshot("comments_post_401.json"), unauthCommentBody)

        // 400 validation when empty body
        val bad = client.post("/v1/stories/${seeded.id}/comments") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        val badBody = normalizeJson(runBlocking { bad.bodyAsText() })
        writeSnapshotIfMissing("comments_post_400.json", badBody)
        assertEquals(readSnapshot("comments_post_400.json"), badBody)

        // 201 ok
        val ok = client.post("/v1/stories/${seeded.id}/comments") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"body":"hello"}""")
        }
        assertEquals(HttpStatusCode.Created, ok.status)
        val okBody = normalizeJson(runBlocking { ok.bodyAsText() })
        writeSnapshotIfMissing("comments_post_201.json", okBody)
        assertEquals(readSnapshot("comments_post_201.json"), okBody)

        // GET page
        val page = client.get("/v1/stories/${seeded.id}/comments")
        assertEquals(HttpStatusCode.OK, page.status)
        val pageBody = normalizeJson(runBlocking { page.bodyAsText() })
        writeSnapshotIfMissing("comments_get_200.json", pageBody)
        assertEquals(readSnapshot("comments_get_200.json"), pageBody)
    }

    @Test
    fun report_contracts() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installErrorMapping()
            val repo = object : ReportRepository() {
                private val items = mutableListOf<Report>()
                override suspend fun ensureIndexes() {}
                override suspend fun create(report: Report): Report { items.add(report); return report }
                override suspend fun pageByStatus(status: ReportStatus, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.status == status }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val svc = ReportService(repo, AllowAllLimiter)
            routing { reportsRoutes(service = svc) }
        }

        // 401 when missing auth on create report
        val unauthReport = client.post("/v1/reports") {
            contentType(ContentType.Application.Json)
            setBody("""{"targetType":"STORY","targetId":"x","reason":"r"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthReport.status)
        val unauthReportBody = normalizeJson(runBlocking { unauthReport.bodyAsText() })
        writeSnapshotIfMissing("reports_post_401.json", unauthReportBody)
        assertEquals(readSnapshot("reports_post_401.json"), unauthReportBody)

        // 403 when user role on mod endpoint
        System.setProperty("TEST_AUTH_ROLES", "user")
        val forbidden = client.get("/v1/mod/reports") { header(HttpHeaders.Authorization, "Bearer test") }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        val forbBody = normalizeJson(runBlocking { forbidden.bodyAsText() })
        writeSnapshotIfMissing("reports_mod_get_403.json", forbBody)
        assertEquals(readSnapshot("reports_mod_get_403.json"), forbBody)

        // Switch back to mod and create report
        System.setProperty("TEST_AUTH_ROLES", "mod")
        val created = client.post("/v1/reports") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"targetType":"STORY","targetId":"x","reason":"r"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdBody = normalizeJson(runBlocking { created.bodyAsText() })
        writeSnapshotIfMissing("reports_post_201.json", createdBody)
        assertEquals(readSnapshot("reports_post_201.json"), createdBody)
    }
}
