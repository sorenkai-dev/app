package com.sorenkai.server.routing

import com.sorenkai.common.util.Constants.SERVER_HEALTH_TIMEOUT
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.core.health.HealthResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import org.koin.ktor.ext.getKoin

/**
 * Defines routes for health checks.
 *
 * - `/healthz`: lightweight liveness probe (no dependencies)
 * - `/serverhealth`: readiness check (includes Mongo and Redis)
 */
fun Route.healthRoutes() {
//    val healthChecks by inject<List<HealthCheck>>()
    // Liveness: simple OK response, no dependencies
    get("/healthz") {
        call.respond(HttpStatusCode.OK, mapOf("ok" to true))
    }

    // Readiness: aggregates all registered HealthCheck implementations.
    get("/serverhealth") {
        try {
            val healthChecks = getKoin().getAll<HealthCheck>()
            val results =
                withTimeout(SERVER_HEALTH_TIMEOUT) {
                    healthChecks
                        .map { check ->
                            async { check.name to check.check() }
                        }.awaitAll()
                        .toMap()
                }

            val overallOk = results.values.all { it.ok }

            call.respond(
                if (overallOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResult(ok = overallOk, components = results),
            )
        } catch (expected: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResult(ok = false, error = "Health check timed out or failed: ${expected.message}"),
            )
        }
    }
}
