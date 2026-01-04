package com.sorenkai.infrastructure.dto

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.kotlinx.ObjectIdSerializer
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
@Serializable
data class DiscussionMongoEntity
    constructor(
        @SerialName("_id") @Serializable(with = ObjectIdSerializer::class) val id: ObjectId = ObjectId(),
        val kind: Kind,
        @Serializable(with = ObjectIdSerializer::class) val writingId: ObjectId? = null,
        @Serializable(with = ObjectIdSerializer::class) val parentId: ObjectId? = null,
        val userId: UID,
        val body: String,
        val lang: String = "en",
        // Moderation
        val status: DiscussionsStatus = DiscussionsStatus.VISIBLE,
        val reason: String? = null,
        val moderatedBy: UID? = null,
        @Contextual val moderatedAt: Instant? = null,
        // Reporting
        val reportedBy: Set<UID> = emptySet(),
        // Admin
        val isPinned: Boolean = false,
        val isLocked: Boolean = false,
        @Contextual val createdAt: Instant,
        @Contextual val updatedAt: Instant? = null,
    )
