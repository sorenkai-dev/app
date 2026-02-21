package com.sorenkai.reports.services

import com.sorenkai.common.util.Constants.MAX_ADMIN_REPORTS
import com.sorenkai.common.util.Constants.MAX_BODY_SIZE
import com.sorenkai.core.dto.reports.ReportCreateRequest
import com.sorenkai.core.dto.reports.ReportResponseAdmin
import com.sorenkai.core.dto.reports.ReportResponsePublic
import com.sorenkai.core.dto.reports.toAdminResponse
import com.sorenkai.core.dto.reports.toPublicResponse
import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import com.sorenkai.core.repo.IReportRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReportService(
    private val reports: IReportRepository,
) {
    class ValidationException(
        message: String,
    ) : RuntimeException(message)

    class ConflictException(
        message: String,
    ) : RuntimeException(message)

    class NotFoundException(
        message: String,
    ) : RuntimeException(message)

    @OptIn(ExperimentalTime::class)
    suspend fun create(
        uid: String,
        req: ReportCreateRequest,
    ): ReportResponsePublic {
        if (uid.isBlank()) throw ValidationException("Missing uid")
        val reason = req.reason.trim()
        if (reason.length !in 1..MAX_BODY_SIZE) throw ValidationException("Reason must be 1..$MAX_BODY_SIZE chars")
        if (req.targetId.isBlank()) throw ValidationException("Missing targetId")

        val targetType = parseTargetType(req.targetType)
        val targetId = parseUuid(req.targetId)

        val exists = reports.existsOpenOrReviewed(uid, targetType, targetId)
        if (exists) throw ConflictException("Duplicate report exists for this target")

        val now = Clock.System.now()
        val toInsert =
            Report(
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                createdAt = now,
                createdBy = uid,
                status = ReportStatus.OPEN,
            )
        return reports.create(toInsert).toPublicResponse()
    }

    suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: String?,
        limit: Int,
        offset: Int,
    ): List<ReportResponseAdmin> {
        val lim = limit.coerceIn(1, MAX_ADMIN_REPORTS)
        val off = offset.coerceAtLeast(0)
        val tid = targetId?.let { parseUuid(it) }
        return reports.listAdmin(status, targetType, tid, lim, off).map { it.toAdminResponse() }
    }

    suspend fun updateStatus(
        actorUid: String,
        id: String,
        newStatus: ReportStatus,
    ): Boolean {
        if (actorUid.isBlank()) throw ValidationException("Missing actor uid")
        val uuid = parseUuid(id)
        val current = reports.findById(uuid) ?: return false
        // Transition rules: OPEN -> IN_REVIEW -> RESOLVED only
        val ok =
            when (current.status) {
                ReportStatus.OPEN -> newStatus == ReportStatus.IN_REVIEW
                ReportStatus.IN_REVIEW -> newStatus == ReportStatus.RESOLVED
                ReportStatus.RESOLVED -> false
            }
        if (!ok) throw ConflictException("Invalid status transition: ${current.status} -> $newStatus")

        val updated = reports.updateStatus(uuid, newStatus)
        return updated
    }

    private fun parseTargetType(raw: String): TargetType =
        runCatching { TargetType.valueOf(raw.uppercase()) }
            .getOrElse { throw ValidationException("Invalid targetType") }

    private fun parseUuid(raw: String): Uuid =
        runCatching { Uuid.parse(raw) }
            .getOrElse { throw ValidationException("Invalid ID format") }
}
