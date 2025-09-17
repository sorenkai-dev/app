package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val ok: Boolean = true,
    val data: T? = null
)
