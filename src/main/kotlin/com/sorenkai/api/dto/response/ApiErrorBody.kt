package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)
