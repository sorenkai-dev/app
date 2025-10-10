package com.sorenkai.api.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Writing(
    val id: String = com.sorenkai.api.util.newId(),
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val content: String,
    val status: Status,
    val coverUrl: String? = null,
    val authorId: String,
    val publishedAt: Instant? = null,
    val createdAt: Instant = com.sorenkai.api.util.now(),
    val updatedAt: Instant = com.sorenkai.api.util.now(),
    val stats: Stats = Stats()
) {
    @Serializable
    enum class Status { DRAFT, PUBLISHED }

    @Serializable
    data class Stats(
        val commentCount: Int = 0,
        val viewCount: Int = 0,
        val likeCount: Int = 0
    )
}
