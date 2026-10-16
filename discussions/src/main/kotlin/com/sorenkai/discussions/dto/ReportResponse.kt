package com.sorenkai.discussions.dto

import kotlinx.serialization.Serializable
import org.kindredhq.discussions.core.domain.enums.ReportStatus
import org.kindredhq.discussions.core.domain.model.Report
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class ReportResponsePublic(
    val id: Uuid,
    val targetId: Uuid,
    val reason: String,
    val createdAt: Instant,
    val status: ReportStatus
)

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class ReportResponseAdmin(
    val id: Uuid,
    val targetId: Uuid,
    val reason: String,
    val createdAt: Instant,
    val status: ReportStatus,
    val createdBy: String,
    val moderatedBy: String?,
    val moderatedAt: Instant?,
    val resolutionReason: String?
)

@OptIn(ExperimentalUuidApi::class)
fun Report.toPublicResponse(): ReportResponsePublic =
    ReportResponsePublic(
        id = id,
        targetId = targetId,
        reason = reason,
        createdAt = createdAt,
        status = status
    )

@OptIn(ExperimentalUuidApi::class)
fun ReportWithAudit.toAdmin(): ReportResponseAdmin =
    ReportResponseAdmin(
        id = report.id,
        targetId = report.targetId,
        reason = report.reason,
        createdAt = report.createdAt,
        status = report.status,
        createdBy = report.createdBy,
        moderatedBy = moderatedBy,
        moderatedAt = moderatedAt,
        resolutionReason = resolutionReason
    )




