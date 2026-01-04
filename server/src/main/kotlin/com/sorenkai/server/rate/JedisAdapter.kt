package com.sorenkai.server.rate

import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.SetParams

/** Minimal adapter to make Redis calls testable without a live Redis. */
interface JedisAdapter {
    fun set(
        key: String,
        value: String,
        params: SetParams,
    ): String?
}

internal class JedisPooledAdapter(
    private val client: JedisPooled,
) : JedisAdapter {
    override fun set(
        key: String,
        value: String,
        params: SetParams,
    ): String? = client.set(key, value, params)
}
