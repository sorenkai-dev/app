package com.sorenkai.api.rate

import redis.clients.jedis.params.SetParams

interface JedisAdapter {
    fun get(key: String): String?
    fun set(key: String, value: String, params: SetParams): String?
    fun incr(key: String): Long?
    fun expire(key: String, seconds: Long): Long?
}
