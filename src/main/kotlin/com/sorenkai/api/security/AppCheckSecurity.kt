package com.sorenkai.api.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

/**
 * Minimal App Check enforcement utilities.
 *
 * In production, set APP_CHECK_ENFORCE=true to require an App Check token
 * (placeholder verification for now) and optionally constrain requests to
 * ALLOWED_ORIGINS. In development, enforcement can remain off.
 */
object AppCheckSecurity {
    private const val APPCHECKHEADER = "X-Firebase-AppCheck"

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

        val token = call.request.header(APPCHECKHEADER)
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

    /**
         * Parses ALLOWED_ORIGINS into a set of allowed URL prefixes used for Origin/Referer checks.
         */
        fun parseAllowedOriginsFromEnv(): Set<String> {
        val raw = System.getenv("ALLOWED_ORIGINS")?.trim().orEmpty()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
    }

    /**
         * Returns true when APP_CHECK_ENFORCE is set to true (case-insensitive).
         */
        fun isEnforced(): Boolean {
        return (System.getenv("APP_CHECK_ENFORCE") ?: "false").equals("true", ignoreCase = true)
    }
}

// Small ergonomic extension to make route checks more readable.
// Small ergonomic extension to make route checks more readable.
@Suppress("unused")
/**
 * Enforces App Check and allowed origin checks for this call.
 *
 * @param enforce when true, require an App Check token (placeholder verification)
 * @param allowedOrigins optional set of allowed Origin/Referer prefixes; empty set disables the check
 * @return true when request may proceed; false when a response has been sent and processing should stop
 */
suspend fun ApplicationCall.enforceAppCheck(
    enforce: Boolean,
    allowedOrigins: Set<String>
): Boolean = AppCheckSecurity.enforce(this, enforce, allowedOrigins)
