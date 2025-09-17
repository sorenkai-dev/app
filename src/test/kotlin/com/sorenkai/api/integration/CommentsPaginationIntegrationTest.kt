package com.sorenkai.api.integration

import com.sorenkai.api.routes.storiesRoutes
import com.sorenkai.api.services.CommentService
import com.sorenkai.api.services.StoryService
import com.sorenkai.api.repo.CommentRepository
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
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.*

class CommentsPaginationIntegrationTest {

    private lateinit var mongo: MongoDBContainer

    @BeforeTest
    fun setup() {
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_UID", "test-user")
        System.setProperty("TEST_AUTH_ROLES", "user")
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
    fun `comments GET supports cursor pagination end-to-end`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            val storyRepo = StoryRepository()
            val commentRepo = CommentRepository()
            val storySvc = StoryService(storyRepo, AllowAllLimiter)
            val commentSvc = CommentService(commentRepo, storyRepo, AllowAllLimiter)
            routing { storiesRoutes(service = storySvc, commentService = commentSvc) }
            runBlocking {
                storyRepo.ensureIndexes()
                commentRepo.ensureIndexes()
            }
        }

        // Create a story
        val createdStoryRes = client.post("/v1/stories") {
            header(HttpHeaders.Authorization, "Bearer test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Story","url":"https://example.com/s"}""")
        }
        assertEquals(HttpStatusCode.Created, createdStoryRes.status)
        val storyJson = runBlocking { createdStoryRes.bodyAsText() }
        val storyId = Regex("\"id\":\"([^\"]+)\"").find(storyJson)?.groupValues?.get(1) ?: error("no id")

        // Seed 3 comments via API
        repeat(3) { i ->
            val res = client.post("/v1/stories/$storyId/comments") {
                header(HttpHeaders.Authorization, "Bearer test")
                contentType(ContentType.Application.Json)
                setBody("""{"body":"c$i"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }

        // Page 1
        val page1 = client.get("/v1/stories/$storyId/comments?pageSize=2")
        assertEquals(HttpStatusCode.OK, page1.status)
        val body1 = page1.bodyAsText()
        val cursor = Regex("\"nextCursor\":\"([^\"]+)\"").find(body1)?.groupValues?.get(1)
        assertNotNull(cursor)

        // Page 2
        val page2 = client.get("/v1/stories/$storyId/comments?pageSize=2&cursor=$cursor")
        assertEquals(HttpStatusCode.OK, page2.status)
        val body2 = page2.bodyAsText()
        assertTrue(body2.contains("\"items\":["))
        assertTrue(body2.contains("\"nextCursor\":null") || !body2.contains("\"nextCursor\":"))
    }
}
