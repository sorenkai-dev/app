package com.sorenkai.server.health

import com.sorenkai.common.util.Constants.MAX_MSG_SIZE
import com.sorenkai.common.util.Constants.REDIS_TIMEOUT
import com.sorenkai.common.util.Constants.REDIS_TTL
import com.sorenkai.core.health.ComponentStatus
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.server.rate.JedisAdapter
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import redis.clients.jedis.params.SetParams

/**
 * Health check for Redis connectivity using Jedis.
 */
class RedisHealthCheck(
    private val redis: Lazy<JedisAdapter>,
) : HealthCheck {
    private val log = LoggerFactory.getLogger(RedisHealthCheck::class.java)
    override val name: String = "redis"

    override suspend fun check(): ComponentStatus =
        try {
            withTimeout(REDIS_TIMEOUT) {
                val response =
                    redis.value.set(
                        "healthcheck",
                        "ok",
                        SetParams.setParams().ex(REDIS_TTL),
                    )
                if (response == "OK") {
                    ComponentStatus(ok = true)
                } else {
                    ComponentStatus(ok = false, error = "Unexpected Redis response: $response")
                }
            }
        } catch (expected: Exception) {
            log.error("Redis health check failed", expected)
            ComponentStatus(ok = false, error = expected.message?.take(MAX_MSG_SIZE))
        }
}
