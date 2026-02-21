package com.sorenkai.server.configuration

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.sorenkai.common.util.Constants.BUCKET_REFILL_RATE
import com.sorenkai.common.util.Constants.BUCKET_SIZE
import com.sorenkai.common.util.Constants.CACHE_EXPIRATION
import com.sorenkai.common.util.Constants.CACHE_SIZE
import com.sorenkai.common.util.isProdEnv
import com.sorenkai.core.auth.extractRolesFromClaims
import com.sorenkai.core.model.auth.AuthUser
import com.sorenkai.core.model.auth.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.util.logging.KtorSimpleLogger
import java.net.URL
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

private val securityLog = KtorSimpleLogger("Security")

fun Application.configureSecurity() {
    val bypass =
        (System.getenv("ENV")?.equals("test", true) == true || System.getenv("TEST_AUTH_ENABLED")?.equals("true", true) == true) &&
            !isProdEnv()

    if (bypass) {
        // Test/CI bypass: always authenticate as provided by env
        install(Authentication) {
            provider("zitadel") {
                authenticate { context ->
                    val uid = System.getenv("TEST_AUTH_UID")?.ifBlank { null } ?: "test-user"
                    val rolesRaw = System.getenv("TEST_AUTH_ROLES")?.trim().orEmpty()
                    val roles: Set<Role> =
                        rolesRaw
                            .split(',')
                            .mapNotNull { raw ->
                                raw.trim().takeIf { it.isNotEmpty() }?.let {
                                    runCatching { Role.valueOf(it.uppercase()) }.getOrNull()
                                }
                            }.toSet()
                    val principal =
                        com.sorenkai.server.security
                            .UserPrincipal(AuthUser(uid = uid, roles = roles))
                    context.principal(principal)
                }
            }
        }
        securityLog.info("Auth bypass enabled for tests; injecting AuthUser principal")
    } else {
        val issuer = System.getenv("ZITADEL_ISSUER") ?: error("ZITADEL_ISSUER env var is required")
        val audience = System.getenv("ZITADEL_AUDIENCE") ?: error("ZITADEL_AUDIENCE env var is required")
        val jwksUrl = System.getenv("ZITADEL_JWKS_URL") ?: "$issuer/.well-known/jwks.json"

        val jwkProvider =
            JwkProviderBuilder(URL(jwksUrl))
                .cached(CACHE_SIZE, CACHE_EXPIRATION, TimeUnit.HOURS)
                .rateLimited(BUCKET_SIZE, BUCKET_REFILL_RATE, TimeUnit.MINUTES)
                .build()

        val rsaKeyProvider =
            object : RSAKeyProvider {
                override fun getPublicKeyById(keyId: String): RSAPublicKey = jwkProvider.get(keyId).publicKey as RSAPublicKey

                override fun getPrivateKey(): RSAPrivateKey? = null

                override fun getPrivateKeyId(): String? = null
            }

        install(Authentication) {
            securityLog.warn("Registering Zitadel authentication provider")
            jwt("zitadel") {
                securityLog.warn("Registering Zitadel authentication provider with issuer=$issuer, audience=$audience")
                verifier(
                    JWT
                        .require(Algorithm.RSA256(rsaKeyProvider))
                        .withIssuer(issuer)
                        .withAudience(audience)
                        .build(),
                )
                securityLog.warn("Creating JWT verifier")
                validate { credential ->
                    securityLog.warn("Validating JWT credential")
                    securityLog.warn("Extracting subject from JWT credential")
                    securityLog.warn("JWT credential: $credential")
                    val uid = credential.subject
                    if (uid.isNullOrBlank()) {
                        securityLog.debug("Auth failure: missing subject")
                        return@validate null
                    }
                    val roles = extractRolesFromClaims(credential.payload.claims)
                    com.sorenkai.server.security
                        .UserPrincipal(AuthUser(uid = uid, roles = roles))
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
        securityLog.info("Zitadel authentication provider registered successfully")
    }
}
