package com.sorenkai.core.dto.reports

import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Public projection for reports returned to the reporting user.
 * Excludes createdBy.
 */
@Serializable
data class ReportResponsePublic
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val targetType: TargetType,
        val targetId: String,
        val reason: String,
        @Contextual val createdAt: Instant,
        val status: ReportStatus,
    )

/**
 * Admin projection for reports returned to moderation/admin tools.
 * Includes all fields.
 */
@Serializable
data class ReportResponseAdmin
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val targetType: TargetType,
        val targetId: String,
        val reason: String,
        @Contextual val createdAt: Instant,
        val createdBy: String,
        val status: ReportStatus,
    )

@OptIn(ExperimentalTime::class)
fun Report.toPublicResponse(): ReportResponsePublic =
    ReportResponsePublic(
        id = id,
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        createdAt = createdAt,
        status = status,
    )

@OptIn(ExperimentalTime::class)
fun Report.toAdminResponse(): ReportResponseAdmin =
    ReportResponseAdmin(
        id = id,
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        createdAt = createdAt,
        createdBy = createdBy,
        status = status,
    )
