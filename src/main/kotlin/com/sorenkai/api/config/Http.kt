package com.sorenkai.api.config

import io.ktor.http.CacheControl
import io.ktor.http.HttpMethod
import io.ktor.http.content.CachingOptions
import io.ktor.server.application.Application
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
import io.ktor.server.response.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

private fun CORSConfig.allowHostsFromEnv(rawEnv: String?) {
    val items = rawEnv.orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (items.isEmpty()) {
        val isProd = System.getenv("ENV")?.equals("prod", ignoreCase = true) == true
        if (isProd) {
            // In prod, do NOT allow any origin by default. Require ALLOWED_ORIGINS to be set.
            return
        } else {
            anyHost() // dev only; tighten in prod by setting ALLOWED_ORIGINS
            return
        }
    }

    items.forEach { raw ->
        var hostPort = raw
        var schemes: List<String> = listOf("https", "http")

        // Normalize input: strip scheme and path if provided
        when {
            raw.startsWith("https://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("https://")
                schemes = listOf("https")
            }
            raw.startsWith("http://", ignoreCase = true) -> {
                hostPort = raw.removePrefix("http://")
                schemes = listOf("http")
            }
        }
        // Drop any path/query accidentally included
        hostPort = hostPort.substringBefore("/") // keep host[:port] only

        // Ktor allowHost supports wildcards like "*.example.com" and explicit ports like "localhost:5173"
        allowHost(hostPort, schemes = schemes)
    }
}

fun Application.configureHttp() {

    install(ForwardedHeaders) // respect X-Forwarded-* from proxy/load balancer

    // Secure default headers
    val enableHsts = (System.getenv("ENABLE_HSTS") ?: "false").equals("true", ignoreCase = true) ||
        (System.getenv("ENV")?.equals("prod", ignoreCase = true) == true)
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        if (enableHsts) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        }
    }

    // Lightweight request size guard (applies to all requests with Content-Length)
    val maxUploadMb = (System.getenv("MAX_UPLOAD_MB") ?: "10").toLongOrNull()?.coerceAtLeast(1) ?: 10
    val maxBytes = maxUploadMb * 1024 * 1024
    val RequestSizeGuard = io.ktor.server.application.createApplicationPlugin("RequestSizeGuard") {
        onCall { call ->
            val cl = call.request.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
            if (cl != null && cl > maxBytes) {
                call.respond(
                    io.ktor.http.HttpStatusCode.PayloadTooLarge,
                    com.sorenkai.api.dto.response.ApiErrorResponse(
                        ok = false,
                        error = com.sorenkai.api.dto.response.ApiErrorBody(
                            code = com.sorenkai.api.errors.ErrorCode.TOO_LARGE.name,
                            message = "Request entity too large (>${maxUploadMb} MB)",
                            details = mapOf("maxBytes" to maxBytes.toString())
                        )
                    )
                )
                return@onCall
            }
        }
    }
    install(RequestSizeGuard)

    install(CORS) {
        allowNonSimpleContentTypes = true
        allowHeaders { true }
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowCredentials = true
        maxAgeInSeconds = 3600

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
                path == "/healthz" || path == "/metrics" ->
                    CachingOptions(CacheControl.NoStore(null))
                else ->
                    CachingOptions(CacheControl.NoStore(null))
            }
        }
    }
}
