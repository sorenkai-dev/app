package com.sorenkai.api.rate

/**
 * Rate limiter for creating reports. Uses Firebase RTDB when configured, otherwise falls back to in-memory.
 */
class RtdbReportRateLimiter(
    private val windowSeconds: Long = (System.getenv("RATE_REPORT_WINDOW_SECONDS") ?: "60").toLong(),
    private val limit: Int = (System.getenv("RATE_REPORT_LIMIT") ?: "5").toInt()
) : RateLimiter {
    override suspend fun checkAndIncrement(uid: String): Boolean =
        RtdbLimiterCore.checkAndIncrement(bucket = "report:create", uid = uid, windowSeconds = windowSeconds, limit = limit)
}
