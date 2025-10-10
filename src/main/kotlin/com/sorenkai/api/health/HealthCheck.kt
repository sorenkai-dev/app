package com.sorenkai.api.health

import com.sorenkai.api.dto.response.ComponentStatus

/**
 * A contract for individual subsystem health checks.
 *
 * Implementations should provide the [name] of the subsystem
 * and a [check] method that returns a [ComponentStatus].
 *
 * Health checks should be fast, non-blocking, and use timeouts
 * to avoid hanging the health endpoint.
 */
interface HealthCheck {
    val name: String
    suspend fun check(): ComponentStatus
}
