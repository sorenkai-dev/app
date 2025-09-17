package com.sorenkai.api.rate

/**
 * Simple rate limiter abstraction to enable testing by dependency injection.
 */
interface RateLimiter {
    suspend fun checkAndIncrement(uid: String): Boolean
}
