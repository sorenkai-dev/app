package com.sorenkai.api.services

import com.sorenkai.api.dto.CommentCreateRequest
import com.sorenkai.api.model.Comment
import com.sorenkai.api.model.Story
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.repo.CommentRepository
import com.sorenkai.api.repo.StoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommentServiceTest {

    private class FakeCommentsRepo : CommentRepository() {
        var last: Comment? = null
        override suspend fun create(comment: Comment): Comment {
            last = comment
            return comment
        }
    }
    private class FakeStoriesRepo(private val existing: Story?) : StoryRepository() {
        override suspend fun findById(id: String): Story? = existing
    }

    private class AllowingLimiter : RateLimiter { override suspend fun checkAndIncrement(uid: String) = true }
    private class DenyingLimiter : RateLimiter { override suspend fun checkAndIncrement(uid: String) = false }

    @Test
    fun create_success_trims_and_persists() = kotlinx.coroutines.test.runTest {
        val story = Story(title = "t", url = "http://x", createdBy = "a")
        val comments = FakeCommentsRepo()
        val stories = FakeStoriesRepo(story)
        val service = CommentService(comments, stories, AllowingLimiter())

        val out = service.create("u1", story.id, CommentCreateRequest("  hello world  "))
        assertEquals("hello world", out.body)
        assertEquals("u1", out.createdBy)
        assertEquals(story.id, out.storyId)
        assertEquals(out, comments.last)
    }

    @Test
    fun create_rate_limited() = kotlinx.coroutines.test.runTest {
        val story = Story(title = "t", url = "http://x", createdBy = "a")
        val comments = FakeCommentsRepo()
        val stories = FakeStoriesRepo(story)
        val service = CommentService(comments, stories, DenyingLimiter())
        assertFailsWith<CommentService.RateLimitedException> {
            service.create("u1", story.id, CommentCreateRequest("ok"))
        }
    }

    @Test
    fun create_not_found_story() = kotlinx.coroutines.test.runTest {
        val comments = FakeCommentsRepo()
        val stories = FakeStoriesRepo(null)
        val service = CommentService(comments, stories, AllowingLimiter())
        assertFailsWith<CommentService.NotFoundException> {
            service.create("u1", "missing", CommentCreateRequest("ok"))
        }
    }

    @Test
    fun create_validation_errors() = kotlinx.coroutines.test.runTest {
        val story = Story(title = "t", url = "http://x", createdBy = "a")
        val comments = FakeCommentsRepo()
        val stories = FakeStoriesRepo(story)
        val service = CommentService(comments, stories, AllowingLimiter())
        assertFailsWith<CommentService.ValidationException> {
            service.create("u1", story.id, CommentCreateRequest("   "))
        }
        val long = "x".repeat(2001)
        assertFailsWith<CommentService.ValidationException> {
            service.create("u1", story.id, CommentCreateRequest(long))
        }
    }
}
