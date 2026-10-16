package com.sorenkai.api.configuration

import com.sorenkai.common.util.Constants.MAX_AGE_IN_SECONDS
import com.sorenkai.common.util.Constants.MAX_UPLOAD_BYTES
import com.sorenkai.common.util.Constants.MAX_UPLOAD_MB
import com.sorenkai.api.errors.ErrorCode
import com.sorenkai.api.errors.errorHandler
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.CachingOptions
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.cors.CORSConfig
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.request.path
import kotlin.text.toLongOrNull

/**
 * Configures Cross-Origin Resource Sharing (CORS) based on the `ALLOWED_ORIGINS` environment variable.
 *
 * Parses a comma-separated list of allowed origins and registers them with Ktor's [allowHost].
 * Schemes are normalized per entry:
 * - `https://` origins are always allowed as HTTPS only.
 * - `http://` origins are allowed as both HTTP and HTTPS in non-production, or HTTPS only in
 *   production - except for localhost which is always allowed over HTTP regardless of environment.
 *
 * Behavior by environment:
 * - **Production (`ENV=prod`)** - only explicitly listed HTTPS origins are allowed. No defaults.
 * - **Non-production** - if `ALLOWED_ORIGINS` is empty, [anyHost] is permitted for local testing.
 *
 * @param rawEnv the raw value of the `ALLOWED_ORIGINS` environment variable (comma-separated origins)
 * @param isProd whether the application is running in production
 */
private fun CORSConfig.allowHostsFromEnv(rawEnv: String?, isProd: Boolean) {
    val items = rawEnv
        .orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (items.isEmpty()) {
        if (!isProd) anyHost()
        return
    }

    items.forEach { raw ->
        var hostPort = raw
        var schemes: List<String> = listOf("https")
        val isLocalhost = raw.contains("localhost", ignoreCase = true)

        when {
            raw.startsWith("https://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("https://")
                schemes = listOf("https")
            }
            raw.startsWith("http://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("http://")
                schemes = if (isProd && !isLocalhost) listOf("https") else listOf("http", "https")
            }
        }

        hostPort = hostPort.substringBefore("/")
        allowHost(hostPort, schemes = schemes)
    }
}

/**
 * Installs and configures all HTTP-level Ktor plugins for the application.
 *
 * - [ForwardedHeaders]: respects `X-Forwarded-*` headers from Cloud Run / load balancer.
 * - [DefaultHeaders]: applies security headers to all responses.
 * - Request size guard: rejects requests exceeding `MAX_UPLOAD_MB` with 413.
 * - [CORS]: configures allowed origins from the `ALLOWED_ORIGINS` environment variable.
 * - [Compression]: gzip and deflate response compression.
 * - [CachingHeaders]: no-store by default, no-store on health and metrics endpoints.
 *
 * Note: HSTS is intentionally omitted here as it is handled at the edge
 * (Cloud Run / load balancer) and should not be set at the application layer.
 */
fun Application.configureHttp() {
    val isProd = System.getenv("ENV")?.equals("prod", ignoreCase = true) == true

    install(ForwardedHeaders)

    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        header("X-API-Version", "v3")
    }

    val maxBytes = MAX_UPLOAD_MB * MAX_UPLOAD_BYTES

    /**
     * Rejects requests where the `Content-Length` header exceeds the configured
     * maximum upload size. Responds with 413 and a structured error body.
     */
    val requestSizeGuard = createApplicationPlugin("requestSizeGuard") {
        onCall { call ->
            val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (contentLength != null && contentLength > maxBytes) {
                call.errorHandler(
                    ErrorCode.TOO_LARGE,
                    "Request entity too large (>${MAX_UPLOAD_MB}MB)",
                    mapOf("maxBytes" to maxBytes.toString()),
                )
                return@onCall
            }
        }
    }
    install(requestSizeGuard)

    install(CORS) {
        allowNonSimpleContentTypes = true
        allowHeaders {
            when (it.lowercase()) {
                HttpHeaders.ContentType.lowercase(),
                HttpHeaders.Authorization.lowercase(),
                HttpHeaders.Accept.lowercase(),
                HttpHeaders.Origin.lowercase(),
                HttpHeaders.UserAgent.lowercase(),
                HttpHeaders.XForwardedFor.lowercase(),
                    -> true
                else -> false
            }
        }
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowCredentials = !isProd
        maxAgeInSeconds = MAX_AGE_IN_SECONDS

        allowHostsFromEnv(System.getenv("ALLOWED_ORIGINS"), isProd)
    }

    install(Compression) {
        gzip()
        deflate()
    }

    install(CachingHeaders) {
        options { call, _ ->
            when (call.request.path()) {
                "/serverhealth", "/metrics" -> CachingOptions(CacheControl.NoStore(null))
                else -> CachingOptions(CacheControl.NoStore(null))
            }
        }
    }
}
