package com.sorenkai.api.integration

import com.sorenkai.api.routes.reportsRoutes
import com.sorenkai.api.services.ReportService
import com.sorenkai.api.repo.ReportRepository
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
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.*

class ReportsPaginationIntegrationTest {

    private lateinit var mongo: MongoDBContainer

    @BeforeTest
    fun setup() {
        // Auth as moderator for listing endpoint
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "mod-user")
        System.setProperty("TEST_AUTH_ROLES", "mod")
        mongo = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        mongo.start()
        System.setProperty("MONGODB_URI", mongo.replicaSetUrl)
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("ENV")
        System.clearProperty("TEST_AUTH_UID")
        System.clearProperty("TEST_AUTH_ROLES")
        System.clearProperty("MONGODB_URI")
        runCatching { mongo.stop() }
    }

    @Test
    fun `reports GET supports cursor pagination end-to-end`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val repo = ReportRepository()
            val svc = ReportService(repo, AllowAllLimiter)
            routing { reportsRoutes(service = svc) }
            kotlinx.coroutines.runBlocking { repo.ensureIndexes() }
        }

        // Seed 3 reports as a normal user (switch role temporarily)
        System.setProperty("TEST_AUTH_UID", "test-user")
        System.setProperty("TEST_AUTH_ROLES", "user")
        repeat(3) { i ->
            val res = client.post("/v1/reports") {
                header(HttpHeaders.Authorization, "Bearer test")
                contentType(ContentType.Application.Json)
                setBody("""{"targetType":"STORY","targetId":"s$i","reason":"r$i"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
        // Switch back to moderator for listing
        System.setProperty("TEST_AUTH_UID", "mod-user")
        System.setProperty("TEST_AUTH_ROLES", "mod")

        // Page 1 (open reports by default)
        val page1 = client.get("/v1/mod/reports?pageSize=2") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.OK, page1.status)
        val body1 = page1.bodyAsText()
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body1)?.groupValues?.get(1)
        assertNotNull(cursor)

        // Page 2
        val page2 = client.get("/v1/mod/reports?pageSize=2&cursor=$cursor") {
            header(HttpHeaders.Authorization, "Bearer test")
        }
        assertEquals(HttpStatusCode.OK, page2.status)
        val body2 = page2.bodyAsText()
        assertTrue(body2.contains("\"items\":["))
        assertTrue(body2.contains("\"nextCursor\":null") || !body2.contains("\"nextCursor\":"))
    }
}
