package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

/**
 * Represents the aggregated health status of the backend service.
 *
 * This is the top-level response returned by `/serverhealth`.
 *
 * @property ok Overall health status (`true` only if all critical components are healthy).
 * @property components A map of individual component statuses keyed by component name.
 * @property error Optional high-level error message if the overall health check fails.
 * @property uptimeSeconds Total uptime of the application process, in seconds.
 * @property environment Current runtime environment (e.g., "staging", "prod").
 * @property version Application version string (injected at build/deploy time).
 * @property commit Git commit hash or identifier for the deployed build.
 */
@Serializable
data class HealthResult(
    val ok: Boolean,
    val components: Map<String, ComponentStatus> = emptyMap(),
    val error: String? = null,
    val uptimeSeconds: Long? = null,
    val environment: String? = null,
    val version: String? = null,
    val commit: String? = null
)
