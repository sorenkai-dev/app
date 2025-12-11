package com.sorenkai.api.dto

import com.sorenkai.api.model.Writing
import com.sorenkai.api.model.Writing.Status
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Lightweight summary of a published writing.
 *
 * Returned by list endpoints (e.g., `GET /v1/writings`) to display metadata
 * without including the full article content.
 *
 * @property title The title of the writing.
 * @property slug The unique slug identifier for the writing, used in URLs.
 * @property synopsis Optional short summary or teaser text for the writing.
 * @property coverUrl Optional cover image URL (absolute or relative path).
 * @property publishedAt Timestamp when the writing was published (nullable for drafts).
 * @property updatedAt Timestamp when the writing was last updated (nullable if never updated).
 *
 * ### Example JSON
 * ```json
 * {
 *   "title": "The Future of Kotlin Multiplatform",
 *   "slug": "future-of-kmp",
 *   "synopsis": "Exploring the growth of Kotlin Multiplatform in 2025.",
 *   "coverUrl": "https://cdn.example.com/covers/kmp.png",
 *   "publishedAt": "2025-01-12T10:00:00Z",
 *   "updatedAt": "2025-01-20T18:45:00Z"
 * }
 * ```
 */
@Serializable
data class WritingListItem(
    val title: String,
    val slug: String,
    val language: String,
    val synopsis: String? = null,
    val status: Status,
    val type: Writing.Type,
    val salesLink: String? = null,
    val tags: List<String> = emptyList(),
    val featured: Boolean? = null,
    val seoDescription: String? = null,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant?,
    @Contextual val publishedAt: Instant?,
    val stats: Stats
){
    @Serializable
    data class Stats(
        val commentCount: Int,
        val viewCount: Int,
        val likeCount: Int,
        val shareCount: Int,
        val salesClicks: Int
    )
}

/**
 * Full details of a writing, including article content and stats.
 *
 * Returned by `GET /v1/writings/{slug}` when fetching a single writing.
 *
 * @property title The title of the writing.
 * @property slug The unique slug identifier for the writing, used in URLs.
 * @property synopsis Optional short summary or teaser text.
 * @property content The full Markdown content of the writing.
 * @property status The current status (DRAFT or PUBLISHED).
 * @property coverUrl Optional cover image URL (absolute or relative path).
 * @property authorId The UID of the author who created the writing.
 * @property publishedAt Timestamp when the writing was published (nullable if draft).
 * @property createdAt Timestamp when the writing was first created.
 * @property updatedAt Timestamp when the writing was last updated (nullable if never updated).
 * @property stats Aggregated statistics for engagement (comments, views, likes).
 *
 * ### Example JSON
 * ```json
 * {
 *   "title": "The Future of Kotlin Multiplatform",
 *   "slug": "future-of-kmp",
 *   "synopsis": "Exploring the growth of Kotlin Multiplatform in 2025.",
 *   "content": "# Introduction\\nKMP is rapidly evolving...",
 *   "status": "PUBLISHED",
 *   "coverUrl": "https://cdn.example.com/covers/kmp.png",
 *   "authorId": "user_12345",
 *   "publishedAt": "2025-01-12T10:00:00Z",
 *   "createdAt": "2025-01-05T14:30:00Z",
 *   "updatedAt": "2025-01-20T18:45:00Z",
 *   "stats": {
 *     "commentCount": 12,
 *     "viewCount": 842,
 *     "likeCount": 57
 *   }
 * }
 * ```
 */
@Serializable
data class WritingDetail(
    val title: String,
    val slug: String,
    val language: String,
    val synopsis: String? = null,
    val type: Writing.Type,
    val salesLink: String? = null,
    val content: String,
    val status: Status,
    val coverUrl: String? = null,
    val authorId: String,
    @Contextual val publishedAt: Instant?,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant?,
    val stats: Stats,
    val tags: List<String> = emptyList(),
    val featured: Boolean? = null,
    val seoDescription: String? = null
) {
    @Serializable
    data class Stats(
        val commentCount: Int,
        val viewCount: Int,
        val likeCount: Int,
        val shareCount: Int,
        val salesClicks: Int
    )
}
