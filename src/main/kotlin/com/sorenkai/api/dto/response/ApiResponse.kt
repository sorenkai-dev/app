package com.sorenkai.api.dto.response

import kotlinx.serialization.Serializable

/**
 * Standardized wrapper for successful API responses.
 *
 * All successful requests return this shape to ensure consistency across endpoints.
 * The `ok` flag is always `true` and the actual payload is contained in [data].
 *
 * @param T The type of the data payload being returned (e.g., `WritingDetail`, `Discussion`, etc.).
 * @property ok Always `true` for success responses, included for symmetry with
 *   [ApiErrorResponse] where it is always `false`.
 * @property data The actual response payload, or `null` if no payload is returned.
 *
 * ### Example JSON
 * ```json
 * {
 *   "ok": true,
 *   "data": {
 *     "id": "123",
 *     "title": "Hello World",
 *     "body": "This is my first writing"
 *   }
 * }
 * ```
 */
@Serializable
data class ApiResponse<T>(
    val ok: Boolean = true,
    val data: T? = null
)
