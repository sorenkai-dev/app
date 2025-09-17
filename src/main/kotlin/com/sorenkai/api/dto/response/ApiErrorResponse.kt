package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val ok: Boolean = false,
    val error: ApiErrorBody
)
