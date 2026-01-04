package com.sorenkai.core.repo

import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType

interface IReportRepository {
    suspend fun create(doc: Report): Report

    suspend fun findById(id: String): Report?

    /**
     * Returns true if a report exists by this user for this target that is currently OPEN or IN_REVIEW.
     */
    suspend fun existsOpenOrReviewed(
        createdBy: String,
        targetType: TargetType,
        targetId: String,
    ): Boolean

    suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: String?,
        limit: Int,
        offset: Int,
    ): List<Report>

    suspend fun updateStatus(
        id: String,
        newStatus: ReportStatus,
    ): Boolean
}
