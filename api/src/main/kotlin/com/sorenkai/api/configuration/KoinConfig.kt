package com.sorenkai.api.configuration

import com.sorenkai.common.util.RateLimiter
import com.sorenkai.api.health.HealthCheck
import com.sorenkai.likes.repository.ILikeRepository
import com.sorenkai.writings.repository.IWritingRepository
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sorenkai.api.health.MongoHealthCheck
import com.sorenkai.api.health.PostgresHealthCheck
import com.sorenkai.api.mongo.MongoProvider
import com.sorenkai.writings.repository.MongoWritingRepository
import com.sorenkai.likes.repository.PostgresLikeRepository
import com.sorenkai.writings.service.WritingService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import redis.clients.jedis.JedisPooled
import com.sorenkai.api.health.RedisHealthCheck
import com.sorenkai.api.rate.JedisAdapter
import com.sorenkai.api.rate.JedisPooledAdapter
import com.sorenkai.api.rate.RedisRateLimiter
import com.sorenkai.api.services.UserService
import com.sorenkai.writings.service.DocxToMarkdownService

/**
 * Configures application-wide frameworks and dependency injection.
 *
 * This sets up [Koin] for dependency injection and provides a placeholder module
 * that can be extended with service, repository, or utility bindings as the
 * application grows.
 *
 * Installed globally in [Application.module].
 *
 * Example:
 * ```
 * fun Application.module() {
 *     configureFrameworks()
 *     // other config...
 * }
 * ```
 */
fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        modules(
            module {
                // Infrastructure providers
                single { MongoProvider.database }
                single { createDatabase() }

                // HttpClient for external APIs (Zitadel)
                single {
                    HttpClient(CIO) {
                        install(ContentNegotiation) {
                            json(
                                Json {
                                    ignoreUnknownKeys = true
                                    prettyPrint = true
                                    isLenient = true
                                    encodeDefaults = true
                                },
                            )
                        }
                    }
                }

                // Repositories
                single<ILikeRepository> { PostgresLikeRepository() }
                single<IWritingRepository> { MongoWritingRepository(get()) }

                // Rate limiters
                single {
                    val url = System.getenv("REDIS_URL") ?: System.getProperty("REDIS_URL")
                    require(!url.isNullOrBlank()) { "REDIS_URL is not set" }
                    JedisPooled(url)
                }
                single<JedisAdapter> { JedisPooledAdapter(get()) }
                single<RateLimiter> { RedisRateLimiter(get()) }

                // Health checks
                single<HealthCheck>(named("mongo")) { MongoHealthCheck(lazy { get<MongoDatabase>() }) }
                single<HealthCheck>(named("redis")) { RedisHealthCheck(lazy { get<JedisAdapter>() }) }
                single<HealthCheck>(named("postgres")) { PostgresHealthCheck(lazy { getOrNull<Database>() }) }

                // Services
                single<com.sorenkai.common.model.user.IUserService> {
                    val issuer = System.getenv("ZITADEL_ISSUER") ?: "https://sorenkai-web-app-tnvm4d.us1.zitadel.cloud"
                    val pat = System.getenv("ZITADEL_PAT") ?: ""
                    UserService(get(), get(), issuer, pat)
                }
                single { WritingService(get(), get()) }
            },
        )
    }
}




