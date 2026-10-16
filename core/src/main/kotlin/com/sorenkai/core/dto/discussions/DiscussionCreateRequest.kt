package com.sorenkai.core.dto.discussions

import com.sorenkai.core.model.community.discussions.Kind
import kotlinx.serialization.Serializable

/**
 * Request payload for creating a new discussion item (post or comment).
 * DTOs live in core to avoid UI/server coupling and to be shared across modules.
 */
@Serializable
data class DiscussionCreateRequest(
    val kind: Kind,
    val body: String,
    val lang: String = "en",
    val writingId: String? = null,
    val parentId: String? = null,
)
