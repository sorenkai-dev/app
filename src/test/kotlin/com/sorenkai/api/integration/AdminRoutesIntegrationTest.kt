package com.sorenkai.api.integration

import com.sorenkai.api.routes.adminRoutes
import com.sorenkai.api.services.AdminService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAdminService : AdminService(
    storyRepo = com.sorenkai.api.repo.StoryRepository(),
    commentRepo = com.sorenkai.api.repo.CommentRepository(),
    reportRepo = com.sorenkai.api.repo.ReportRepository()
) {
    override suspend fun exportByUid(uid: String): AdminService.UserExport =
        AdminService.UserExport(uid = uid, stories = emptyList(), comments = emptyList(), reports = emptyList())
}

class AdminRoutesIntegrationTest {

    private fun io.ktor.server.application.Application.testModule(service: AdminService) {
        install(ContentNegotiation) { json() }
        routing { adminRoutes(service) }
    }

    @Test
    fun `mod cannot access admin export (403)`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig()
        }
        // Enable test auth and set mod role
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_ENABLED", "true")
        System.setProperty("TEST_AUTH_UID", "moduser")
        System.setProperty("TEST_AUTH_ROLES", "mod")

        application { testModule(FakeAdminService()) }

        val res = client.get("/v1/admin/users/any/export") {
            header("Authorization", "Bearer dev")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `admin can export empty data (200)`() = testApplication {
        environment { config = io.ktor.server.config.MapApplicationConfig() }
        System.setProperty("ENV", "test")
        System.setProperty("TEST_AUTH_ENABLED", "true")
        System.setProperty("TEST_AUTH_UID", "admin1")
        System.setProperty("TEST_AUTH_ROLES", "admin")

        application { testModule(FakeAdminService()) }

        val res = client.get("/v1/admin/users/someone/export") {
            header("Authorization", "Bearer dev")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"uid\":\"someone\""))
        assertTrue(body.contains("\"stories\":["))
        assertTrue(body.contains("\"comments\":["))
        assertTrue(body.contains("\"reports\":["))
    }
}
