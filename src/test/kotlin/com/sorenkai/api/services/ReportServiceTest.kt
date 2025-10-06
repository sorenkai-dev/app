package com.sorenkai.api.services

import com.sorenkai.api.dto.ReportCreateRequest
import com.sorenkai.api.model.Report
import com.sorenkai.api.model.TargetType
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.repo.ReportRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ReportServiceTest {

    private class FakeRepo : ReportRepository() {
        var last: Report? = null
        override suspend fun create(report: Report): Report {
            last = report
            return report
        }
    }

    private class AllowingLimiter : RateLimiter { override suspend fun checkAndIncrement(uid: String) = true }
    private class DenyingLimiter : RateLimiter { override suspend fun checkAndIncrement(uid: String) = false }

    @Test
    fun create_success_trims_and_persists() = kotlinx.coroutines.test.runTest {
        val repo = FakeRepo()
        val service = ReportService(repo, AllowingLimiter())
        val out = service.create(
            "u1",
            ReportCreateRequest(targetType = TargetType.STORY, targetId = "  abc123  ", reason = "  spam  ")
        )
        assertEquals("abc123", out.targetId)
        assertEquals("spam", out.reason)
        assertEquals("u1", out.createdBy)
        assertEquals(out, repo.last)
    }

    @Test
    fun create_rate_limited() = kotlinx.coroutines.test.runTest {
        val repo = FakeRepo()
        val service = ReportService(repo, DenyingLimiter())
        try {
            service.create("u1", ReportCreateRequest(TargetType.COMMENT, "id", "r"))
            org.junit.jupiter.api.Assertions.fail("Expected RateLimitedException")
        } catch (e: ReportService.RateLimitedException) {
            // expected
        }
    }
}
