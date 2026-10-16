package com.sorenkai.core.paging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * Cursor used for pagination.
 *
 * Instead of relying on numeric offsets (which can be inconsistent under concurrent writes),
 * we encode a cursor based on the creation timestamp + unique id of the last seen item.
 *
 * @property createdAtEpochMs Epoch timestamp in milliseconds when the entity was created.
 * @property id Unique identifier of the entity (typically a Mongo `_id` string).
 */
@Serializable
data class Cursor(
    val createdAtEpochMs: Long,
    val id: String,
)

/**
 * Encode this [Cursor] as a URL-safe Base64 string.
 *
 * Internally:
 * - Serializes to JSON with [kotlinx.serialization].
 * - Encodes the JSON string to Base64 URL-safe format.
 *
 * Example:
 * ```kotlin
 * val cursor = Cursor(System.currentTimeMillis(), "abc123")
 * val encoded = cursor.encode()
 * // => "eyJjcmVhdGVkQXRFcG9jaE1zIjo..."
 * ```
 */
fun Cursor.encode(): String {
    val json = Json.encodeToString(this)
    return Base64.UrlSafe.encode(json.toByteArray())
}

/**
 * Decode a [Cursor] from a Base64-encoded string, or return null if invalid.
 *
 * Used for parsing `?cursor=` query parameters in pagination APIs.
 *
 * Example:
 * ```kotlin
 * val decoded: Cursor? = encodedCursorString.decodeCursorOrNull()
 * if (decoded != null) {
 *   // safely resume pagination
 * }
 * ```
 */
fun String.decodeCursorOrNull(): Cursor? =
    runCatching {
        val decoded = Base64.UrlSafe.decode(this)
        Json.decodeFromString<Cursor>(decoded.decodeToString())
    }.getOrNull()
