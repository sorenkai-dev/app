package com.sorenkai.api.integration

import com.sorenkai.api.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CallIdIntegrationTest {

    @Test
    fun request_id_is_logged_and_returned() = testApplication {
        application {
            module()
        }
        // No header -> server should generate one
        val res1 = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, res1.status)
        val id1 = res1.headers["X-Request-ID"]
        assertNotNull(id1)

        // Provide custom header; it should be echoed back
        val custom = "test-req-123"
        val res2 = client.get("/healthz") {
            header("X-Request-ID", custom)
        }
        assertEquals(HttpStatusCode.OK, res2.status)
        val id2 = res2.headers["X-Request-ID"]
        assertEquals(custom, id2)
    }
}
