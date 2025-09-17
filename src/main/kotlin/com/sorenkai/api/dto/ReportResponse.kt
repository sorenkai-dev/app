package com.sorenkai.api.dto

import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.model.TargetType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ReportResponse(
    val id: String,
    val targetType: TargetType,
    val targetId: String,
    val reason: String,
    val createdAt: Instant,
    val createdBy: String,
    val status: ReportStatus
)
