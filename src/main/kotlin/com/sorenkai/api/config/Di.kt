package com.sorenkai.api.config

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
        modules(module {
            // TODO: Add bindings for repositories, services, and other components here.
        })
    }
}
