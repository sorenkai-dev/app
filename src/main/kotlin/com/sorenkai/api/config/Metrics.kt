package com.sorenkai.api.config

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global Metrics registry holder and convenience counters for the API.
 */
object Metrics {
    @Volatile
    var registry: PrometheusMeterRegistry? = null
        private set

    private val initialized = AtomicBoolean(false)

    // Custom counters
    @Volatile lateinit var storiesCreated: Counter
    @Volatile lateinit var commentsCreated: Counter
    @Volatile lateinit var reportsCreated: Counter

    fun init(reg: PrometheusMeterRegistry) {
        registry = reg
        if (initialized.compareAndSet(false, true)) {
            storiesCreated = Counter.builder("stories_created_total")
                .description("Number of stories successfully created")
                .register(reg)
            commentsCreated = Counter.builder("comments_created_total")
                .description("Number of comments successfully created")
                .register(reg)
            reportsCreated = Counter.builder("reports_created_total")
                .description("Number of reports successfully created")
                .register(reg)
        }
    }
}
