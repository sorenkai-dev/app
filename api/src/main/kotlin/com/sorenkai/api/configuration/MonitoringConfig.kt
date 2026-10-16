package com.sorenkai.api.configuration

import com.sorenkai.api.observability.Metrics
import com.sorenkai.common.util.Constants.CALL_ID_LENGTH
import com.sorenkai.api.security.UserPrincipal
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

private const val CALL_ID_LENGTH = 32

/**
 * Configures application monitoring and observability.
 *
 * Installs the following Ktor plugins and instrumentation:
 *
 * 1. **CallId**
 *    - Generates a per-request unique ID ([CALL_ID_LENGTH] chars) if `X-Request-ID` is absent.
 *    - Adds the ID to MDC for structured logging and attaches it to responses.
 *
 * 2. **CallLogging**
 *    - Logs incoming requests and responses at INFO level.
 *    - Enriches logs with MDC fields: `method`, `path`, `status`, `uid`, and `X-Request-ID`.
 *    - Filters out noise from `/serverhealth` and `/metrics` endpoints.
 *
 * 3. **MicrometerMetrics**
 *    - Sets up a [PrometheusMeterRegistry] for HTTP server metrics.
 *    - Collects request counts, latencies, and status codes automatically.
 *    - Registers JVM metrics (GC, heap, threads) for Cloud Run observability.
 *    - Initializes custom application counters and gauges via [Metrics.init].
 *
 * 4. **Metrics endpoint**
 *    - Exposes Prometheus scrape data at `GET /metrics` in plain text.
 *    - Intended for Prometheus scraping and Grafana dashboards.
 */
fun Application.configureMonitoring() {
    install(CallId) {
        header("X-Request-ID")
        generate(CALL_ID_LENGTH)
        verify { it.isNotBlank() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("X-Request-ID")
        mdc("method") { it.request.httpMethod.value }
        mdc("path") { it.request.path() }
        mdc("uid") { call -> call.principal<UserPrincipal>()?.user?.uid }
        mdc("status") { it.response.status()?.value?.toString() }
        filter { call ->
            val path = call.request.path()
            path != "/serverhealth" && path != "/metrics"
        }
    }

    val promRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = promRegistry
        meterBinders = listOf(
            io.micrometer.core.instrument.binder.jvm.JvmGcMetrics(),
            io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics(),
            io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics(),
            io.micrometer.core.instrument.binder.system.ProcessorMetrics(),
        )
    }

    Metrics.init(promRegistry)

    routing {
        get("/metrics") {
            call.respondText(promRegistry.scrape(), ContentType.Text.Plain)
        }
    }
}

