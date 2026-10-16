package com.sorenkai.api.health

/**
 * A contract for individual subsystem health checks.
 *
 * Implementations should provide the [name] of the subsystem
 * and a [check] method that returns a [com.sorenkai.server.health.ComponentStatus].
 *
 * Health checks should be fast, non-blocking, and use timeouts
 * to avoid hanging the health endpoint.
 */
interface HealthCheck {
    val name: String

    suspend fun check(): ComponentStatus
}




