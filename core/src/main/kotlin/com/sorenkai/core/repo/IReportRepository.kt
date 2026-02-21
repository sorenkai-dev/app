package com.sorenkai.core.repo

import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface IReportRepository {
    suspend fun create(doc: Report): Report

    suspend fun findById(id: Uuid): Report?

    /**
     * Returns true if a report exists by this user for this target that is currently OPEN or IN_REVIEW.
     */
    suspend fun existsOpenOrReviewed(
        createdBy: String,
        targetType: TargetType,
        targetId: Uuid,
    ): Boolean

    suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: Uuid?,
        limit: Int,
        offset: Int,
    ): List<Report>

    suspend fun updateStatus(
        id: Uuid,
        newStatus: ReportStatus,
    ): Boolean
}
