package com.sorenkai.core.repo

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface IDiscussionRepository {
    // Creation
    suspend fun insert(discussion: Discussion): Discussion

    // Reads
    suspend fun findById(id: String): Discussion?

    suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion>

    suspend fun listComments(
        parentId: String,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion>

    // Author-level mutation
    @OptIn(ExperimentalTime::class)
    suspend fun updateBody(
        id: String,
        body: String,
        updatedAt: Instant,
    ): Boolean

    // Moderation / Admin mutations
    @OptIn(ExperimentalTime::class)
    suspend fun setStatus(
        id: String,
        status: DiscussionsStatus,
        moderatedBy: UID?,
        moderatedAt: Instant?,
        reason: String?,
    ): Boolean

    suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult

    suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult
}
