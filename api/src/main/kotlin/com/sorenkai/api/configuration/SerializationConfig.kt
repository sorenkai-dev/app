package com.sorenkai.api.configuration

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
 * - Installs [ContentNegotiation] so routes can automatically (de)serialize request/response bodies.
 * - Applies a consistent, centralized JSON configuration across the API.
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
 * - `ignoreUnknownKeys = true`: allows clients to send extra fields without breaking deserialization.
 * - `explicitNulls = false`: omits `null` values when serializing, keeping payloads compact.
 * - `encodeDefaults = true`: includes fields with default values in serialized output, ensuring
 *   clients always receive a complete and predictable response shape.
 * - `prettyPrint = false`: produces compact JSON for smaller payloads.
 * - `isLenient = false`: strict parsing; rejects malformed JSON for stronger input guarantees.
 *
 * This configuration ensures:
 * - Backward/forward compatibility with clients that may add or remove fields.
 * - Smaller payloads and cleaner responses.
 * - Consistent response shapes regardless of default values.
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
