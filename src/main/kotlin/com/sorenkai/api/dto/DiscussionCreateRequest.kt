package com.sorenkai.api.dto

import com.sorenkai.api.model.Discussion
import kotlinx.serialization.Serializable

@Serializable
data class DiscussionCreateRequest(
    val kind: Discussion.Kind,
    val body: String,
    val storyId: String? = null,
    val parentId: String? = null,
    val hashtags: List<String> = emptyList()
)
