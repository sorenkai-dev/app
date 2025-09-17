package com.sorenkai.api.services

import com.sorenkai.api.dto.StoryCreateRequest
import com.sorenkai.api.model.Story
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.repo.StoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoryServiceTest {

    private class FakeRepo : StoryRepository() {
        var last: Story? = null
        override suspend fun create(story: Story): Story {
            last = story
            return story
        }
    }

    private class AllowingLimiter : RateLimiter {
        override suspend fun checkAndIncrement(uid: String) = true
    }
    private class DenyingLimiter : RateLimiter {
        override suspend fun checkAndIncrement(uid: String) = false
    }

    @Test
    fun create_trims_and_filters_and_succeeds() = kotlinx.coroutines.test.runTest {
        val repo = FakeRepo()
        val service = StoryService(repo, AllowingLimiter())
        val req = StoryCreateRequest(
            title = "  Hello World  ",
            url = "  https://example.com/article  ",
            summary = "  summary  ",
            tags = listOf(" kotlin ", "", " ktor", " ")
        )
        val result = service.create("uid123", req)

        // Ensure trimmed fields and filtered tags
        assertEquals("Hello World", result.title)
        assertEquals("https://example.com/article", result.url)
        assertEquals("summary", result.summary)
        assertEquals(listOf("kotlin", "ktor"), result.tags)
        assertEquals("uid123", result.createdBy)

        // Repo received the same
        val saved = repo.last!!
        assertEquals(result, saved)
    }

    @Test
    fun create_rate_limited_throws() = kotlinx.coroutines.test.runTest {
        val repo = FakeRepo()
        val service = StoryService(repo, DenyingLimiter())
        assertFailsWith<StoryService.RateLimitedException> {
            service.create("uid123", StoryCreateRequest(title = "t", url = "http://x"))
        }
    }
}
