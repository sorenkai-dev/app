package com.sorenkai.api.rate

/**
 * Rate limiter for creating discussion posts (stories).
 *
 * Sources its configuration from environment variables and delegates to the shared
 * RTDB-backed core with automatic in-memory fallback when RTDB is not configured
 * or unavailable.
 *
 * Env vars:
 * - RATE_POST_WINDOW_SECONDS (default: 60)
 * - RATE_POST_LIMIT (default: 1)
 */
class RtdbPostRateLimiter(
    private val windowSeconds: Long = (System.getenv("RATE_POST_WINDOW_SECONDS") ?: "60").toLong(),
    private val limit: Int = (System.getenv("RATE_POST_LIMIT") ?: "1").toInt()
) : RateLimiter {
    override suspend fun checkAndIncrement(uid: String): Boolean =
        RtdbLimiterCore.checkAndIncrement(bucket = "post:stories", uid = uid, windowSeconds = windowSeconds, limit = limit)
}
