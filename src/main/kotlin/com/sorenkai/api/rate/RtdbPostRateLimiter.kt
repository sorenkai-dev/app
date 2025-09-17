package com.sorenkai.api.rate

/**
 * Rate limiter for story posts. Uses Firebase RTDB when configured, otherwise falls back to in-memory.
 */
class RtdbPostRateLimiter(
    private val windowSeconds: Long = (System.getenv("RATE_POST_WINDOW_SECONDS") ?: "60").toLong(),
    private val limit: Int = (System.getenv("RATE_POST_LIMIT") ?: "1").toInt()
) : RateLimiter {
    override suspend fun checkAndIncrement(uid: String): Boolean =
        RtdbLimiterCore.checkAndIncrement(bucket = "post:stories", uid = uid, windowSeconds = windowSeconds, limit = limit)
}
