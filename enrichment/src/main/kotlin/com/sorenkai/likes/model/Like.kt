package com.sorenkai.likes.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Like(
    val id: String,
    val userId: String,
    val targetId: String,
    val targetType: LikeTarget,
    val createdAt: Instant
)
