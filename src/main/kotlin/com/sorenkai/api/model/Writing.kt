package com.sorenkai.api.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Writing(
    val id: String = com.sorenkai.api.util.newId(),
    val title: String,
    val language: String,
    val slug: String,
    val synopsis: String? = null,
    val content: String? = null,
    val status: Status,
    val coverUrl: String? = null,
    val authorId: String,
    @Contextual val publishedAt: Instant? = null,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant?,
    val stats: Stats = Stats(),
    val type: Type = Type.ARTICLE,
    val salesLink: String? = null,
    // v1.1 additions
    val tags: List<String> = emptyList(),
    val featured: Boolean? = null,
    val seoDescription: String? = null
) {
    @Serializable
    enum class Status { DRAFT, PUBLISHED, ARCHIVED, SUBMITTED }

    @Serializable
    enum class Type { ARTICLE, BOOK }

    @Serializable
    data class Stats(
        val commentCount: Int = 0,
        val viewCount: Int = 0,
        val likeCount: Int = 0,
        // v1.1 additions
        val shareCount: Int = 0,
        val salesClicks: Int = 0
    )
}
