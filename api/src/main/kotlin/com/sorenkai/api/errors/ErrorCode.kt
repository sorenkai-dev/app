package com.sorenkai.api.errors

import io.ktor.http.HttpStatusCode

/**
 * Standardized error codes for API responses.
 *
 * Each code maps directly to an [HttpStatusCode] to ensure consistency
 * between domain exceptions and HTTP responses.
 *
 * These codes are used by [ServerExceptions] and surfaced in
 * [ErrorResponses].
 *
 * @property http the corresponding HTTP status to return for this error
 */
enum class ErrorCode(
    val http: HttpStatusCode,
) {
    UNAUTHORIZED(HttpStatusCode.Unauthorized),
    FORBIDDEN(HttpStatusCode.Forbidden),
    NOT_FOUND(HttpStatusCode.NotFound),
    VALIDATION_FAILED(HttpStatusCode.BadRequest),
    CONFLICT(HttpStatusCode.Conflict),
    RATE_LIMITED(HttpStatusCode.TooManyRequests),
    TOO_LARGE(HttpStatusCode.PayloadTooLarge),
    INTERNAL(HttpStatusCode.InternalServerError),
}




