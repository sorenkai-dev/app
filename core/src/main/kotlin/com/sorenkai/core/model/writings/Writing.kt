package com.sorenkai.core.model.writings

import com.sorenkai.common.util.newId
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class Writing
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String = newId(),
        val title: String,
        val language: String,
        val slug: String,
        val synopsis: String? = null,
        val content: String? = null,
        val writingsStatus: WritingsStatus,
        val coverUrl: String? = null,
        val authorId: String,
        @Contextual val publishedAt: Instant? = null,
        @Contextual val createdAt: Instant,
        @Contextual val updatedAt: Instant?,
        val writingsStats: WritingsStats = WritingsStats(),
        val writingsType: WritingsType = WritingsType.ARTICLE,
        val salesLink: String? = null,
        // v1.1 additions
        val tags: List<String> = emptyList(),
        val featured: Boolean? = null,
        val seoDescription: String? = null,
        val jsonLd: JsonObject? = null,
    ) {
        @Serializable
        data class WritingsStats(
            val commentCount: Int = 0,
            val viewCount: Int = 0,
            // v1.1 additions
            val shareCount: Int = 0,
            val salesClicks: Int = 0,
        )
    }
