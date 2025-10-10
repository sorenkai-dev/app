package com.sorenkai.api.services

import com.sorenkai.api.dto.ReportResponse
import com.sorenkai.api.repo.ReportRepository
import kotlinx.serialization.Serializable

/**
 * Admin-only operations for user data export and deletion.
 */
open class AdminService(
    protected val reportRepo: ReportRepository = ReportRepository(),
) {
    @Serializable
    data class UserExport(
        val uid: String,
        val reports: List<ReportResponse>
    )

    @Serializable
    data class DeleteResult(
        val uid: String,
        val reportsDeleted: Long
    )

    open suspend fun exportByUid(uid: String): UserExport {
        val reports = reportRepo.listByUser(uid).map {
            ReportResponse(
                id = it.id,
                targetType = it.targetType,
                targetId = it.targetId,
                reason = it.reason,
                createdAt = it.createdAt,
                createdBy = it.createdBy,
                status = it.status
            )
        }
        return UserExport(uid, reports)
    }

    open suspend fun deleteByUid(uid: String): DeleteResult {
        val r = reportRepo.deleteByUser(uid)
        return DeleteResult(uid, r)
    }
}
