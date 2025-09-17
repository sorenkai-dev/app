package com.sorenkai.api.model

import com.sorenkai.api.util.newId
import com.sorenkai.api.util.now
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Comment(
    val id: String = newId(),
    val storyId: String,
    val body: String,
    val createdAt: Instant = now(),
    val createdBy: String
)
