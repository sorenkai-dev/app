package com.sorenkai.core.dto.discussions

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Public projection for discussions returned to the frontend.
 * Includes all fields except moderation actor/timestamp (moderatedBy, moderatedAt).
 */
@Serializable
data class DiscussionResponsePublic
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val kind: Kind,
        val writingId: String? = null,
        val parentId: String? = null,
        val userId: String,
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
        // Timestamps
        @Contextual val createdAt: Instant,
        @Contextual val updatedAt: Instant? = null,
    )

/**
 * Admin projection for discussions returned to the admin dashboard.
 * Includes all fields except `likes`.
 */
@Serializable
data class DiscussionResponseAdmin
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val kind: Kind,
        val writingId: String? = null,
        val parentId: String? = null,
        val userId: String,
        val body: String,
        // Moderation (full for admin)
        val status: DiscussionsStatus,
        val reason: String? = null,
        val moderatedBy: UID? = null,
        @Contextual val moderatedAt: Instant? = null,
        // Reporting
        val reportedBy: Set<UID> = emptySet(),
        // Admin flags
        val isPinned: Boolean = false,
        val isLocked: Boolean = false,
        @Contextual val createdAt: Instant,
        @Contextual val updatedAt: Instant? = null,
    )

/**
 * Mapping helpers from domain model to response projections.
 */
@OptIn(ExperimentalTime::class)
fun Discussion.toPublicResponse(
    isLikedByMe: Boolean = false,
    likesOverride: Int? = null,
): DiscussionResponsePublic =
    DiscussionResponsePublic(
        id = id,
        kind = kind,
        writingId = writingId,
        parentId = parentId,
        userId = userId,
        body = body,
        status = status,
        reason = reason,
        isPinned = isPinned,
        isLocked = isLocked,
        likesCount = likesOverride ?: 0,
        isLikedByMe = isLikedByMe,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

@OptIn(ExperimentalTime::class)
fun Discussion.toAdminResponse(): DiscussionResponseAdmin =
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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
