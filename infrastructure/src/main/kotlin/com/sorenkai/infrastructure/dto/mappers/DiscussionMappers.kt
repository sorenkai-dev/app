package com.sorenkai.infrastructure.dto.mappers

import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.infrastructure.dto.DiscussionPgEntity
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Discussion.toPgEntity(): DiscussionPgEntity =
    DiscussionPgEntity(
        id = this.id,
        kind = this.kind,
        writingId = this.writingId,
        parentId = this.parentId,
        userId = this.userId,
        body = this.body,
        lang = this.lang,
        status = this.status,
        reason = this.reason,
        moderatedBy = this.moderatedBy,
        moderatedAt = this.moderatedAt,
        reportedBy = this.reportedBy,
        isPinned = this.isPinned,
        isLocked = this.isLocked,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        deletedAt = this.deletedAt,
        purgeAt = this.purgeAt,
    )

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun DiscussionPgEntity.toDomain(): Discussion =
    Discussion(
        id = this.id,
        kind = this.kind,
        writingId = this.writingId,
        parentId = this.parentId,
        userId = this.userId,
        body = this.body,
        lang = this.lang,
        status = this.status,
        reason = this.reason,
        moderatedBy = this.moderatedBy,
        moderatedAt = this.moderatedAt,
        reportedBy = this.reportedBy,
        isPinned = this.isPinned,
        isLocked = this.isLocked,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        deletedAt = this.deletedAt,
        purgeAt = this.purgeAt,
    )
