package com.sorenkai.api.errors

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
 * @property error The structured [ErrorBody] describing the failure,
 *   including machine-readable code, human-readable message, and optional details.
 * @property requestId Optional request/correlation ID. Included when available.
 *   Not encoded when null (to preserve existing snapshots/tests).
 *
 * ### Example JSON
 * ```json
 * {
 *   "ok": false,
 *   "error": {
 *     "code": "UNAUTHORIZED",
 *     "message": "Unauthorized",
 *     "details": null
 *   },
 *   "requestId": "abc123"
 * }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ErrorResponses(
    val ok: Boolean = false,
    val error: ErrorBody,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val requestId: String? = null,
)




