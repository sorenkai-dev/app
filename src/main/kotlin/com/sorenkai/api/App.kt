package com.sorenkai.api

import com.sorenkai.api.config.configureHttp
import com.sorenkai.api.config.configureMonitoring
import com.sorenkai.api.config.configureSecurity
import com.sorenkai.api.config.configureSerialization
import com.sorenkai.api.config.configureValidation
import com.sorenkai.api.errors.configureExceptionMapping
import com.sorenkai.api.routes.configureRouting
import com.sorenkai.api.routes.initRepos
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * - Starts the Ktor server with the configured port and host.
 * - Applies graceful shutdown parameters from environment variables:
 *   - `SHUTDOWN_GRACE_SECONDS`: delay before shutdown (default: 5s).
 *   - `SHUTDOWN_TIMEOUT_SECONDS`: maximum shutdown duration (default: 10s).
 * - Delegates server setup to [module] for modular configuration.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
        // delegate to your modular setup (lives in src/main/kotlin/com/sorenkai/api/config + routes)
        module()
    }

    // Graceful shutdown configuration via env vars
    val graceSeconds = (System.getenv("SHUTDOWN_GRACE_SECONDS") ?: "5").toLongOrNull()?.coerceAtLeast(0) ?: 5
    val timeoutSeconds = (System.getenv("SHUTDOWN_TIMEOUT_SECONDS") ?: "10").toLongOrNull()?.coerceAtLeast(1) ?: 10
    val graceMillis = graceSeconds * 1000L
    val timeoutMillis = timeoutSeconds * 1000L

    Runtime.getRuntime().addShutdownHook(Thread {
        try {
            server.stop(graceMillis, timeoutMillis)
        } catch (_: Throwable) {
            // ignore
        }
    })

    server.start(wait = true)
}

/**
 * Configures the Ktor [Application] by installing all modules and routes.
 *
 * Responsibilities:
 * - Exception mapping
 * - JSON serialization
 * - Request validation
 * - HTTP server configuration
 * - Security (Firebase Auth, role enforcement, etc.)
 * - Metrics/monitoring
 * - API routes (reports, discussions, writings, health, docs)
 * - Repository initialization (executed asynchronously on IO dispatcher)
 */
fun Application.module() {
    configureExceptionMapping()
    configureSerialization()
    configureValidation()
    configureHttp()
    configureSecurity()
    configureMonitoring()
    configureRouting()
    CoroutineScope(Dispatchers.IO).launch { initRepos() }
}
