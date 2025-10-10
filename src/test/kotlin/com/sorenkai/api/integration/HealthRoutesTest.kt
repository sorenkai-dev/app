package com.sorenkai.api.integration

import com.sorenkai.api.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthRoutesTest {

    @Test
    fun healthz_returns_200_and_serverhealth_checks_mongo() = testApplication {
        // Use the full module to ensure routing installed
        application {
            module()
        }
        // Liveness
        val live = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, live.status)

        // Readiness: likely ServiceUnavailable in test (no Mongo), which is acceptable
        val ready = client.get("/serverhealth")
        assertEquals(HttpStatusCode.ServiceUnavailable, ready.status)
        ready.bodyAsText() // ensure body is readable
    }
}
