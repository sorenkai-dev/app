package com.sorenkai.server.configuration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

fun createDatabase(): Database? {
    val log = LoggerFactory.getLogger("DatabaseConfig")
    val url = System.getenv("SUPABASE_DB_URL") ?: System.getProperty("SUPABASE_DB_URL")
    val user = System.getenv("SUPABASE_DB_USER") ?: System.getProperty("SUPABASE_DB_USER")
    val password = System.getenv("SUPABASE_DB_PASSWORD") ?: System.getProperty("SUPABASE_DB_PASSWORD")

    if (url.isNullOrBlank()) {
        log.error("SUPABASE_DB_URL is not set")
        return null
    }

    log.info("Connecting to PostgreSQL at {} via HikariCP", url)

    val config =
        HikariConfig().apply {
            jdbcUrl = url
            driverClassName = "org.postgresql.Driver"
            username = user ?: ""
            this.password = password ?: ""
            maximumPoolSize = 10
            minimumIdle = 5
            idleTimeout = 300000
            connectionTimeout = 20000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

    val dataSource = HikariDataSource(config)
    return Database.connect(dataSource)
}

fun Application.configureDatabase() {
    // Database is now registered in Koin and lazily created.
    // To ensure it connects at startup, we can just resolve it once.
    // Use the Koin instance from the application
    val db =
        org.koin.java.KoinJavaComponent
            .getKoin()
            .getOrNull<Database>()
    if (db != null) {
        LoggerFactory.getLogger("DatabaseConfig").info("Database initialized via Koin with HikariCP")
    }
}
