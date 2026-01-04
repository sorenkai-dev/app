package com.sorenkai.core.model.community.discussions

import kotlinx.serialization.Serializable

/**
 * The type of discussion entry:
 * - [POST]: A top-level post (thread starter).
 * - [COMMENT]: A comment or reply under a post.
 */
@Serializable
enum class Kind { POST, COMMENT }
