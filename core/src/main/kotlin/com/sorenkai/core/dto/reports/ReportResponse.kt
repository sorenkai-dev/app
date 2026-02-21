package com.sorenkai.core.dto.reports

import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

/**
 * Public projection for reports returned to the reporting user.
 * Excludes createdBy.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
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
@OptIn(ExperimentalUuidApi::class)
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

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Report.toPublicResponse(): ReportResponsePublic =
    ReportResponsePublic(
        id = id.toString(),
        targetType = targetType,
        targetId = targetId.toString(),
        reason = reason,
        createdAt = createdAt,
        status = status,
    )

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Report.toAdminResponse(): ReportResponseAdmin =
    ReportResponseAdmin(
        id = id.toString(),
        targetType = targetType,
        targetId = targetId.toString(),
        reason = reason,
        createdAt = createdAt,
        createdBy = createdBy,
        status = status,
    )
