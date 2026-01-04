package com.sorenkai.core.dto.writings

import kotlinx.serialization.Serializable

@Serializable
data class ShareResponse(
    val slug: String,
    val shareCount: Int,
)

@Serializable
data class LikeResponse(
    val slug: String,
    val likesCount: Int,
)

@Serializable
data class ViewResponse(
    val slug: String,
    val viewCount: Int,
)

@Serializable
data class SalesClickResponse(
    val slug: String,
    val salesClicks: Int,
)
