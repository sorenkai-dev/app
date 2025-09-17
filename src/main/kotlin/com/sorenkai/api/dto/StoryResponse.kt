package com.sorenkai.api.dto

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API response shape for a Story.
 *
 * This is what clients receive from GET/POST endpoints.
 * Server-side fields like [id], [createdAt], and [createdBy] are set by the backend.
 */
@Serializable
data class StoryResponse(
    /** Stable identifier for the story (UUID string). */
    val id: String,
    /** Human-readable title shown in lists and story pages. */
    val title: String,
    /** Canonical, absolute URL to the external story resource. */
    val url: String,
    /** Optional short description for context or previews. */
    val summary: String? = null,
    /** Tag slugs (may be empty). */
    val tags: List<String> = emptyList(),
    /** Creation timestamp in UTC. */
    val createdAt: Instant,
    /** The user who submitted the story (Firebase UID). */
    val createdBy: String
)
