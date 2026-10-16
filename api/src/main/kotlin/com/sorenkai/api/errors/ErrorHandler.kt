package com.sorenkai.api.errors

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Sends a structured JSON error response for the given [errorCode].
 *
 * Convenience extension on [ApplicationCall] that constructs an [ErrorResponses] body
 * and responds with the appropriate HTTP status code. Used throughout route handlers
 * to return consistent error responses without boilerplate.
 *
 * If [message] is not provided, a default human-readable message is derived from [errorCode].
 * If [details] is provided, it is included in the response body for additional context
 * (e.g. field-level validation errors).
 *
 * @param errorCode the [ErrorCode] defining the HTTP status and machine-readable code
 * @param message optional human-readable override; defaults to a standard message for the code
 * @param details optional key-value map with additional error context
 */
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
