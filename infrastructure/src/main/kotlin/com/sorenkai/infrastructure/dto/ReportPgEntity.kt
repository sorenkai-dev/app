package com.sorenkai.infrastructure.dto

import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ReportPgEntity(
    val id: Uuid,
    val targetType: TargetType,
    val targetId: Uuid,
    val reason: String,
    val createdAt: Instant,
    val createdBy: String,
    val status: ReportStatus,
)
