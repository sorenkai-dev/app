package com.sorenkai.server.rate

import com.sorenkai.core.rate.RateLimiter
import org.slf4j.LoggerFactory
import redis.clients.jedis.params.SetParams
import kotlin.coroutines.cancellation.CancellationException

/**
 * Redis-backed cooldown limiter using SET NX EX.
 * Cooldown: 10 seconds per uid+action.
 */
class RedisRateLimiter(
    private val redis: JedisAdapter,
) : RateLimiter {
    private val log = LoggerFactory.getLogger(RedisRateLimiter::class.java)

    private val ttlSeconds: Long = 10L

    override suspend fun tryConsume(
        uid: String,
        action: String,
    ): Boolean {
        val key = "rl:$uid:$action"
        return try {
            val res = redis.set(key, "1", SetParams.setParams().nx().ex(ttlSeconds))
            if (res == null) {
                // Key exists -> hit
                log.info("Rate limit hit uid={} action={}", uid, action)
                false
            } else {
                true
            }
        } catch (expected: Exception) {
            if (expected is CancellationException) throw expected
            // Fail-open
            log.warn("Redis rate limiter failure uid={} action={} - allowing request", uid, action, expected)
            true
        }
    }
}
