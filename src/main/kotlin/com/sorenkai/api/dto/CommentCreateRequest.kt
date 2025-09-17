package com.sorenkai.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommentCreateRequest(
    val body: String
)
