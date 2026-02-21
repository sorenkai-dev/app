package com.sorenkai.server.configuration

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Configures application-wide JSON serialization via Ktor's [ContentNegotiation] plugin.
 *
 * Uses Kotlinx Serialization with a shared [Json] configuration defined in [defaultJson].
 *
 * Responsibilities:
 * - Install [ContentNegotiation] so routes can automatically (de)serialize request/response bodies.
 * - Apply a consistent, centralized JSON configuration across the API.
 *
 * Typical usage in [Application.module]:
 * ```
 * fun Application.module() {
 *     configureSerialization()
 * }
 * ```
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(json = defaultJson())
    }
}

/**
 * Shared [Json] configuration used across the API.
 *
 * ### Settings:
 * - `ignoreUnknownKeys = true`: allows clients to send extra fields without breaking.
 * - `explicitNulls = false`: omits `null` values when serializing, keeps payloads compact.
 * - `prettyPrint = false`: produces compact JSON (better for APIs).
 * - `isLenient = false`: strict parsing; rejects malformed JSON for stronger guarantees.
 *
 * This configuration ensures:
 * - Backward/forward compatibility with clients that may add fields.
 * - Smaller payloads and cleaner responses.
 * - Strict input validation for predictable request handling.
 */
private fun defaultJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
        isLenient = false
    }
