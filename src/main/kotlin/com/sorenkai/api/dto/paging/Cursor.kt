package com.sorenkai.api.dto.paging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

@Serializable
data class Cursor(
    val createdAtEpochMs: Long,
    val id: String
)

fun Cursor.encode(): String {
    val json = Json.encodeToString(this)
    return Base64.UrlSafe.encode(json.toByteArray())
}

fun String.decodeCursorOrNull(): Cursor? = runCatching {
    val decoded = Base64.UrlSafe.decode(this)
    Json.decodeFromString<Cursor>(decoded.decodeToString())
}.getOrNull()
