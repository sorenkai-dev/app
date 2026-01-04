package com.sorenkai.core.rate

/**
 * Abstraction for a fixed-window rate limiter that higher layers can depend on.
 * Implementations live in infrastructure.
 */
interface RateLimiter {
    /**
     * Attempts to consume an action token for the user.
     * @return true if allowed, false if rate-limited.
     */
    suspend fun tryConsume(
        uid: String,
        action: String,
    ): Boolean

    companion object {
        object Action {
            const val DISCUSSION_POST = "discussion_post"
            const val DISCUSSION_COMMENT = "discussion_comment"
        }
    }
}
