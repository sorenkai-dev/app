package com.sorenkai.api.services

import com.sorenkai.api.config.Metrics
import com.sorenkai.api.dto.CommentCreateRequest
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.model.Comment
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.repo.CommentRepository
import com.sorenkai.api.repo.StoryRepository
import com.sorenkai.api.rate.RtdbCommentRateLimiter

class CommentService(
    private val comments: CommentRepository = CommentRepository(),
    private val stories: StoryRepository = StoryRepository(),
    private val limiter: RateLimiter = RtdbCommentRateLimiter()
) {
    suspend fun create(uid: String, storyId: String, req: CommentCreateRequest): Comment {
        if (!limiter.checkAndIncrement(uid)) {
            throw RateLimitedException("Too many comments in a short period")
        }
        val story = stories.findById(storyId) ?: throw NotFoundException("Story not found")
        val body = req.body.trim()
        if (body.isEmpty()) throw ValidationException("body is required")
        val maxLen = 2000
        if (body.length > maxLen) throw ValidationException("body must be <= $maxLen chars")

        val comment = Comment(
            storyId = story.id,
            body = body,
            createdBy = uid
        )
        val created = comments.create(comment)
        runCatching { Metrics.commentsCreated.increment() }
        return created
    }

    suspend fun page(storyId: String, pageSize: Int, cursor: String?): Pair<List<Comment>, Cursor?> =
        comments.listByStoryPage(storyId, pageSize, cursor)

    class RateLimitedException(msg: String) : RuntimeException(msg)
    class NotFoundException(msg: String) : RuntimeException(msg)
    class ValidationException(msg: String) : RuntimeException(msg)
}
