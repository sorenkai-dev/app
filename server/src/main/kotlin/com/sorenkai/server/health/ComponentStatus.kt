package com.sorenkai.server.health

import kotlinx.serialization.Serializable

/**
 * Represents the health status of an individual component.
 *
 * @property ok True if the component is healthy.
 * @property error Optional error message if unhealthy.
 */
@Serializable
data class ComponentStatus(
    val ok: Boolean,
    val error: String? = null,
)
