package com.sorenkai.server.rate

import com.sorenkai.core.rate.RateLimiter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import redis.clients.jedis.params.SetParams

private class FakeJedisAdapter(
    var behavior: () -> String?,
) : JedisAdapter {
    override fun set(
        key: String,
        value: String,
        params: SetParams,
    ): String? = behavior()

    override fun get(key: String): String? = behavior()
}

class RedisRateLimiterTest {
    @Test
    fun allow_when_set_returns_OK() =
        runBlocking {
            val adapter = FakeJedisAdapter { "OK" }
            val limiter = RedisRateLimiter(adapter)
            val allowed = limiter.tryConsume("u1", RateLimiter.Companion.Action.DISCUSSION_POST)
            assertTrue(allowed)
        }

    @Test
    fun block_when_set_returns_null() =
        runBlocking {
            val adapter = FakeJedisAdapter { null }
            val limiter = RedisRateLimiter(adapter)
            val allowed = limiter.tryConsume("u1", RateLimiter.Companion.Action.DISCUSSION_POST)
            assertFalse(allowed)
        }

    @Test
    fun fail_open_on_exception() =
        runBlocking {
            val adapter = FakeJedisAdapter { throw RuntimeException("boom") }
            val limiter = RedisRateLimiter(adapter)
            val allowed = limiter.tryConsume("u1", RateLimiter.Companion.Action.DISCUSSION_POST)
            assertTrue(allowed)
        }
}
