package com.sorenkai.core.repo

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class DiscussionWithLikes(
    val discussion: Discussion,
    val likesCount: Long,
    val isLikedByMe: Boolean,
    val isReportedByMe: Boolean = false,
)

enum class DiscussionOrder {
    NEWEST,
    OLDEST,
}

@OptIn(ExperimentalUuidApi::class)
interface IDiscussionRepository {
    /* =======================
       Creation
       ======================= */

    suspend fun insert(discussion: Discussion): Discussion

    /* =======================
       Reads
       ======================= */

    suspend fun findById(id: Uuid): Discussion?

    suspend fun findByIdWithLikes(
        id: Uuid,
        currentUserId: UID? = null,
        isModerator: Boolean = false,
    ): DiscussionWithLikes?

    suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID? = null,
        isModerator: Boolean = false,
        order: DiscussionOrder = DiscussionOrder.NEWEST,
    ): List<DiscussionWithLikes>

    suspend fun listComments(
        parentId: Uuid,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID? = null,
        isModerator: Boolean = false,
    ): List<DiscussionWithLikes>

    suspend fun countChildren(parentIds: Set<Uuid>): Map<Uuid, Int>

    /* =======================
       Author-level mutation
       ======================= */

    @OptIn(ExperimentalTime::class)
    suspend fun updateBody(
        id: Uuid,
        body: String,
        updatedAt: Instant,
        currentUserId: UID? = null,
    ): DiscussionWithLikes?

    /* =======================
       Moderation / Admin
       ======================= */

    @OptIn(ExperimentalTime::class)
    suspend fun setStatus(
        id: Uuid,
        status: DiscussionsStatus,
        moderatedBy: UID?,
        moderatedAt: Instant?,
        reason: String?,
        deletedAt: Instant? = null,
        purgeAt: Instant? = null,
        currentUserId: UID? = null,
    ): DiscussionWithLikes?

    suspend fun setPinned(
        id: Uuid,
        pinned: Boolean,
    ): UpdateResult

    suspend fun setLocked(
        id: Uuid,
        locked: Boolean,
    ): UpdateResult

    @OptIn(ExperimentalTime::class)
    suspend fun restore(
        id: Uuid,
        currentUserId: UID? = null,
    ): DiscussionWithLikes?
}
