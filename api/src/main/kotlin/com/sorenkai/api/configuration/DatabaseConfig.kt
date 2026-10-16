package com.sorenkai.api.configuration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

private const val POOL_MAX_SIZE = 10
private const val POOL_MIN_IDLE = 2
private const val IDLE_TIMEOUT_MS = 300_000L
private const val CONNECTION_TIMEOUT_MS = 20_000L

/**
 * Creates and configures a [Database] instance backed by a HikariCP connection pool
 * targeting the Supabase PostgreSQL instance.
 *
 * Configuration is loaded from environment variables with fallback to system properties:
 * - `SUPABASE_DB_URL` - JDBC connection URL (required)
 * - `SUPABASE_DB_USER` - database username (required)
 * - `SUPABASE_DB_PASSWORD` - database password (required)
 *
 * Returns `null` if any required configuration is missing, logging an error for each
 * missing value. This allows the application to start in a degraded state if Postgres
 * is unavailable, rather than failing on startup.
 *
 * Connection pool settings:
 * - Max pool size: [POOL_MAX_SIZE]
 * - Min idle connections: [POOL_MIN_IDLE]
 * - Idle timeout: [IDLE_TIMEOUT_MS]ms
 * - Connection timeout: [CONNECTION_TIMEOUT_MS]ms
 * - Auto-commit: disabled (Exposed manages transactions explicitly)
 * - Transaction isolation: READ_COMMITTED (default PostgreSQL isolation level)
 *
 * This function is intended to be called once at startup and registered as a
 * Koin singleton via [com.sorenkai.api.configuration.configureFrameworks].
 *
 * @return a connected [Database] instance, or `null` if configuration is incomplete
 */
fun createDatabase(): Database? {
    val log = LoggerFactory.getLogger("DatabaseConfig")

    val url = System.getenv("SUPABASE_DB_URL") ?: System.getProperty("SUPABASE_DB_URL")
    val user = System.getenv("SUPABASE_DB_USER") ?: System.getProperty("SUPABASE_DB_USER")
    val password = System.getenv("SUPABASE_DB_PASSWORD") ?: System.getProperty("SUPABASE_DB_PASSWORD")

    var valid = true
    if (url.isNullOrBlank()) {
        log.error("SUPABASE_DB_URL is not set")
        valid = false
    }
    if (user.isNullOrBlank()) {
        log.error("SUPABASE_DB_USER is not set")
        valid = false
    }
    if (password.isNullOrBlank()) {
        log.error("SUPABASE_DB_PASSWORD is not set")
        valid = false
    }
    if (!valid) return null

    log.info("Connecting to PostgreSQL via HikariCP")

    val config = HikariConfig().apply {
        jdbcUrl = url
        driverClassName = "org.postgresql.Driver"
        username = user
        this.password = password
        maximumPoolSize = POOL_MAX_SIZE
        minimumIdle = POOL_MIN_IDLE
        idleTimeout = IDLE_TIMEOUT_MS
        connectionTimeout = CONNECTION_TIMEOUT_MS
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        validate()
    }

    val dataSource = HikariDataSource(config)
    return Database.connect(dataSource).also {
        log.info("PostgreSQL connection pool initialized via HikariCP")
    }
}
