package com.sorenkai.server.configuration

import com.sorenkai.common.util.Constants.MAX_AGE_IN_SECONDS
import com.sorenkai.common.util.Constants.MAX_UPLOAD_BYTES
import com.sorenkai.common.util.Constants.MAX_UPLOAD_MB
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
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
 * This function parses a comma-separated list of allowed origins and registers them with Ktor’s
 * [CORSConfig.allowHost] method. Schemes (`https`, `http`) are normalized for each entry:
 * - `https://` origins are always allowed as-is.
 * - `http://` origins are upgraded to HTTPS in production (`ENV=prod`) and allowed as HTTP only in
 *   development or staging environments.
 *
 * Behavior by environment:
 * - **Production (`ENV=prod`)** → Only explicitly listed HTTPS origins are allowed. No defaults.
 * - **Non-production** → If `ALLOWED_ORIGINS` is empty, `anyHost()` is permitted to simplify local testing.
 *
 * This ensures secure, restrictive CORS in production while maintaining flexibility during development.
 *
 * @param rawEnv The raw value of the `ALLOWED_ORIGINS` environment variable, containing comma-separated origins.
 */

private fun CORSConfig.allowHostsFromEnv(rawEnv: String?) {
    val items =
        rawEnv
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val isProd = System.getenv("ENV")?.equals("prod", ignoreCase = true) == true

    if (items.isEmpty()) {
        if (isProd) {
            // No origins set → default to secure lockdown
            return
        } else {
            anyHost() // dev only
            return
        }
    }

    items.forEach { raw ->
        var hostPort = raw
        var schemes: List<String> = listOf("https")

        val isLocalhost = raw.contains("localhost", ignoreCase = true) // <-- NEW CHECK

        when {
            raw.startsWith("https://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("https://")
                schemes = listOf("https")
            }
            raw.startsWith("http://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("http://")
                // EXCEPTION: If it's localhost, allow http even in prod. Otherwise, force https.
                schemes = if (isProd && !isLocalhost) listOf("https") else listOf("http", "https") // <-- MODIFIED LINE
            }
        }

        hostPort = hostPort.substringBefore("/")
        allowHost(hostPort, schemes = schemes)
    }
}

/**
 * Installs common HTTP-related Ktor plugins and policies:
 *
 * - [ForwardedHeaders]: respect proxy/load balancer headers.
 * - [DefaultHeaders]: secure headers (X-Frame-Options, HSTS, etc.).
 * - Request size guard (custom plugin).
 * - [CORS]: origins configured via ALLOWED_ORIGINS environment variable.
 * - [Compression]: gzip/deflate.
 * - [CachingHeaders]: disables caching by default, with exceptions for /legal pages.
 *
 * This centralizes all "edge HTTP" concerns (headers, CORS, compression).
 */
fun Application.configureHttp() {
    install(ForwardedHeaders) // respect X-Forwarded-* from proxy/load balancer

    // Enable HTTP Strict Transport Security (HSTS) if:
    // - ENABLE_HSTS=true explicitly, OR
    // - ENV=prod (automatic in production).
    val enableHsts =
        (System.getenv("ENABLE_HSTS") ?: "false").equals("true", ignoreCase = true) ||
            (System.getenv("ENV")?.equals("prod", ignoreCase = true) == true)
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        // Advertise current API version for client diagnostics
        header("X-API-Version", "v2")
        if (enableHsts) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        }
    }

    // Lightweight request size guard (applies to all requests with Content-Length)
    val maxUploadMb = MAX_UPLOAD_MB
    val maxBytes = maxUploadMb * MAX_UPLOAD_BYTES

    /**
     * Custom lightweight plugin to reject requests with a `Content-Length` exceeding
     * the configured max upload size.
     *
     * Configured via:
     * - `MAX_UPLOAD_MB` env var (defaults to 10).
     *
     * Responds with `413 Payload Too Large` and a structured error response.
     */
    val requestSizeGuard =
        createApplicationPlugin("requestSizeGuard") {
            onCall { call ->
                val cl = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (cl != null && cl > maxBytes) {
                    call.errorHandler(
                        ErrorCode.TOO_LARGE,
                        "Request entity too large (>$maxUploadMb MB)",
                        mapOf("maxBytes" to maxBytes.toString()),
                    )
                    return@onCall
                }
            }
        }
    install(requestSizeGuard)

    install(CORS) {
        // Allow only necessary headers
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
        // Allow only necessary methods (GET is simple and allowed by default)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // Disable credentials in production
        val isProd = System.getenv("ENV")?.equals("prod", ignoreCase = true) == true
        allowCredentials = !isProd
        maxAgeInSeconds = MAX_AGE_IN_SECONDS

        // From env: e.g. "http://localhost:5173, https://sorenkai.com, https://www.sorenkai.com"
        allowHostsFromEnv(System.getenv("ALLOWED_ORIGINS"))
    }

    install(Compression) {
        gzip()
        deflate()
        // (We can add br() later if you include the Brotli dependency)
    }

    // Default no-store; cache legal pages a bit longer
    install(CachingHeaders) {
        options { call, _ ->
            val path = call.request.path()
            when {
                path.startsWith("/legal/") ->
                    CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 6 * 60 * 60))
                path == "/serverhealth" || path == "/metrics" ->
                    CachingOptions(CacheControl.NoStore(null))
                else ->
                    CachingOptions(CacheControl.NoStore(null))
            }
        }
    }
}
