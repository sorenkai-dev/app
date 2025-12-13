package com.sorenkai.server.configuration

import com.sorenkai.core.repo.AuditLogRepository
import com.sorenkai.core.repo.DiscussionRepository
import com.sorenkai.core.repo.ReportRepository
import com.sorenkai.core.repo.WritingRepository
import com.sorenkai.core.services.AdminService
import com.sorenkai.core.services.WritingService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

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
                // Repositories
                single { WritingRepository() }
                single { DiscussionRepository() }
                single { ReportRepository() }
                single { AuditLogRepository() }

                // Rate limiters
//                single<RateLimiter> { RtdbPostRateLimiter() }
//                single<RateLimiter>(
//                    qualifier =
//                        org.koin.core.qualifier
//                            .named("report"),
//                ) { RtdbReportRateLimiter() }

                // Services
                single { WritingService(get()) }
                single { AdminService(get()) }
            },
        )
    }
}
