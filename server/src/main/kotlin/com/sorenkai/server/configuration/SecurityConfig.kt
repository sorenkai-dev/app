package com.sorenkai.server.configuration

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.util.logging.KtorSimpleLogger
import java.net.URL
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.concurrent.TimeUnit

private val securityLog = KtorSimpleLogger("Security")

fun Application.configureSecurity() {
    val issuer =
        System.getenv("ZITADEL_ISSUER")
            ?: error("ZITADEL_ISSUER env var is required")

    val audience =
        System.getenv("ZITADEL_AUDIENCE")
            ?: error("ZITADEL_AUDIENCE env var is required")

    val jwksUrl =
        System.getenv("ZITADEL_JWKS_URL")
            ?: "$issuer/.well-known/jwks.json"

    securityLog.info(
        "Initializing Zitadel authentication (issuer=$issuer, audience=$audience)",
    )

    val jwkProvider =
        JwkProviderBuilder(URL(jwksUrl))
            .cached(10, Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val rsaKeyProvider: RSAKeyProvider =
        object : RSAKeyProvider {
            override fun getPublicKeyById(keyId: String): RSAPublicKey = jwkProvider.get(keyId).publicKey as RSAPublicKey

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
                // Authentication only: principal exists if token is valid
                JWTPrincipal(credential.payload)
            }
        }
    }

    securityLog.info("Zitadel authentication provider registered successfully")
}
