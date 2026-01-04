package com.sorenkai.server.configuration

import com.sorenkai.common.util.Constants.CALL_ID_LENGTH
import com.sorenkai.server.observability.Metrics
import com.sorenkai.server.security.UserPrincipal
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.event.Level

/**
 * Configures application monitoring and observability.
 *
 * Installs the following Ktor plugins and instrumentation:
 *
 * 1. **CallId**
 *    - Generates a per-request unique ID (32 chars) if `X-Request-ID` is not provided.
 *    - Adds the ID to MDC for structured logging.
 *
 * 2. **CallLogging**
 *    - Logs incoming requests and responses at INFO level.
 *    - Enriches logs with MDC fields: `method`, `path`, `status`, and `X-Request-ID`.
 *    - Filters out noise from `/serverhealth` and `/metrics` endpoints.
 *
 * 3. **Micrometer Metrics**
 *    - Sets up a Prometheus registry ([PrometheusMeterRegistry]).
 *    - Collects HTTP server metrics (request counts, latencies, status codes).
 *    - Initializes custom counters via [Metrics.init]:
 *        - `stories_created_total`
 *        - `comments_created_total`
 *        - `reports_created_total`
 *
 * 4. metrics endpoint**
 *    - Exposes Prometheus scrape data at `GET /metrics` in plain text format.
 *    - Intended for Prometheus/Grafana integration.
 *
 * Example usage:
 * ```
 * fun Application.module() {
 *     configureMonitoring()
 * }
 * ```
 **/
fun Application.configureMonitoring() {
    // Request ID
    install(CallId) {
        header("X-Request-ID")
        generate(CALL_ID_LENGTH)
        verify { it.isNotBlank() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("X-Request-ID")
        // Structured MDC fields
        mdc("method") { it.request.httpMethod.value }
        mdc("path") { it.request.path() }
        // Attach authenticated uid to MDC when available
        mdc("uid") { call -> call.principal<UserPrincipal>()?.user?.uid }
        mdc("status") {
            it.response
                .status()
                ?.value
                ?.toString()
        }
        // Optionally filter out health/metrics noise:
        filter { call ->
            val p = call.request.path()
            p != "/serverhealth" && p != "/metrics"
        }
    }

    // Micrometer (Prometheus)
    val promRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = promRegistry
        // Optional: addJvmMetrics = true etc.
    }
    // Initialize global metrics holder and custom counters
    Metrics.init(promRegistry)

    // Expose /metrics
    routing {
        get("/metrics") {
            call.respondText(promRegistry.scrape(), ContentType.Text.Plain)
        }
    }
}
