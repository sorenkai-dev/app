package com.sorenkai.api.services

import com.sorenkai.api.model.Discussion
import com.sorenkai.api.rate.RateLimiter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DiscussionServiceRateLimitTest {
    private class DenyLimiter : RateLimiter {
        override suspend fun checkAndIncrement(uid: String): Boolean = false
    }

    @Test
    fun create_post_rate_limited_throws() = runTest {
        val service = DiscussionService(
            postLimiter = DenyLimiter()
        )
        val req = DiscussionService.CreateRequest(
            kind = Discussion.Kind.POST,
            body = "Hello world #intro",
            writingSlug = null,
            parentId = null,
            hashtags = emptyList()
        )
        try {
            service.create("u1", req, null)
            throw AssertionError("Expected RateLimitedException to be thrown")
        } catch (e: Exception) {
            if (e !is DiscussionService.RateLimitedException) throw e
        }
    }
}
