package com.sorenkai.core.dto.writings

import kotlinx.serialization.Serializable

@Serializable
data class ShareResponse(
    val id: String,
    val shareCount: Int,
)

@Serializable
data class LikeResponse(
    val id: String,
    val likesCount: Int,
)

@Serializable
data class ViewResponse(
    val id: String,
    val viewCount: Int,
)

@Serializable
data class SalesClickResponse(
    val id: String,
    val salesClicks: Int,
)
