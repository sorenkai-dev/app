package com.sorenkai.api.integration

import com.sorenkai.api.model.Report
import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.model.TargetType
import com.sorenkai.api.repo.ReportRepository
import com.sorenkai.api.routes.reportsRoutes
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
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ReportsRoutesIntegrationTest {

    @BeforeTest
    fun setupAuthBypassUser() {
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
    fun `POST v1-reports creates report and returns 201`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = object : ReportRepository() {
                private val items = mutableListOf<Report>()
                override suspend fun ensureIndexes() {}
                override suspend fun create(report: Report): Report { items.add(report); return report }
                override suspend fun pageByStatus(status: ReportStatus, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.status == status }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val svc = ReportService(repo, AllowAllLimiter)
            routing { this.reportsRoutes(svc) }
        }

        val response = client.post("/v1/reports") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"targetType":"STORY","targetId":"abc123","reason":"spam"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = runBlocking { response.bodyAsText() }
        assertTrue(body.contains("\"targetType\":\"STORY\""))
        assertTrue(body.contains("\"targetId\":\"abc123\""))
        assertTrue(body.contains("\"status\":\"OPEN\""))
    }

    @Test
    fun `GET v1-mod-reports forbidden for user`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = object : ReportRepository() {
                private val items = mutableListOf<Report>()
                override suspend fun ensureIndexes() {}
                override suspend fun pageByStatus(status: ReportStatus, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.status == status }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val svc = ReportService(repo, AllowAllLimiter)
            routing { this.reportsRoutes(svc) }
        }

        val res = client.get("/v1/mod/reports") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `GET v1-mod-reports ok for mod`() = testApplication {
        // Switch roles to mod for this test only
        System.setProperty("TEST_AUTH_ROLES", "mod")
        application {
            install(ContentNegotiation) { json() }
            val repo = object : ReportRepository() {
                private val items = mutableListOf<Report>()
                override suspend fun ensureIndexes() {}
                override suspend fun pageByStatus(status: ReportStatus, pageSize: Int, afterCursor: String?) =
                    (items.filter { it.status == status }.let { if (it.size <= pageSize) it else it.take(pageSize) }) to null
            }
            val svc = ReportService(repo, AllowAllLimiter)
            routing { this.reportsRoutes(svc) }
        }

        val res = client.get("/v1/mod/reports") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val text = runBlocking { res.bodyAsText() }
        assertTrue(text.contains("\"items\":[]"))
    }
}
