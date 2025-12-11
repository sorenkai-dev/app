package com.sorenkai.api

import com.sorenkai.api.config.Mongo
import com.sorenkai.api.config.configureHttp
import com.sorenkai.api.config.configureMonitoring
import com.sorenkai.api.config.configureSecurity
import com.sorenkai.api.config.configureSerialization
import com.sorenkai.api.config.configureValidation
import com.sorenkai.api.errors.configureExceptionMapping
import com.sorenkai.api.health.MongoHealthCheck
import com.sorenkai.api.routes.configureRouting
import com.sorenkai.api.routes.initRepos
import io.ktor.server.application.Application
import io.ktor.server.application.log
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
    // Disable Jansi native library to avoid /tmp execution or mapping issues in some environments
    // These properties are safe no-ops when Jansi is not present
    System.setProperty("org.fusesource.jansi.disable", "true")
    System.setProperty("jansi.passthrough", "true")

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
//    validateRequiredConfigOrFail()
    configureExceptionMapping()
    configureSerialization()
    configureValidation()
    configureHttp()
    configureSecurity()
    configureMonitoring()
    configureRouting()

    val dbName = Mongo.db.name
    log.info("------- \n\n Starting database $dbName \n\n -------")
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { initRepos() }.onFailure {
            environment.log.warn("initRepos failed: ${'$'}{it.message}")
        }
    }
}

/**
 * Validates required configuration and dependencies at startup.
 *
 * - In ENV=prod, fails fast if critical env vars are missing.
 * - Logs a redacted summary of effective configuration.
 * - Performs a Mongo readiness ping and fails if unreachable.
 */
private fun Application.validateRequiredConfigOrFail() {
    fun envOrProp(name: String): String? = System.getProperty(name) ?: System.getenv(name)

    val env = envOrProp("ENV")?.lowercase() ?: "dev"
    val isProd = env.equals("prod", ignoreCase = true)

    val mongoUri = envOrProp("MONGODB_URI")
    val gcpProject = envOrProp("GOOGLE_CLOUD_PROJECT")
    val fbProject = envOrProp("FIREBASE_PROJECT_ID")
    val appCheckEnforce = envOrProp("APP_CHECK_ENFORCE")
    val allowedOrigins = envOrProp("ALLOWED_ORIGINS")

    val missing = mutableListOf<String>()
    if (isProd) {
        if (mongoUri.isNullOrBlank()) missing += "MONGODB_URI"
        if ((gcpProject.isNullOrBlank()) && (fbProject.isNullOrBlank())) missing += "GOOGLE_CLOUD_PROJECT|FIREBASE_PROJECT_ID"
//        if (appCheckEnforce.isNullOrBlank()) missing += "APP_CHECK_ENFORCE"
//        if (allowedOrigins.isNullOrBlank()) missing += "ALLOWED_ORIGINS"
    }

    // Log redacted summary
    val appCheckEnabled = appCheckEnforce?.equals("true", ignoreCase = true) == true
    when {
        missing.isNotEmpty() -> {
            environment.log.error("Environment validation failed: missing ${'$'}missing for ENV=${'$'}env")
            throw IllegalStateException("Missing required configuration: ${'$'}missing")
        }
        else -> {
            val mongoStatus = if (!mongoUri.isNullOrBlank()) "present" else "missing"
            val projectStatus = if (!(gcpProject ?: fbProject).isNullOrBlank()) "present" else "missing"
            environment.log.info("Environment validated: ENV=${'$'}env, MongoDB URI ${'$'}mongoStatus, Firebase project ID ${'$'}projectStatus")
            if (isProd && !appCheckEnabled) {
                environment.log.warn("APP_CHECK_ENFORCE=false (enable in prod)")
            }
        }
    }

    // Perform Mongo readiness ping; fail if not ok in prod
    try {
        kotlinx.coroutines.runBlocking {
            val check = MongoHealthCheck().check()
            if (!check.ok && isProd) {
                val err = check.error ?: "unknown"
                throw IllegalStateException("Mongo readiness check failed: ${'$'}err")
            }
        }
    } catch (t: Throwable) {
        if (isProd) throw t
        else environment.log.warn("Mongo readiness check failed in non-prod: ${'$'}{t.message}")
    }
}
