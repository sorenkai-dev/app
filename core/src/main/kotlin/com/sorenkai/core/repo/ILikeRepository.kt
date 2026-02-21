package com.sorenkai.core.repo

import com.sorenkai.core.model.auth.UID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface ILikeRepository {
    /* =======================
       Write operations
       ======================= */

    /** Idempotently likes a discussion or comment. */
    suspend fun likeDiscussion(
        discussionId: Uuid,
        userId: UID,
    )

    /** Removes a like from a discussion or comment. */
    suspend fun unlikeDiscussion(
        discussionId: Uuid,
        userId: UID,
    )

    /** Idempotently likes a writing. */
    suspend fun likeWriting(
        writingId: String,
        userId: UID,
    )

    /** Removes a like from a writing. */
    suspend fun unlikeWriting(
        writingId: String,
        userId: UID,
    )

    /* =======================
       Read operations
       ======================= */

    /** Counts likes for discussions/comments. */
    suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long>

    /** Counts likes for writings. */
    suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long>

    /** Returns discussion IDs liked by the given user. */
    suspend fun getLikedDiscussions(
        discussionIds: Set<Uuid>,
        userId: UID,
    ): Set<Uuid>

    /** Returns writing IDs liked by the given user. */
    suspend fun getLikedWritings(
        writingIds: Set<String>,
        userId: UID,
    ): Set<String>
}
