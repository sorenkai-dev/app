package com.sorenkai.api.integration

import com.sorenkai.api.rate.RateLimiter

object AllowAllLimiter : RateLimiter {
    override suspend fun checkAndIncrement(uid: String): Boolean = true
}
