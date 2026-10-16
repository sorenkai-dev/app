package com.sorenkai.api.configuration

import io.ktor.server.application.Application

/**
 * Validates required environment configuration at application startup.
 *
 * In production (`ENV=prod`), all required variables must be present or the application
 * will fail to start with a clear error message listing what is missing.
 *
 * In non-production environments, missing variables are logged as warnings rather than
 * causing a hard failure, allowing local development with partial configuration.
 *
 * Environment variables are resolved with the following priority:
 * 1. Environment variables (`System.getenv`)
 * 2. System properties (`System.getProperty`)
 *
 * Required variables by category:
 *
 * **MongoDB:**
 * - `MONGODB_URI` - connection string for the MongoDB cluster
 *
 * **PostgreSQL (Supabase):**
 * - `SUPABASE_DB_URL` - JDBC connection URL
 * - `SUPABASE_DB_USER` - database username
 * - `SUPABASE_DB_PASSWORD` - database password
 *
 * **Zitadel (Auth):**
 * - `ZITADEL_ISSUER` - JWT issuer URL
 * - `ZITADEL_JWKS_URL` - JWKS endpoint for JWT verification
 * - `ZITADEL_PAT` - personal access token for user resolution
 *
 * **Redis (Rate Limiting):**
 * - `REDIS_URL` - Redis connection URL
 *
 * **Discussion Microservice:**
 * - `DISCUSSION_SERVICE_URL` - base URL of the discussion microservice
 *
 * @throws IllegalStateException in production if any required variable is missing
 */
fun Application.validateRequiredConfigOrFail() {
    fun resolve(name: String): String? = System.getenv(name) ?: System.getProperty(name)

    val env = resolve("ENV")?.lowercase() ?: "dev"
    val isProd = env == "prod"

    val required = listOf(
        "MONGODB_URI",
        "SUPABASE_DB_URL",
        "SUPABASE_DB_USER",
        "SUPABASE_DB_PASSWORD",
        "ZITADEL_ISSUER",
        "ZITADEL_JWKS_URL",
        "ZITADEL_PAT",
        "REDIS_URL",
        "DISCUSSION_SERVICE_URL",
    )

    val missing = required.filter { resolve(it).isNullOrBlank() }

    if (missing.isNotEmpty()) {
        if (isProd) {
            environment.log.error(
                "Environment validation failed [ENV={}]: missing required variables: {}",
                env,
                missing,
            )
            throw IllegalStateException("Missing required configuration for production: $missing")
        } else {
            environment.log.warn(
                "Environment validation [ENV={}]: missing variables (non-fatal in dev/test): {}",
                env,
                missing,
            )
        }
    }

    val present = required.filter { !resolve(it).isNullOrBlank() }
    environment.log.info(
        "Environment validated [ENV={}]: {}/{} required variables present",
        env,
        present.size,
        required.size,
    )
}
