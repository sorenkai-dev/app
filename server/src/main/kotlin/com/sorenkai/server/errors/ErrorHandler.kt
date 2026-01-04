package com.sorenkai.server.errors

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.errorHandler(
    errorCode: ErrorCode,
    message: String? = null,
    details: Map<String, String>? = null,
) {
    val errorMsg =
        message ?: when (errorCode) {
            ErrorCode.UNAUTHORIZED -> "Unauthorized"
            ErrorCode.NOT_FOUND -> "Not found"
            ErrorCode.FORBIDDEN -> "Forbidden"
            ErrorCode.VALIDATION_FAILED -> "Bad request"
            ErrorCode.CONFLICT -> "Conflict"
            ErrorCode.RATE_LIMITED -> "Too many requests"
            ErrorCode.TOO_LARGE -> "Payload too large"
            ErrorCode.INTERNAL -> "Internal server error"
        }
    this.respond(
        errorCode.http,
        ErrorResponses(
            false,
            ErrorBody(
                errorCode.name,
                errorMsg,
                details,
            ),
        ),
    )
}
