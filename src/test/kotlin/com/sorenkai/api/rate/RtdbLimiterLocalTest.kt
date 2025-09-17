package com.sorenkai.api.rate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RtdbLimiterLocalTest {
    @Test
    fun local_fallback_window_and_limit() = kotlinx.coroutines.test.runTest {
        val limiter = RtdbPostRateLimiter(windowSeconds = 60, limit = 2)
        val uid = "u-test"
        assertTrue(limiter.checkAndIncrement(uid))
        assertTrue(limiter.checkAndIncrement(uid))
        assertFalse(limiter.checkAndIncrement(uid))
    }
}
