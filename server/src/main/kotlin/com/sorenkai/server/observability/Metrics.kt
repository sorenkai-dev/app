package com.sorenkai.server.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global metrics registry and convenience counters for the API.
 *
 * Responsibilities:
 * - Holds a reference to the Prometheus [io.micrometer.prometheusmetrics.PrometheusMeterRegistry] for custom metrics.
 * - Exposes counters for tracking core API events (stories, comments, reports).
 * - Provides a one-time [init] method to safely initialize metrics without duplicate registration.
 *
 * Thread-safety:
 * - Uses [java.util.concurrent.atomic.AtomicBoolean] to ensure counters are registered only once.
 * - All counters are marked [Volatile] for safe concurrent access after initialization.
 */
object Metrics {
    /** Global Prometheus registry instance, set once at [init]. */
    @Volatile
    var registry: PrometheusMeterRegistry? = null
        private set

    private val initialized = AtomicBoolean(false)

    /** Counter tracking the number of stories created successfully. */
    @Volatile lateinit var storiesCreated: Counter

    /** Counter tracking the number of comments created successfully. */
    @Volatile lateinit var commentsCreated: Counter

    /** Counter tracking the number of reports created successfully. */
    @Volatile lateinit var reportsCreated: Counter

    /**
     * Initialize the metrics registry and register all custom counters.
     *
     * Safe to call multiple times, but counters will only be created once.
     *
     * @param reg the Prometheus [PrometheusMeterRegistry] to register metrics against.
     */
    fun init(reg: PrometheusMeterRegistry) {
        registry = reg
        if (initialized.compareAndSet(false, true)) {
            storiesCreated =
                Counter
                    .builder("stories_created_total")
                    .description("Number of stories successfully created")
                    .register(reg)
            commentsCreated =
                Counter
                    .builder("comments_created_total")
                    .description("Number of comments successfully created")
                    .register(reg)
            reportsCreated =
                Counter
                    .builder("reports_created_total")
                    .description("Number of reports successfully created")
                    .register(reg)
        }
    }
}
