package com.sorenkai.api.config

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
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
 * CallId + CallLogging + Micrometer/Prometheus + /metrics route
 */
fun Application.configureMonitoring() {

    // Request ID
    install(CallId) {
        header("X-Request-ID")
        generate(32)
        verify { it.isNotBlank() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("X-Request-ID")
        // Structured MDC fields
        mdc("method") { it.request.httpMethod.value }
        mdc("path") { it.request.path() }
        mdc("status") { it.response.status()?.value?.toString() }
        // Optionally filter out health/metrics noise:
        filter { call ->
            val p = call.request.path()
            p != "/healthz" && p != "/metrics"
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
