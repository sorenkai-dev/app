package com.sorenkai.api.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

object AppCheckSecurity {
    private const val AppCheckHeader = "X-Firebase-AppCheck"

    /**
     * App Check enforcement stub:
     * - If enforce=false: allow.
     * - If enforce=true: require the header to be present (placeholder until full verification is wired).
     * Returns true if request may proceed, false if it has been rejected with a response.
     */
    suspend fun enforce(call: ApplicationCall, enforce: Boolean, allowedOrigins: Set<String>): Boolean {
        // Optional Origin/Referer allowlist (defense-in-depth); skip if list is empty
        if (allowedOrigins.isNotEmpty()) {
            val origin = call.request.header("Origin") ?: call.request.header("Referer")
            if (origin != null && allowedOrigins.none { origin.startsWith(it) }) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    com.sorenkai.api.dto.response.ApiErrorResponse(
                        ok = false,
                        error = com.sorenkai.api.dto.response.ApiErrorBody(
                            code = com.sorenkai.api.errors.ErrorCode.FORBIDDEN.name,
                            message = "Origin not allowed"
                        )
                    )
                )
                return false
            }
        }

        if (!enforce) return true

        val token = call.request.header(AppCheckHeader)
        if (token.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = com.sorenkai.api.errors.ErrorCode.UNAUTHORIZED.name,
                        message = "Missing App Check token"
                    )
                )
            )
            return false
        }

        // TODO: Replace with proper verification via Firebase Admin App Check or JWKS-based signature validation.
        // For now, accept any non-empty token when enforcement is enabled.
        return true
    }

    fun parseAllowedOriginsFromEnv(): Set<String> {
        val raw = System.getenv("ALLOWED_ORIGINS")?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }

    fun isEnforced(): Boolean {
        return (System.getenv("APP_CHECK_ENFORCE") ?: "false").equals("true", ignoreCase = true)
    }
}
