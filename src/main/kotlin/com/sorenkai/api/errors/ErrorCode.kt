package com.sorenkai.api.errors

import io.ktor.http.HttpStatusCode

enum class ErrorCode(val http: HttpStatusCode) {
    UNAUTHORIZED(HttpStatusCode.Unauthorized),
    FORBIDDEN(HttpStatusCode.Forbidden),
    NOT_FOUND(HttpStatusCode.NotFound),
    VALIDATION_FAILED(HttpStatusCode.BadRequest),
    CONFLICT(HttpStatusCode.Conflict),
    RATE_LIMITED(HttpStatusCode.TooManyRequests),
    TOO_LARGE(HttpStatusCode.PayloadTooLarge),
    INTERNAL(HttpStatusCode.InternalServerError)
}
