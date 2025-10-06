package com.sorenkai.api.services

import com.sorenkai.api.dto.ReportCreateRequest
import com.sorenkai.api.rate.RateLimiter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReportServiceRateLimitTest {
    private class DenyLimiter : RateLimiter {
        override suspend fun checkAndIncrement(uid: String): Boolean = false
    }

    @Test
    fun create_report_rate_limited_throws() = runTest {
        val service = ReportService(
            repo = com.sorenkai.api.repo.ReportRepository(),
            limiter = DenyLimiter()
        )
        val req = ReportCreateRequest(
            targetType = com.sorenkai.api.model.TargetType.STORY,
            targetId = "story-123",
            reason = "spam"
        )
        try {
            service.create("u1", req)
            throw AssertionError("Expected RateLimitedException to be thrown")
        } catch (e: Exception) {
            if (e !is ReportService.RateLimitedException) throw e
        }
    }
}
