package com.sorenkai.core.dto.discussions

import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Request payload for creating a new discussion item (post or comment).
 * DTOs live in core to avoid UI/server coupling and to be shared across modules.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
@Serializable
data class DiscussionCreateRequest(
    val kind: Kind,
    val body: String,
    val lang: String = "en",
    val writingId: String? = null,
    val userId: String,
    val parentId: Uuid? = null,
    val status: DiscussionsStatus? = null,
    val createdAt: Instant? = null,
) {
    override fun toString(): String =
        "DiscussionCreateRequest(kind=$kind, body='${body.take(
            20,
        )}...', lang='$lang', writingId=$writingId, parentId=$parentId, status=$status, createdAt=$createdAt)"
}
