package com.sorenkai.api.rate

import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.SetParams

class JedisPooledAdapter(private val jedis: JedisPooled) : JedisAdapter {
    override fun get(key: String): String? = jedis.get(key)
    override fun set(key: String, value: String, params: SetParams): String? = jedis.set(key, value, params)
    override fun incr(key: String): Long? = jedis.incr(key)
    override fun expire(key: String, seconds: Long): Long? = jedis.expire(key, seconds)
}
