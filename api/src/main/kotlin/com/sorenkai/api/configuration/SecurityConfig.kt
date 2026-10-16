package com.sorenkai.api.configuration

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.sorenkai.api.security.UserPrincipal
import com.sorenkai.common.util.Constants.BUCKET_REFILL_RATE
import com.sorenkai.common.util.Constants.BUCKET_SIZE
import com.sorenkai.common.util.Constants.CACHE_EXPIRATION
import com.sorenkai.common.util.Constants.CACHE_SIZE
import com.sorenkai.common.util.isProdEnv
import com.sorenkai.common.util.extractRolesFromClaims
import com.sorenkai.common.model.auth.AuthUser
import com.sorenkai.common.model.auth.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

private val securityLog = KtorSimpleLogger("Security")

/**
 * Configures JWT-based authentication using Zitadel as the identity provider.
 *
 * In test/CI environments where `ENV=test` or `TEST_AUTH_ENABLED=true` (and never in production),
 * authentication is bypassed and a synthetic [UserPrincipal] is injected using
 * `TEST_AUTH_UID` and `TEST_AUTH_ROLES` environment variables via [testUid] and [testRoles].
 *
 * In all other environments, Zitadel JWT authentication is configured using:
 * - `ZITADEL_ISSUER` - the Zitadel instance URL (required)
 * - `ZITADEL_AUDIENCE` - the expected JWT audience (required)
 * - `ZITADEL_JWKS_URL` - JWKS endpoint for public key resolution (required)
 *
 * JWK keys are cached for [CACHE_EXPIRATION] hours with a rate limit of [BUCKET_SIZE]
 * requests per [BUCKET_REFILL_RATE] minutes to prevent excessive JWKS endpoint calls.
 *
 * On successful validation, a [UserPrincipal] is created containing the authenticated
 * user's UID (from the JWT subject) and roles (extracted from JWT claims).
 *
 * On validation failure, responds with 401 Unauthorized.
 */
fun Application.configureSecurity() {
    val bypass = (
            System.getenv("ENV")?.equals("test", ignoreCase = true) == true ||
                    System.getenv("TEST_AUTH_ENABLED")?.equals("true", ignoreCase = true) == true
            ) && !isProdEnv()

    if (bypass) {
        val uid = System.getProperty("TEST_AUTH_UID")
            ?: System.getenv("TEST_AUTH_UID")
            ?: "test-user"
        val rolesRaw = System.getenv("TEST_AUTH_ROLES")?.trim().orEmpty()
        val roles: Set<Role> = rolesRaw
            .split(',')
            .mapNotNull { raw ->
                raw.trim().takeIf { it.isNotEmpty() }?.let {
                    runCatching { Role.valueOf(it.uppercase()) }.getOrNull()
                }
            }.toSet().ifEmpty { setOf(Role.USER) }

        install(Authentication) {
            provider("zitadel") {
                authenticate { context ->
                    context.principal(UserPrincipal(AuthUser(uid = uid, roles = roles)))
                }
            }
        }
        securityLog.info("Auth bypass enabled for tests; injecting AuthUser uid=$uid roles=$roles")
        return
    }

    val issuer = System.getenv("ZITADEL_ISSUER")
        ?: error("ZITADEL_ISSUER environment variable is required")
    val audience = System.getenv("ZITADEL_AUDIENCE")
        ?: error("ZITADEL_AUDIENCE environment variable is required")
    val jwksUrl = System.getenv("ZITADEL_JWKS_URL")
        ?: error("ZITADEL_JWKS_URL environment variable is required")

    val jwkProvider = JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(CACHE_SIZE, CACHE_EXPIRATION, TimeUnit.HOURS)
        .rateLimited(BUCKET_SIZE, BUCKET_REFILL_RATE, TimeUnit.MINUTES)
        .build()

    val rsaKeyProvider = object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String): RSAPublicKey =
            jwkProvider.get(keyId).publicKey as RSAPublicKey
        override fun getPrivateKey(): RSAPrivateKey? = null
        override fun getPrivateKeyId(): String? = null
    }

    install(Authentication) {
        jwt("zitadel") {
            verifier(
                JWT
                    .require(Algorithm.RSA256(rsaKeyProvider))
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build(),
            )
            validate { credential ->
                val uid = credential.subject
                if (uid.isNullOrBlank()) {
                    securityLog.debug("Auth failure: missing subject in JWT")
                    return@validate null
                }
                val roles = extractRolesFromClaims(credential.payload.claims)
                UserPrincipal(AuthUser(uid = uid, roles = roles))
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }

    securityLog.info("Zitadel JWT authentication configured with issuer=$issuer audience=$audience")
}
