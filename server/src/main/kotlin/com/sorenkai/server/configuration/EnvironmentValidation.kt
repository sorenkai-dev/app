package com.sorenkai.server.configuration

import io.ktor.server.application.Application

/**
 * Validates required configuration at startup. During the v2 migration we keep this lightweight
 * and avoid hard coupling to infrastructure health checks.
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

    if (missing.isNotEmpty()) {
        environment.log.error("Environment validation failed: missing $missing for ENV=$env")
        throw IllegalStateException("Missing required configuration: $missing")
    }

    val mongoStatus = if (!mongoUri.isNullOrBlank()) "present" else "missing"
    environment.log.info("Environment validated: ENV=$env, MongoDB URI $mongoStatus")
}
