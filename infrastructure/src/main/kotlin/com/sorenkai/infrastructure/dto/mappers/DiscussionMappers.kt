package com.sorenkai.infrastructure.dto.mappers

import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.infrastructure.dto.DiscussionMongoEntity
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Discussion.toMongoEntity(): DiscussionMongoEntity =
    DiscussionMongoEntity(
        id = ObjectId(this.id),
        kind = this.kind,
        writingId = this.writingId?.let { ObjectId(it) },
        parentId = this.parentId?.let { ObjectId(it) },
        userId = this.userId,
        body = this.body,
        lang = this.lang,
        // Moderation fields
        status = this.status,
        reason = this.reason,
        moderatedBy = this.moderatedBy,
        moderatedAt = this.moderatedAt,
        reportedBy = this.reportedBy,
        // Admin fields
        isPinned = this.isPinned,
        isLocked = this.isLocked,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

/**
 * Converts the Infrastructure Persistence Entity back to the Core Domain Model.
 * This is defined in the infrastructure module because it extends the persistence DTO.
 */
@OptIn(ExperimentalTime::class)
fun DiscussionMongoEntity.toCoreModel(): Discussion =
    Discussion(
        id = this.id.toHexString(),
        kind = this.kind,
        writingId = this.writingId?.toHexString(),
        parentId = this.parentId?.toHexString(),
        userId = this.userId,
        body = this.body,
        lang = this.lang,
        // Moderation fields
        status = this.status,
        reason = this.reason,
        moderatedBy = this.moderatedBy,
        moderatedAt = this.moderatedAt,
        reportedBy = this.reportedBy,
        // Admin fields
        isPinned = this.isPinned,
        isLocked = this.isLocked,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
