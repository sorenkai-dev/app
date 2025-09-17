package com.sorenkai.api.dto

import kotlinx.serialization.Serializable

/**
 * Request payload for creating a new Story.
 *
 * Validation (enforced server-side):
 * - [title]  : non-empty, reasonable max length (e.g., 200)
 * - [url]    : absolute URL (http/https)
 * - [summary]: optional, trimmed, reasonable max length (e.g., 2k)
 * - [tags]   : optional, small list of short, lowercase slugs
 */
@Serializable
data class StoryCreateRequest(
    /** Human-readable title shown in lists and story pages. */
    val title: String,

    /** Canonical, absolute URL to the external story resource. */
    val url: String,

    /** Optional short description for context or previews. */
    val summary: String? = null,

    /** Optional tag slugs (e.g., ["kotlin", "ktor"]). */
    val tags: List<String> = emptyList()
)
