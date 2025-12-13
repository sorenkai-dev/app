package com.sorenkai.infrastructure.rate

/**
 * Rate limiter for creating abuse reports.
 *
 * Uses ...,
 * otherwise falls back to a safe in-memory fixed-window implementation shared in
 * RtdbLimiterCore.
 *
 * Env vars:
 * - RATE_REPORT_WINDOW_SECONDS (default: 60)
 * - RATE_REPORT_LIMIT (default: 5)
 */
class RtdbReportRateLimiter {
//    private val windowSeconds: Long = (System.getenv("RATE_REPORT_WINDOW_SECONDS") ?: "60").toLong(),
//    private val limit: Int = (System.getenv("RATE_REPORT_LIMIT") ?: "5").toInt(),
// ) : RateLimiter {
//    override suspend fun checkAndIncrement(uid: String): Boolean =
//        RtdbLimiterCore.checkAndIncrement(bucket = "report:create", uid = uid, windowSeconds = windowSeconds, limit = limit)
}
