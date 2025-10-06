package com.sorenkai.api.auth

import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.slf4j.MDC

/**
 * Utilities for extracting and validating authentication/authorization
 * information from incoming requests.
 *
 * Supports:
 * - Firebase Auth token verification (via Admin SDK)
 * - Role enforcement (`USER`, `MOD`, `ADMIN`)
 * - Test bypass mode for local/CI testing (controlled via env/system props)
 *
 * Test bypass:
 * - Enabled when ENV == "test" OR TEST_AUTH_ENABLED == "true"
 * - Disabled in prod regardless
 * - UID provided by TEST_AUTH_UID
 * - Roles provided by TEST_AUTH_ROLES (comma-separated: user,mod,admin)
 *
 * Typical usage inside a route:
 * ```
 * post("/v1/writings") {
 *   val uid = call.extractUidOrUnauthorized() ?: return@post
 *   // proceed with uid
 * }
 * ```
 */

/**
 * Resolve a test UID for bypass mode.
 *
 * Conditions:
 * - Requires TEST_AUTH_UID to be set
 * - Requires either ENV=="test" OR TEST_AUTH_ENABLED=="true"
 * - Disabled if ENV=="prod"
 *
 * @return fixed UID string for tests, or null if not enabled
 */
private fun isProdEnv(): Boolean {
    val env = System.getenv("ENV") ?: System.getProperty("ENV")
    return env?.equals("prod", ignoreCase = true) == true
}

private fun testUid(): String? {
    // Only allow test bypass when explicitly enabled for tests.
    // Conditions:
    // - ENV == "test" OR TEST_AUTH_ENABLED == "true" (case-insensitive)
    // - And never in prod (ENV=="prod" blocks regardless)
    val uid = System.getProperty("TEST_AUTH_UID") ?: System.getenv("TEST_AUTH_UID")
    if (uid.isNullOrBlank()) return null

    val env = (System.getenv("ENV") ?: System.getProperty("ENV"))?.lowercase()
    val enabledFlag = (System.getProperty("TEST_AUTH_ENABLED") ?: System.getenv("TEST_AUTH_ENABLED") ?: "false").lowercase()

    val enabled = (env == "test") || (enabledFlag == "true")
    return if (!isProdEnv() && enabled) uid else null
}

/**
 * Resolve test roles for bypass mode.
 *
 * Reads TEST_AUTH_ROLES as a comma-separated list, mapping to Role enum.
 * Defaults to [Role.USER] if missing or invalid.
 *
 * @return a non-empty set of roles
 */
private fun testRoles(): Set<Role> {
    val raw = System.getProperty("TEST_AUTH_ROLES") ?: System.getenv("TEST_AUTH_ROLES")
    if (raw.isNullOrBlank()) return setOf(Role.USER)
    return raw.split(',').map { it.trim().lowercase() }.mapNotNull {
        when (it) {
            "user" -> Role.USER
            "mod", "moderator" -> Role.MOD
            "admin", "administrator" -> Role.ADMIN
            else -> null
        }
    }.toSet().ifEmpty { setOf(Role.USER) }
}

/**
 * Extract the Firebase user UID from Authorization header.
 *
 * Behavior:
 * - Requires "Authorization: Bearer <idToken>" header
 * - Verifies the token using Firebase Admin SDK
 * - On success: returns UID and populates MDC["uid"]
 * - On failure: responds with 401 Unauthorized and returns null
 *
 * Test mode:
 * - If TEST_AUTH_UID is set and enabled, returns that UID without verifying
 */
suspend fun ApplicationCall.extractUidOrUnauthorized(): String? {
    val authHeader = request.headers[HttpHeaders.Authorization] ?: return unauthorizedAndNull()

    // Test bypass: if configured, return a fixed UID without verifying
    testUid()?.let { uid ->
        MDC.put("uid", uid)
        return uid
    }

    // Split once on whitespace: "Bearer <token>"
    val parts = authHeader.trim().split(Regex("\\s+"), limit = 2)
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) {
        return unauthorizedAndNull()
    }

    val token = parts[1]
    return try {
        val decoded = FirebaseAuth.getInstance().verifyIdToken(token)
        MDC.put("uid", decoded.uid)
        decoded.uid
    } catch (_: Throwable) {
        unauthorizedAndNull()
    }
}

/**
 * Extract authenticated user and roles.
 *
 * - Returns [AuthUser] on success
 * - Returns null on any failure (no response is sent)
 * - Suitable for optional auth in GET endpoints
 *
 * Test mode:
 * - Returns test UID + roles when TEST_AUTH_UID/ROLES is configured
 */
fun ApplicationCall.extractAuthUserOrNull(): AuthUser? {
    val authHeader = request.headers[HttpHeaders.Authorization] ?: return null

    // Test bypass (requires an Authorization header to be present to mimic auth flow)
    testUid()?.let { uid ->
        MDC.put("uid", uid)
        return AuthUser(uid = uid, roles = testRoles())
    }

    val parts = authHeader.trim().split(Regex("\\s+"), limit = 2)
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
    val token = parts[1]
    return try {
        val decoded = FirebaseAuth.getInstance().verifyIdToken(token)
        MDC.put("uid", decoded.uid)
        val roles = extractRolesFromClaims(decoded.claims)
        AuthUser(uid = decoded.uid, roles = roles)
    } catch (_: Throwable) {
        null
    }
}

/** Convenience: only the UID, optionally. */
fun ApplicationCall.extractUidOptional(): String? = extractAuthUserOrNull()?.uid

/**
 * Require that the caller is authenticated and has at least one of the provided roles.
 * - On missing/invalid token -> 401.
 * - On insufficient role -> 403.
 * Returns the AuthUser on success, or null when a response has been sent.
 */
suspend fun ApplicationCall.requireRole(vararg roles: Role): AuthUser? {
    val user = extractAuthUserOrNull() ?: run {
        respond(
            HttpStatusCode.Unauthorized,
            com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = com.sorenkai.api.errors.ErrorCode.UNAUTHORIZED.name,
                    message = "Unauthorized",
                    details = null
                )
            )
        )
        return null
    }
    if (!user.hasAnyRole(*roles)) {
        respond(
            HttpStatusCode.Forbidden,
            com.sorenkai.api.dto.response.ApiErrorResponse(
                ok = false,
                error = com.sorenkai.api.dto.response.ApiErrorBody(
                    code = com.sorenkai.api.errors.ErrorCode.FORBIDDEN.name,
                    message = "Forbidden",
                    details = null
                )
            )
        )
        return null
    }
    return user
}

private suspend fun ApplicationCall.unauthorizedAndNull(): String? {
    respond(
        HttpStatusCode.Unauthorized,
        com.sorenkai.api.dto.response.ApiErrorResponse(
            ok = false,
            error = com.sorenkai.api.dto.response.ApiErrorBody(
                code = com.sorenkai.api.errors.ErrorCode.UNAUTHORIZED.name,
                message = "Unauthorized",
                details = null
            )
        )
    )
    return null
}
