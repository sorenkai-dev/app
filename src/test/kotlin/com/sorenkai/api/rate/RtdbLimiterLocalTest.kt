package com.sorenkai.api.rate

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

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
