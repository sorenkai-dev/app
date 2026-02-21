package com.sorenkai.core.dto.reports

import kotlinx.serialization.Serializable

/**
 * Request payload for submitting a new abuse/content report.
 * Kept in core so both server and feature modules can share it.
 */
@Serializable
data class ReportCreateRequest(
    val targetType: String,
    val targetId: String,
    val reason: String,
)
