package com.sorenkai.core.dto.discussions

import com.sorenkai.core.auth.UserData
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Public projection for discussions returned to the frontend.
 * Includes all fields except moderation actor/timestamp (moderatedBy, moderatedAt).
 */
@Serializable
data class DiscussionResponsePublic
    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    constructor(
        val id: Uuid,
        val kind: Kind,
        val writingId: String? = null,
        val parentId: Uuid? = null,
        val userId: String,
        val author: UserData? = null,
        val body: String,
        // Moderation (public-safe subset)
        val status: DiscussionsStatus,
        val reason: String? = null,
        // Admin flags
        val isPinned: Boolean = false,
        val isLocked: Boolean = false,
        // Engagement
        val likesCount: Int = 0,
        val isLikedByMe: Boolean = false,
        val isReportedByMe: Boolean = false,
        val childCount: Int = 0,
        // Timestamps
        val createdAt: Instant,
        val updatedAt: Instant? = null,
    )

/**
 * Admin projection for discussions returned to the admin dashboard.
 * Includes all fields except `likes`.
 */
@Serializable
data class DiscussionResponseAdmin
    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    constructor(
        val id: Uuid,
        val kind: Kind,
        val writingId: String? = null,
        val parentId: Uuid? = null,
        val userId: String,
        val body: String,
        // Moderation (full for admin)
        val status: DiscussionsStatus,
        val reason: String? = null,
        val moderatedBy: UID? = null,
        val moderatedAt: Instant? = null,
        // Reporting
        val reportedBy: Set<UID> = emptySet(),
        // Admin flags
        val isPinned: Boolean = false,
        val isLocked: Boolean = false,
        val childCount: Int = 0,
        val createdAt: Instant,
        val updatedAt: Instant? = null,
    )

/**
 * Mapping helpers from domain model to response projections.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Discussion.toPublicResponse(
    isLikedByMe: Boolean = false,
    isReportedByMe: Boolean = false,
    likesOverride: Int? = null,
    author: UserData? = null,
    childCount: Int = 0,
): DiscussionResponsePublic =
    DiscussionResponsePublic(
        id = id,
        kind = kind,
        writingId = writingId,
        parentId = parentId,
        userId = userId,
        author = author,
        body = body,
        status = status,
        reason = reason,
        isPinned = isPinned,
        isLocked = isLocked,
        likesCount = likesOverride ?: 0,
        isLikedByMe = isLikedByMe,
        isReportedByMe = isReportedByMe,
        childCount = childCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Discussion.toAdminResponse(childCount: Int = 0): DiscussionResponseAdmin =
    DiscussionResponseAdmin(
        id = id,
        kind = kind,
        writingId = writingId,
        parentId = parentId,
        userId = userId,
        body = body,
        status = status,
        reason = reason,
        moderatedBy = moderatedBy,
        moderatedAt = moderatedAt,
        reportedBy = reportedBy,
        isPinned = isPinned,
        isLocked = isLocked,
        childCount = childCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
