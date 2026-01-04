package com.sorenkai.server.configuration

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IReportRepository
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.infrastructure.health.MongoHealthCheck
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import com.sorenkai.infrastructure.mongo.MongoProvider
import com.sorenkai.infrastructure.repo.MongoDiscussionRepository
import com.sorenkai.infrastructure.repo.MongoLikeRepository
import com.sorenkai.infrastructure.repo.MongoReportRepository
import com.sorenkai.infrastructure.repo.MongoWritingRepository
import com.sorenkai.reports.services.ReportService
import com.sorenkai.server.health.RedisHealthCheck
import com.sorenkai.server.rate.JedisAdapter
import com.sorenkai.server.rate.JedisPooledAdapter
import com.sorenkai.server.rate.RedisRateLimiter
import com.sorenkai.writings.services.WritingService
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

                // Repositories (bind core interfaces to infrastructure implementations)
                single<IWritingRepository> { MongoWritingRepository(get()) }
                single<IDiscussionRepository> { MongoDiscussionRepository(get()) }
                single<IReportRepository> { MongoReportRepository(get()) }
                single<ILikeRepository> { MongoLikeRepository(get()) }

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

                // Services
                single { WritingService(get(), get()) }
                single { DiscussionService(get(), get(), getOrNull(), get<RateLimiter>()) }
                single { ReportService(get()) }
            },
        )
    }
}
