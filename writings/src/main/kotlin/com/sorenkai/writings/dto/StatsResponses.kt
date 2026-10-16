package com.sorenkai.writings.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShareResponse(
    val id: String,
    val shareCount: Int,
)

@Serializable
data class ViewResponse(
    val id: String,
    val viewCount: Int,
)


