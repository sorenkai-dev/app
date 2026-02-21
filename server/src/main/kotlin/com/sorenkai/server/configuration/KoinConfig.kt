package com.sorenkai.server.configuration

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IReportRepository
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.infrastructure.health.MongoHealthCheck
import com.sorenkai.infrastructure.health.PostgresHealthCheck
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import com.sorenkai.infrastructure.mongo.MongoProvider
import com.sorenkai.infrastructure.repo.MongoWritingRepository
import com.sorenkai.infrastructure.repo.PostgresDiscussionRepository
import com.sorenkai.infrastructure.repo.PostgresLikeRepository
import com.sorenkai.infrastructure.repo.PostgresReportRepository
import com.sorenkai.reports.services.ReportService
import com.sorenkai.server.health.RedisHealthCheck
import com.sorenkai.server.rate.JedisAdapter
import com.sorenkai.server.rate.JedisPooledAdapter
import com.sorenkai.server.rate.RedisRateLimiter
import com.sorenkai.server.services.UserService
import com.sorenkai.writings.services.WritingService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import redis.clients.jedis.JedisPooled

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
                single<IMongoDatabaseProvider> { MongoProvider } bind IMongoDatabaseProvider::class
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
                                },
                            )
                        }
                    }
                }

                // Repositories (bind core interfaces to infrastructure implementations)
                single<ILikeRepository> { PostgresLikeRepository() }
                single<IWritingRepository> { MongoWritingRepository(get(), get()) }
                single<IDiscussionRepository> { PostgresDiscussionRepository() }
                single<IReportRepository> { PostgresReportRepository() }

                // Rate limiters
                single {
                    val url = System.getenv("REDIS_URL") ?: System.getProperty("REDIS_URL")
                    require(!url.isNullOrBlank()) { "REDIS_URL is not set" }
                    JedisPooled(url)
                }
                single<JedisAdapter> { JedisPooledAdapter(get()) }
                single<RateLimiter> { RedisRateLimiter(get()) }

                // Health checks
                single<HealthCheck>(named("mongo")) { MongoHealthCheck(lazy { get<IMongoDatabaseProvider>() }) }
                single<HealthCheck>(named("redis")) { RedisHealthCheck(lazy { get<JedisAdapter>() }) }
                single<HealthCheck>(named("postgres")) { PostgresHealthCheck(lazy { getOrNull<Database>() }) }

                // Services
                single<com.sorenkai.core.auth.IUserService> {
                    val issuer = System.getenv("ZITADEL_ISSUER") ?: "https://sorenkai-web-app-tnvm4d.us1.zitadel.cloud"
                    val pat = System.getenv("ZITADEL_PAT") ?: ""
                    UserService(get(), get(), issuer, pat)
                }
                single { WritingService(get(), get(), get()) }
                single { DiscussionService(get(), get(), get(), getOrNull(), get<RateLimiter>()) }
                single { ReportService(get()) }
            },
        )
    }
}
