package com.sorenkai.api.integration

import com.sorenkai.api.errors.configureExceptionMapping
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExceptionMappingTest {

    @Test
    fun rate_limited_includes_retry_after_30() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            // Use our centralized exception mapping
            configureExceptionMapping()
            routing {
                get("/ratelimit") {
                    throw com.sorenkai.api.services.ReportService.RateLimitedException("Too many requests")
                }
            }
        }
        val res = client.get("/ratelimit")
        assertEquals(HttpStatusCode.TooManyRequests, res.status)
        val retryAfter = res.headers["Retry-After"]
        assertEquals("30", retryAfter)
        // Request ID should be present when CallId plugin is installed in full module, but here it's not.
        // We validate header presence in CallIdIntegrationTest.
    }
}
