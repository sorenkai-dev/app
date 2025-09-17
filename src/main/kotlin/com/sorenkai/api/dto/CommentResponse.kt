package com.sorenkai.api.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CommentResponse(
    val id: String,
    val storyId: String,
    val body: String,
    val createdAt: Instant,
    val createdBy: String
)
