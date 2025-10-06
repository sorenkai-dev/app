package com.sorenkai.api.routes

import com.sorenkai.api.health.MongoHealthCheck
import com.sorenkai.api.model.HealthResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 * Defines routes for health checks.
 *
 * - `/serverhealth`: primary endpoint for human/monitoring checks.
 *
 */
fun Routing.healthRoutes() {
    get("/serverhealth") {
        val checks = listOf(MongoHealthCheck())
        val results = checks.associateBy({ it.name }, { it.check() })
        val overallOk = results.values.all { it.ok }
        call.respond(
            if (overallOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResult(ok = overallOk, components = results)
        )
    }

}
