package com.sorenkai.community.discussions.dto

import com.sorenkai.community.discussions.model.Discussion
import kotlinx.serialization.Serializable

/**
 * Request payload for creating a new [com.sorenkai.api.model.Discussion] entity (post or comment).
 *
 * This DTO is consumed by the discussions API (`POST /v1/discussions`)
 * and mirrors the fields expected by [DiscussionService.create].
 *
 * @property kind The type of discussion being created:
 *  - [com.sorenkai.api.model.Discussion.Kind.POST] for a new top-level thread
 *  - [com.sorenkai.api.model.Discussion.Kind.COMMENT] for a reply to an existing post
 * @property body The textual content of the post or comment.
 *   - Required, non-blank, maximum 5000 characters.
 * @property writingSlug Optional slug of a writing/article that the post
 *   is attached to. Only valid when `kind == POST`.
 * @property parentId Optional ID of the parent discussion.
 *   - Required when `kind == COMMENT`.
 * @property hashtags Optional list of hashtags provided by the client.
 *   - May be empty; additional hashtags may also be auto-extracted from [body].
 *
 * ### Example JSON
 * ```json
 * {
 *   "kind": "POST",
 *   "body": "This is my first post! #hello #intro",
 *   "writingSlug": "welcome-article"
 * }
 * ```
 */
@Serializable
data class DiscussionCreateRequest(
    val kind: Discussion.Kind,
    val body: String,
    val writingSlug: String? = null,
    val parentId: String? = null,
    val hashtags: List<String> = emptyList(),
)
