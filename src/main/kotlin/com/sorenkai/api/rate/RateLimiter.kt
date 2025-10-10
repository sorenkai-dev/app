package com.sorenkai.api.rate

/**
 * Abstraction for a fixed-window rate limiter.
 *
 * Implementations should atomically check whether an action is permitted for the
 * given uid within the current window and, if permitted, increment the counter
 * within the same atomic operation.
 *
 * Implementors are encouraged to provide safe fallbacks and to be side-effect free
 * when the limit would be exceeded (i.e., do not advance counters when denying).
 */
interface RateLimiter {
    /**
     * Checks if the caller identified by uid can perform the action and increments the
     * window counter if permitted.
     *
     * Returns true when allowed, false when the rate limit has been exceeded.
     */
    suspend fun checkAndIncrement(uid: String): Boolean
}
