package com.sorenkai.api.contract

//import kotlin.test.AfterTest
//import kotlin.test.BeforeTest
//import kotlin.test.Test
import com.sorenkai.api.integration.AllowAllLimiter
import com.sorenkai.api.model.Report
import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.repo.ReportRepository
import com.sorenkai.api.routes.discussionsRoutes
import com.sorenkai.api.routes.reportsRoutes
import com.sorenkai.api.routes.writingsRoutes
import com.sorenkai.api.services.ReportService
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
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Snapshot-style contract tests to ensure responses follow the documented shapes.
 * We normalize dynamic fields (ids, timestamps, uids) before comparing to snapshots.
 */
class ContractTests {

    private fun Application.installErrorMapping() {
        install(StatusPages) {
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

    @BeforeEach
    fun setupAuthBypass() {
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "contract-user")
        System.setProperty("TEST_AUTH_ROLES", "mod") // mod so that mod endpoints are accessible in tests that need them
    }

    @AfterEach
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
    fun writing_contracts() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installErrorMapping()
            // Fake repo to avoid Mongo
            val repo = object : com.sorenkai.api.repo.WritingRepository() {
                override suspend fun ensureIndexes() {}
                override suspend fun listPublished(limit: Int, offset: Int) = emptyList<com.sorenkai.api.model.Writing>()
                override suspend fun getPublishedBySlug(slug: String) = null
            }
            val svc = com.sorenkai.api.services.WritingService(repo)
            routing { writingsRoutes(service = svc, enforce = true, allowedOrigins = setOf("https://allowed.example")) }
        }
        // 401 when missing token
        val unauth = client.get("/v1/writings")
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)
        val unauthBody = normalizeJson(runBlocking { unauth.bodyAsText() })
        writeSnapshotIfMissing("writings_get_401.json", unauthBody)
        assertEquals(readSnapshot("writings_get_401.json"), unauthBody)

        // 403 when origin not allowed
        val forbidden = client.get("/v1/writings") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        // 200 with allowed origin and token
        val ok = client.get("/v1/writings") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://allowed.example")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        val okBody = normalizeJson(runBlocking { ok.bodyAsText() })
        writeSnapshotIfMissing("writings_get_200.json", okBody)
        assertEquals(readSnapshot("writings_get_200.json"), okBody)

        // 404 for missing slug
        val notFound = client.get("/v1/writings/nope") {
            header("X-Firebase-AppCheck", "fake-token")
            header(HttpHeaders.Origin, "https://allowed.example")
        }
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }

    @Test
    fun discussion_contracts() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            installErrorMapping()
            // Fake repos to avoid Mongo
            val repo = object : com.sorenkai.api.repo.DiscussionRepository() {
                override suspend fun ensureIndexes() {}
                override suspend fun listRoot(pageSize: Int, cursor: String?) =
                    emptyList<com.sorenkai.api.model.Discussion>() to null
                override suspend fun listByWriting(writingSlug: String, pageSize: Int, cursor: String?) =
                    emptyList<com.sorenkai.api.model.Discussion>() to null
                override suspend fun listByParent(parentId: String, pageSize: Int, cursor: String?) =
                    emptyList<com.sorenkai.api.model.Discussion>() to null
            }

            val writings = object : com.sorenkai.api.repo.WritingRepository() {
                override suspend fun ensureIndexes() {}
                override suspend fun getPublishedBySlug(slug: String) = null
            }

            val audit = object : com.sorenkai.api.repo.AuditLogRepository() {}

            // Note: DiscussionService constructor now takes WritingRepository instead of StoryRepository
            val svc = com.sorenkai.api.services.DiscussionService(repo, writings, audit)

            routing { discussionsRoutes(service = svc) }
        }

        // GET empty page
        val page = client.get("/v1/discussions")
        assertEquals(HttpStatusCode.OK, page.status)
        val pageBody = normalizeJson(runBlocking { page.bodyAsText() })
        writeSnapshotIfMissing("discussions_get_200.json", pageBody)
        assertEquals(readSnapshot("discussions_get_200.json"), pageBody)

        // POST unauthenticated -> 401 (App Check default off, but auth still required)
        val unauthPost = client.post("/v1/discussions") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"POST","body":"Hello world"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthPost.status)
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
