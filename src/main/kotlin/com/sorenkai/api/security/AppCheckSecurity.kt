package com.sorenkai.api.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import com.google.api.client.http.GenericUrl
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport

// TODO (v1.1): Replace google-api-client with OkHttp to reduce transitive CVE exposure.
// Currently low-risk: outbound-only HTTPS to static Google JWKS endpoint.

/**
 * Firebase App Check enforcement utilities using JWT verification against Google's JWKS endpoint.
 *
 * This object validates the `X-Firebase-AppCheck` token sent by verified client apps (e.g., your Render-hosted
 * Kobweb front-end). It ensures requests originate from legitimate front-ends registered in Firebase App Check.
 *
 * ## Behavior
 * - When `APP_CHECK_ENFORCE=true`, every request must include a valid App Check JWT.
 * - The JWT is verified against Google's JWKS (`https://firebaseappcheck.googleapis.com/v1/jwks`).
 * - JWKS responses are cached in memory for six hours.
 * - Optionally restricts accepted `Origin` / `Referer` headers using `ALLOWED_ORIGINS`.
 * - When enforcement is disabled, all requests pass through unverified (useful for local development).
 *
 * ## Environment Variables
 * | Variable | Purpose |
 * |-----------|----------|
 * | `APP_CHECK_ENFORCE` | Enables or disables App Check verification. |
 * | `ALLOWED_ORIGINS` | Comma-separated list of allowed URL prefixes for Origin/Referer validation. |
 * | `GOOGLE_CLOUD_PROJECT` / `FIREBASE_PROJECT_ID` | Used to verify JWT audience (`aud`). |
 *
 * ## Example
 * ```kotlin
 * get("/protected") {
 *     if (!AppCheckSecurity.enforce(call, enforce = AppCheckSecurity.isEnforced(),
 *         allowedOrigins = AppCheckSecurity.parseAllowedOriginsFromEnv())) return@get
 *
 *     call.respond(HttpStatusCode.OK, mapOf("ok" to true))
 * }
 * ```
 */object AppCheckSecurity {
    private const val APPCHECKHEADER = "X-Firebase-AppCheck"
    private const val JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"
    private val json = Json { ignoreUnknownKeys = true }

    // In-memory JWKS cache (6 hours)
    @Volatile private var cachedJwks: Map<String, RSAPublicKey> = emptyMap()
    private val lastFetchTimeMs = AtomicLong(0L)
    private val cacheTtlMs = Duration.ofHours(6).toMillis()

    /**
     * Enforces App Check and Origin allowlist rules.
     *
     * @param call Current [ApplicationCall].
     * @param enforce When true, require a valid App Check JWT.
     * @param allowedOrigins Optional list of allowed Origin/Referer prefixes. Empty set disables this check.
     * @return `true` if the request may proceed; `false` if a rejection response has been sent.
     *
     * **Response codes**
     * - `403 Forbidden` — Origin not allowed.
     * - `401 Unauthorized` — Missing or invalid App Check token.
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

        val valid = try {
            kotlinx.coroutines.withTimeout(3000) {
                verifyAppCheckToken(token)
            }
        } catch (e: Exception) {
            false
        }

        if (!valid) {
            call.respond(
                HttpStatusCode.Unauthorized,
                com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = com.sorenkai.api.errors.ErrorCode.UNAUTHORIZED.name,
                        message = "Invalid or expired App Check token"
                    )
                )
            )
            return false
        }

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
     **/
    fun isEnforced(): Boolean {
        return (System.getenv("APP_CHECK_ENFORCE") ?: "false").equals("true", ignoreCase = true)
    }

    // ===================== Helpers =====================

    /**
     * Fetches Google's App Check JWKS, caching keys for six hours.
     *
     * Uses `google-api-client` to make a secure HTTPS GET request and parses the result
     * via `kotlinx.serialization`.
     *
     * @return Map of key ID → [RSAPublicKey].
     */
    private fun fetchGoogleJwks(): Map<String, RSAPublicKey> {
        val now = System.currentTimeMillis()
        val last = lastFetchTimeMs.get()
        if (now - last < cacheTtlMs && cachedJwks.isNotEmpty()) {
            return cachedJwks
        }

        synchronized(this) {
            val recheckLast = lastFetchTimeMs.get()
            if (now - recheckLast < cacheTtlMs && cachedJwks.isNotEmpty()) return cachedJwks

            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val requestFactory = transport.createRequestFactory()
            val request = requestFactory.buildGetRequest(GenericUrl(JWKS_URL))
            request.connectTimeout = 5000
            request.readTimeout = 5000
            val response = request.execute()
            try {
                val body = response.parseAsString()
                val jwks = json.decodeFromString(Jwks.serializer(), body)
                val map = mutableMapOf<String, RSAPublicKey>()
                for (jwk in jwks.keys) {
                    val id = jwk.kid ?: continue
                    try {
                        map[id] = jwk.toRsaPublicKey()
                    } catch (_: Exception) {
                        // skip invalid key entries
                    }
                }
                if (map.isNotEmpty()) {
                    cachedJwks = map
                    lastFetchTimeMs.set(now)
                }
                return cachedJwks
            } finally {
                try { response.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Verifies a Firebase App Check JWT using JWKS public keys.
     *
     * @param token Raw JWT string from the `X-Firebase-AppCheck` header.
     * @return `true` if signature and audience/expiry claims are valid, otherwise `false`.
     *
     * Refreshes the JWKS cache once if the key ID (`kid`) is not found (rotation scenario).
     */
    private fun verifyAppCheckToken(token: String): Boolean {
        val projectId = System.getenv("GOOGLE_CLOUD_PROJECT")
            ?: System.getenv("FIREBASE_PROJECT_ID")
            ?: return false

        val decoded = try { JWT.decode(token) } catch (_: Exception) { return false }
        val kid = decoded.keyId ?: return false
        val alg = decoded.algorithm ?: "RS256"
        if (alg != "RS256") return false

        var jwks = fetchGoogleJwks()
        var key = jwks[kid]
        if (key == null) {
            // Refresh once if key not found (rotation scenario)
            lastFetchTimeMs.set(0L)
            jwks = fetchGoogleJwks()
            key = jwks[kid] ?: return false
        }

        val algorithm = Algorithm.RSA256(key, null)
        val verifier: JWTVerifier = JWT.require(algorithm)
            .withAudience(projectId)
            .acceptLeeway(5) // seconds of clock skew tolerance
            .build()

        return try {
            verifier.verify(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ====== JWKS models and conversion ======
    /**
     * Represents the JWKS response structure returned by
     * `https://firebaseappcheck.googleapis.com/v1/jwks`.
     */
    @Serializable
    private data class Jwks(
        val keys: List<Jwk> = emptyList()
    )

    /**
     * Represents an individual JWK entry.
     * Converts base64url-encoded modulus (`n`) and exponent (`e`) into an [RSAPublicKey].
     */
    @Serializable
    private data class Jwk(
        val kid: String? = null,
        val kty: String? = null,
        val alg: String? = null,
        val use: String? = null,
        val n: String? = null,
        val e: String? = null,
        @SerialName("x5c") val x5c: List<String>? = null
    ) {
        /**
         * Converts this JWK to an [RSAPublicKey].
         * @throws IllegalArgumentException if parameters are missing or unsupported.
         */
        fun toRsaPublicKey(): RSAPublicKey {
            if (kty != null && kty != "RSA") throw IllegalArgumentException("Unsupported kty: $kty")
            val nBytes = Base64.getUrlDecoder().decode(n ?: throw IllegalArgumentException("Missing n"))
            val eBytes = Base64.getUrlDecoder().decode(e ?: throw IllegalArgumentException("Missing e"))
            val modulus = BigInteger(1, nBytes)
            val exponent = BigInteger(1, eBytes)
            val spec = RSAPublicKeySpec(modulus, exponent)
            val kf = KeyFactory.getInstance("RSA")
            return kf.generatePublic(spec) as RSAPublicKey
        }
    }
}

// Small ergonomic extension to make route checks more readable.
@Suppress("unused")
/**
 * Convenience extension for route handlers to enforce App Check and origin checks.
 *
 * Example:
 * ```kotlin
 * get("/secure") {
 *     if (!call.enforceAppCheck(AppCheckSecurity.isEnforced(),
 *         AppCheckSecurity.parseAllowedOriginsFromEnv())) return@get
 *     call.respond(HttpStatusCode.OK, mapOf("ok" to true))
 * }
 * ```
 */
suspend fun ApplicationCall.enforceAppCheck(
    enforce: Boolean,
    allowedOrigins: Set<String>
): Boolean = AppCheckSecurity.enforce(this, enforce, allowedOrigins)
