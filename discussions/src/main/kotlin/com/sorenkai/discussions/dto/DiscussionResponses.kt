package com.sorenkai.discussions.dto

import kotlinx.serialization.Serializable
import org.kindredhq.discussions.core.domain.enums.DiscussionsStatus
import org.kindredhq.discussions.core.domain.model.Discussion
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DiscussionResponsePublic(
    val id: Uuid,
    val targetId: Uuid?,
    val parentId: Uuid?,
    val userId: String,
    val language: String?,
    val body: String,
    val status: DiscussionsStatus,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val hasChildren: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
    val likeCount: Long,
    val isLikedByMe: Boolean,
    val isDeletedByMe: Boolean
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class DiscussionResponseAdmin(
    val id: Uuid,
    val targetId: Uuid?,
    val parentId: Uuid?,
    val userId: String,
    val language: String?,
    val body: String,
    val status: DiscussionsStatus,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val hasChildren: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
    val likeCount: Long,
    val isLikedByMe: Boolean,
    val isDeletedByMe: Boolean,
    // Admin fields
    val reason: String?,
    val moderatedBy: String?,
    val moderatedAt: Instant?,
    val reportedByCount: Int,
    val reportedBy: Set<String>,
    val purgeAt: Instant?
)

@OptIn(ExperimentalUuidApi::class)
fun Discussion.toPublic(
    currentUserId: String?,
    hasChildren: Boolean,
    likeCount: Long,
    isLikedByMe: Boolean
): DiscussionResponsePublic {
    val displayBody = if ((status == DiscussionsStatus.DELETED_BY_AUTHOR || status == DiscussionsStatus.DELETED_BY_MODERATOR) && hasChildren) {
        "This post has been deleted"
    } else {
        body
    }

    return DiscussionResponsePublic(
        id = id,
        targetId = targetId,
        parentId = parentId,
        userId = userId,
        language = language,
        body = displayBody,
        status = status,
        isPinned = isPinned,
        isLocked = isLocked,
        hasChildren = hasChildren,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        likeCount = likeCount,
        isLikedByMe = isLikedByMe,
        isDeletedByMe = currentUserId == userId && (status == DiscussionsStatus.DELETED_BY_AUTHOR)
    )
}

@OptIn(ExperimentalUuidApi::class)
fun Discussion.toAdmin(
    currentUserId: String?,
    hasChildren: Boolean,
    likeCount: Long,
    isLikedByMe: Boolean,
    reportedBy: Set<String> = emptySet()
): DiscussionResponseAdmin {
    val public = toPublic(currentUserId, hasChildren, likeCount, isLikedByMe)
    return DiscussionResponseAdmin(
        id = public.id,
        targetId = public.targetId,
        parentId = public.parentId,
        userId = public.userId,
        language = public.language,
        body = public.body,
        status = public.status,
        isPinned = public.isPinned,
        isLocked = public.isLocked,
        hasChildren = public.hasChildren,
        createdAt = public.createdAt,
        updatedAt = public.updatedAt,
        deletedAt = public.deletedAt,
        likeCount = public.likeCount,
        isLikedByMe = public.isLikedByMe,
        isDeletedByMe = public.isDeletedByMe,
        reason = reason,
        moderatedBy = moderatedBy,
        moderatedAt = moderatedAt,
        reportedByCount = reportedBy.size,
        reportedBy = reportedBy,
        purgeAt = purgeAt
    )
}




