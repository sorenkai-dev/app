package com.sorenkai.server

import com.sorenkai.common.util.Constants.BASE_MILLIS
import com.sorenkai.common.util.Constants.DEFAULT_PORT
import com.sorenkai.common.util.Constants.SHUTDOWN_GRACE_DEFAULT
import com.sorenkai.common.util.Constants.SHUTDOWN_TIMEOUT_DEFAULT
import com.sorenkai.server.configuration.configureDatabase
import com.sorenkai.server.configuration.configureExceptionMapping
import com.sorenkai.server.configuration.configureFrameworks
import com.sorenkai.server.configuration.configureHttp
import com.sorenkai.server.configuration.configureMonitoring
import com.sorenkai.server.configuration.configureRouting
import com.sorenkai.server.configuration.configureSecurity
import com.sorenkai.server.configuration.configureSerialization
import com.sorenkai.server.configuration.configureValidation
import com.sorenkai.server.configuration.validateRequiredConfigOrFail
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.lang.classfile.Attributes.module

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
    // Disable Jansi native library to avoid /tmp execution or mapping issues in some environments
    // These properties are safe no-ops when Jansi is not present
    System.setProperty("org.fusesource.jansi.disable", "true")
    System.setProperty("jansi.passthrough", "true")

    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT

    val server =
        embeddedServer(Netty, port = port, host = "0.0.0.0") {
            module()
        }

    // Graceful shutdown configuration via env vars
    val graceSeconds =
        (
            System.getenv("SHUTDOWN_GRACE_SECONDS")
                ?: "5"
        ).toLongOrNull()?.coerceAtLeast(0) ?: SHUTDOWN_GRACE_DEFAULT
    val timeoutSeconds =
        (
            System.getenv("SHUTDOWN_TIMEOUT_SECONDS")
                ?: "10"
        ).toLongOrNull()?.coerceAtLeast(1) ?: SHUTDOWN_TIMEOUT_DEFAULT
    val graceMillis = graceSeconds * BASE_MILLIS
    val timeoutMillis = timeoutSeconds * BASE_MILLIS

    Runtime.getRuntime().addShutdownHook(
        Thread {
            try {
                server.stop(graceMillis, timeoutMillis)
            } catch (_: Throwable) {
                // ignore
            }
        },
    )

    server.start(wait = true)
}

fun Application.module() {
    validateRequiredConfigOrFail()
    configureFrameworks()
    configureDatabase()
    configureExceptionMapping()
    configureSerialization()
    configureValidation()
    configureHttp()
    configureSecurity()
    configureMonitoring()
    configureRouting()
}
