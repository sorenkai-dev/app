package com.sorenkai.api.rate

import com.sorenkai.common.util.RateLimiter

class RedisRateLimiter(private val redis: JedisAdapter) : RateLimiter {
    override suspend fun tryConsume(uid: String, action: String): Boolean {
        val key = "rate:limit:$action:$uid"
        val count = redis.incr(key) ?: return false
        if (count == 1L) {
            redis.expire(key, 60L) // Fixed window for now
        }
        return count <= 5 // Fixed limit for now
    }
}
