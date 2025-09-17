package com.sorenkai.api.services

import com.sorenkai.api.dto.CommentResponse
import com.sorenkai.api.dto.ReportResponse
import com.sorenkai.api.dto.StoryResponse
import com.sorenkai.api.repo.CommentRepository
import com.sorenkai.api.repo.ReportRepository
import com.sorenkai.api.repo.StoryRepository

/**
 * Admin-only operations for user data export and deletion.
 */
open class AdminService(
    protected val storyRepo: StoryRepository = StoryRepository(),
    protected val commentRepo: CommentRepository = CommentRepository(),
    protected val reportRepo: ReportRepository = ReportRepository(),
) {
    data class UserExport(
        val uid: String,
        val stories: List<StoryResponse>,
        val comments: List<CommentResponse>,
        val reports: List<ReportResponse>
    )

    data class DeleteResult(
        val uid: String,
        val storiesDeleted: Long,
        val commentsDeleted: Long,
        val reportsDeleted: Long
    )

    open suspend fun exportByUid(uid: String): UserExport {
        val stories = storyRepo.listByUser(uid).map {
            StoryResponse(
                id = it.id,
                title = it.title,
                url = it.url,
                summary = it.summary,
                tags = it.tags,
                createdAt = it.createdAt,
                createdBy = it.createdBy
            )
        }
        val comments = commentRepo.listByUser(uid).map {
            CommentResponse(
                id = it.id,
                storyId = it.storyId,
                body = it.body,
                createdAt = it.createdAt,
                createdBy = it.createdBy
            )
        }
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
        return UserExport(uid, stories, comments, reports)
    }

    open suspend fun deleteByUid(uid: String): DeleteResult {
        val s = storyRepo.deleteByUser(uid)
        val c = commentRepo.deleteByUser(uid)
        val r = reportRepo.deleteByUser(uid)
        return DeleteResult(uid, s, c, r)
    }
}
