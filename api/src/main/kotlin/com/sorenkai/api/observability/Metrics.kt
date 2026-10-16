package com.sorenkai.api.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.atomic.AtomicInteger

/**
* Holds references to all custom application metrics registered with the Prometheus registry.
*
* Metrics are initialized once at startup via [init] and exposed as top-level counters and gauges
* for use across the application. All metrics follow the Prometheus naming convention
* (snake_case, `_total` suffix for counters).
*
* ### Writings
* - [writingsCreatedTotal] - incremented each time a new writing is successfully created
* - [writingsViewedTotal] - incremented each time a writing's view count is incremented
* - [writingsSharedTotal] - incremented each time a writing's share count is incremented
*
* ### Discussions (proxied)
* - [discussionsCreatedTotal] - incremented each time a discussion is successfully proxied to the microservice
* - [discussionsDeletedTotal] - incremented each time a discussion deletion is successfully proxied
*
* ### Enrichment
* - [likesAddedTotal] - incremented each time a like is added, tagged with `target_type`
* - [likesRemovedTotal] - incremented each time a like is removed, tagged with `target_type`
*
* ### SSE
* - [sseConnectionsActive] - gauge tracking the current number of active SSE connections
* - [sseEventsPushedTotal] - incremented each time an SSE event is pushed to a client
*
* ### PubSub
* - [pubsubEventsReceivedTotal] - incremented each time a PubSub event is received
* - [pubsubEventsFailedTotal] - incremented each time a PubSub event fails to process
*/
object Metrics {
    lateinit var writingsCreatedTotal: Counter
        private set
    lateinit var writingsViewedTotal: Counter
        private set
    lateinit var writingsSharedTotal: Counter
        private set

    lateinit var discussionsCreatedTotal: Counter
        private set
    lateinit var discussionsDeletedTotal: Counter
        private set

    lateinit var likesAddedTotal: Counter
        private set
    lateinit var likesRemovedTotal: Counter
        private set

    lateinit var sseEventsPushedTotal: Counter
        private set

    lateinit var pubsubEventsReceivedTotal: Counter
        private set
    lateinit var pubsubEventsFailedTotal: Counter
        private set

    // Gauge backed by an AtomicInteger for thread-safe SSE connection tracking
    private val sseConnectionCount = AtomicInteger(0)
    val sseConnectionsActive: Int get() = sseConnectionCount.get()

    /**
     * Increments the active SSE connection gauge by 1.
     * Call when a new SSE client connects.
     */
    fun sseConnected() = sseConnectionCount.incrementAndGet()

    /**
     * Decrements the active SSE connection gauge by 1.
     * Call when an SSE client disconnects.
     */
    fun sseDisconnected() = sseConnectionCount.decrementAndGet()

    /**
     * Initializes all custom metrics and registers them with the provided [registry].
     *
     * Must be called once at application startup before any metrics are incremented.
     * Subsequent calls are safe but will re-register metrics, which Micrometer handles
     * by returning the existing meter.
     *
     * @param registry the [PrometheusMeterRegistry] to register metrics with
     */
    fun init(registry: PrometheusMeterRegistry) {
        writingsCreatedTotal = registry.counter("writings_created_total")
        writingsViewedTotal = registry.counter("writings_viewed_total")
        writingsSharedTotal = registry.counter("writings_shared_total")

        discussionsCreatedTotal = registry.counter("discussions_created_total")
        discussionsDeletedTotal = registry.counter("discussions_deleted_total")

        likesAddedTotal = registry.counter("likes_added_total")
        likesRemovedTotal = registry.counter("likes_removed_total")

        sseEventsPushedTotal = registry.counter("sse_events_pushed_total")

        pubsubEventsReceivedTotal = registry.counter("pubsub_events_received_total")
        pubsubEventsFailedTotal = registry.counter("pubsub_events_failed_total")

        // Register SSE connections as a gauge
        registry.gauge("sse_connections_active", sseConnectionCount)
    }
}
