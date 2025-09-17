package com.sorenkai.api.services

import com.sorenkai.api.config.Metrics
import com.sorenkai.api.dto.ReportCreateRequest
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.model.Report
import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.rate.RtdbReportRateLimiter
import com.sorenkai.api.repo.ReportRepository

class ReportService(
    private val repo: ReportRepository = ReportRepository(),
    private val limiter: RateLimiter = RtdbReportRateLimiter()
) {
    suspend fun create(uid: String, req: ReportCreateRequest): Report {
        if (!limiter.checkAndIncrement(uid)) {
            throw RateLimitedException("Too many reports in a short period")
        }
        val report = Report(
            targetType = req.targetType,
            targetId = req.targetId.trim(),
            reason = req.reason.trim(),
            createdBy = uid
        )
        val created = repo.create(report)
        runCatching { Metrics.reportsCreated.increment() }
        return created
    }

    suspend fun page(status: ReportStatus, pageSize: Int, cursor: String?): Pair<List<Report>, Cursor?> =
        repo.pageByStatus(status, pageSize, cursor)

    suspend fun close(reportId: String): Boolean = repo.updateStatus(reportId, ReportStatus.CLOSED)

    class RateLimitedException(msg: String) : RuntimeException(msg)
}
