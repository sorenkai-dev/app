package com.sorenkai.server.errors

import kotlinx.serialization.Serializable

/**
 * Standardized error body returned in API responses when a request fails.
 *
 * This structure is wrapped by [ErrorResponses] and ensures consistency across
 * all endpoints when communicating errors to clients.
 *
 * @property code A short, machine-readable error code. Usually taken from [com.sorenkai.ErrorCode].
 *   Example: `"UNAUTHORIZED"`, `"FORBIDDEN"`, `"NOT_FOUND"`, `"TOO_LARGE"`.
 * @property message A human-readable description of the error, intended for display
 *   or logging. Example: `"Unauthorized"`, `"Request entity too large"`.
 * @property details An optional key/value map with additional context about the error.
 *   Useful for field-level validation errors or extra diagnostic info.
 *
 * ### Example JSON
 * ```json
 * {
 *   "code": "VALIDATION_FAILED",
 *   "message": "reason must be <= 2000 chars",
 *   "details": { "field": "reason" }
 * }
 * ```
 */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)
