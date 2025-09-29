package com.sorenkai.api.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Discussion(
    val id: String = com.sorenkai.api.util.newId(),
    val kind: Kind,
    val storyId: String? = null,
    val parentId: String? = null,
    val userId: String,
    val body: String,
    val likes: Int = 0,
    val hashtags: List<String> = emptyList(),
    val createdAt: Instant = com.sorenkai.api.util.now(),
    val updatedAt: Instant = com.sorenkai.api.util.now()
) {
    @Serializable
    enum class Kind { POST, COMMENT }
}
