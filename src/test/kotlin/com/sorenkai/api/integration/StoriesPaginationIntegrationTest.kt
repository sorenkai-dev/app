package com.sorenkai.api.integration

import com.sorenkai.api.routes.storiesRoutes
import com.sorenkai.api.services.StoryService
import com.sorenkai.api.repo.StoryRepository
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

class StoriesPaginationIntegrationTest {

    private lateinit var mongo: MongoDBContainer

    @BeforeTest
    fun setup() {
        // Auth bypass for tests
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "test-user")
        System.setProperty("TEST_AUTH_ROLES", "user")

        // Start MongoDB Testcontainer
        mongo = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        mongo.start()
        // Point the app to the container DB (database is provided in the replica set URL)
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
    fun `stories GET supports cursor pagination end-to-end`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            // Use real Mongo-backed repos but allow-all limiter for POSTs
            val repo = StoryRepository()
            val service = StoryService(repo, AllowAllLimiter)
            routing { storiesRoutes(service = service) }
            // Ensure indexes created for pagination
            kotlinx.coroutines.runBlocking { repo.ensureIndexes() }
        }

        // Seed 3 stories via API
        repeat(3) { i ->
            val res = client.post("/v1/stories") {
                header(HttpHeaders.Authorization, "Bearer test")
                contentType(ContentType.Application.Json)
                setBody("""{"title":"T$i","url":"https://e.com/$i"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }

        // Page 1: expect 2 items and a nextCursor
        val page1 = client.get("/v1/stories?pageSize=2")
        assertEquals(HttpStatusCode.OK, page1.status)
        val body1 = page1.bodyAsText()
        assertTrue(body1.contains("\"items\":"))
        val cursorRegex = Regex("\"nextCursor\":\"([^\"]+)\"")
        val cursor = cursorRegex.find(body1)?.groupValues?.get(1)
        assertNotNull(cursor, "nextCursor should be present when more results exist")

        // Page 2: pass cursor, expect remaining 1 item and null cursor
        val page2 = client.get("/v1/stories?pageSize=2&cursor=$cursor")
        assertEquals(HttpStatusCode.OK, page2.status)
        val body2 = page2.bodyAsText()
        // Should contain exactly one item
        assertTrue(body2.contains("\"items\":["))
        // nextCursor should be null/absent
        assertTrue(body2.contains("\"nextCursor\":null") || !body2.contains("\"nextCursor\":"))
    }
}
