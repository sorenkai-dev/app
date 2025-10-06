package com.sorenkai.api.model

import com.sorenkai.api.dto.response.ComponentStatus
import kotlinx.serialization.Serializable

/**
 * Represents the health status of the application.
 *
 * @property ok Overall status: true if all components are healthy.
 * @property components A map of component names to their individual [com.sorenkai.api.dto.response.ComponentStatus].
 * @property error Optional global error message if the system is unhealthy.
 */
@Serializable
data class HealthResult(
    val ok: Boolean,
    val components: Map<String, ComponentStatus> = emptyMap(),
    val error: String? = null
)
