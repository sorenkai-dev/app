package com.sorenkai.api.rate

/**
 * Rate limiter for comments. Uses Firebase RTDB when configured, otherwise falls back to in-memory.
 */
class RtdbCommentRateLimiter(
    private val windowSeconds: Long = (System.getenv("RATE_COMMENT_WINDOW_SECONDS") ?: "60").toLong(),
    private val limit: Int = (System.getenv("RATE_COMMENT_LIMIT") ?: "3").toInt()
) : RateLimiter {
    override suspend fun checkAndIncrement(uid: String): Boolean =
        RtdbLimiterCore.checkAndIncrement(bucket = "post:comments", uid = uid, windowSeconds = windowSeconds, limit = limit)
}
