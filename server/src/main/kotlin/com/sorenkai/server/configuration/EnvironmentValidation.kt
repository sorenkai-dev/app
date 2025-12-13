package com.sorenkai.server.configuration

import com.sorenkai.api.health.MongoHealthCheck
import io.ktor.server.application.Application

/**
 * Validates required configuration and dependencies at startup.
 *
 * - In ENV=prod, fails fast if critical env vars are missing.
 * - Logs a redacted summary of effective configuration.
 * - Performs a Mongo readiness ping and fails if unreachable.
 */
fun Application.validateRequiredConfigOrFail() {
    fun envOrProp(name: String): String? = System.getProperty(name) ?: System.getenv(name)

    val env = envOrProp("ENV")?.lowercase() ?: "dev"
    val isProd = env.equals("prod", ignoreCase = true)

    val mongoUri = envOrProp("MONGODB_URI")
    val gcpProject = envOrProp("GOOGLE_CLOUD_PROJECT")

    val missing = mutableListOf<String>()
    if (isProd) {
        if (mongoUri.isNullOrBlank()) missing += "MONGODB_URI"
        if (gcpProject.isNullOrBlank()) missing += "GOOGLE_CLOUD_PROJECT"
    }

    // Log redacted summary
    when {
        missing.isNotEmpty() -> {
            environment.log.error($$"Environment validation failed: missing $missing for ENV=$env")
            throw IllegalStateException($$"Missing required configuration: $missing")
        }
        else -> {
            val mongoStatus = if (!mongoUri.isNullOrBlank()) "present" else "missing"
            val projectStatus = if (!(gcpProject.isNullOrBlank())) "present" else "missing"
            environment.log.info(
                $$"Environment validated: ENV=$env, MongoDB URI $mongoStatus",
            )
        }
    }

    // Perform Mongo readiness ping; fail if not ok in prod
    try {
        kotlinx.coroutines.runBlocking {
            val check = MongoHealthCheck().check()
            if (!check.ok && isProd) {
                val err = check.error ?: "unknown"
                throw IllegalStateException($$"Mongo readiness check failed: $err")
            }
        }
    } catch (t: Throwable) {
        if (isProd) {
            throw t
        } else {
            environment.log.warn($$"Mongo readiness check failed in non-prod: ${t.message}")
        }
    }
}
