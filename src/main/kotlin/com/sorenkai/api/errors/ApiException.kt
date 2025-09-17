package com.sorenkai.api.errors

class ApiException(
    val code: ErrorCode,
    override val message: String,
    val details: Map<String, String>? = null
) : RuntimeException(message)
