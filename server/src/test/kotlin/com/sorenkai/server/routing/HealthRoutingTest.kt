package com.sorenkai.server.routing

import com.sorenkai.core.health.ComponentStatus
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.core.health.HealthResult
import com.sorenkai.server.configuration.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

class HealthRoutingTest {
    private class FakeHealthCheck(
        override val name: String,
        val ok: Boolean,
    ) : HealthCheck {
        override suspend fun check() = ComponentStatus(ok = ok, error = if (ok) null else "failed")
    }

    private fun Application.testModule(checks: List<HealthCheck>) {
        configureSerialization()
        install(Koin) {
            modules(
                module {
                    checks.forEach { check ->
                        single(named(check.name)) { check }
                    }
                },
            )
        }
        routing {
            healthRoutes()
        }
    }

    @Test
    fun `serverhealth returns 200 when all checks pass`() =
        testApplication {
            application {
                testModule(listOf(FakeHealthCheck("db", true), FakeHealthCheck("cache", true)))
            }

            val response = client.get("/serverhealth")
            assertEquals(HttpStatusCode.OK, response.status)
            val result = Json.decodeFromString<HealthResult>(response.bodyAsText())
            assertTrue(result.ok)
            assertEquals(2, result.components.size)
            assertTrue(result.components["db"]!!.ok)
            assertTrue(result.components["cache"]!!.ok)
        }

    @Test
    fun `serverhealth returns 503 when any check fails`() =
        testApplication {
            application {
                testModule(listOf(FakeHealthCheck("db", true), FakeHealthCheck("cache", false)))
            }

            val response = client.get("/serverhealth")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val result = Json.decodeFromString<HealthResult>(response.bodyAsText())
            assertFalse(result.ok)
            assertTrue(result.components["db"]!!.ok)
            assertFalse(result.components["cache"]!!.ok)
        }

    @Test
    fun `healthz returns 200 always`() =
        testApplication {
            application {
                testModule(emptyList())
            }

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"ok\":true"))
        }
}
