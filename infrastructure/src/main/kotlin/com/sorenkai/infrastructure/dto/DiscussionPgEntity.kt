package com.sorenkai.infrastructure.dto

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class DiscussionPgEntity(
    val id: Uuid,
    val kind: Kind,
    val writingId: String?,
    val parentId: Uuid?,
    val userId: UID,
    val body: String,
    val lang: String,
    val status: DiscussionsStatus,
    val reason: String?,
    val moderatedBy: UID?,
    val moderatedAt: Instant?,
    val reportedBy: Set<UID>,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
    val purgeAt: Instant?,
)
