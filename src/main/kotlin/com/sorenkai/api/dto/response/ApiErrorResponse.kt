package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

/**
 * Standardized wrapper for API error responses.
 *
 * All failed requests return this shape to ensure consistency across endpoints.
 * The `ok` flag is always `false` and the actual error details are contained
 * in the [error] field.
 *
 * @property ok Always `false` for error responses, included for consistency with
 *   success envelopes that may return `{ "ok": true, ... }`.
 * @property error The structured [ApiErrorBody] describing the failure,
 *   including machine-readable code, human-readable message, and optional details.
 *
 * ### Example JSON
 * ```json
 * {
 *   "ok": false,
 *   "error": {
 *     "code": "UNAUTHORIZED",
 *     "message": "Unauthorized",
 *     "details": null
 *   }
 * }
 * ```
 */
@Serializable
data class ApiErrorResponse(
    val ok: Boolean = false,
    val error: ApiErrorBody
)
