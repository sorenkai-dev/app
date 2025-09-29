package com.sorenkai.api.services

import com.sorenkai.api.config.Metrics
import com.sorenkai.api.dto.StoryCreateRequest
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.model.Story
import com.sorenkai.api.rate.RateLimiter
import com.sorenkai.api.rate.RtdbPostRateLimiter
import com.sorenkai.api.repo.StoryRepository

class StoryService(
    private val repo: StoryRepository = StoryRepository(),
    private val limiter: RateLimiter = RtdbPostRateLimiter(),
    private val audit: com.sorenkai.api.repo.AuditLogRepository = com.sorenkai.api.repo.AuditLogRepository()
) {
    suspend fun create(uid: String, req: StoryCreateRequest): Story {
        if (!limiter.checkAndIncrement(uid)) {
            throw RateLimitedException("Too many posts in a short period")
        }
        val story = Story(
            title = req.title.trim(),
            url = req.url.trim(),
            summary = req.summary?.trim(),
            tags = req.tags.map { it.trim() }.filter { it.isNotEmpty() },
            createdBy = uid
        )
        val created = repo.create(story)
        // increment metric
        runCatching { Metrics.storiesCreated.increment() }
        // audit log
        runCatching {
            audit.create(
                com.sorenkai.api.model.AuditLog(
                    entityType = com.sorenkai.api.model.AuditLog.EntityType.STORY,
                    entityId = created.id,
                    action = com.sorenkai.api.model.AuditLog.Action.CREATE,
                    userId = uid,
                    changes = mapOf("title" to created.title, "url" to created.url)
                )
            )
        }.onFailure { t ->
            io.ktor.util.logging.KtorSimpleLogger("Audit").warn("Failed to write audit log for story create: ${t.message}")
        }
        return created
    }

    suspend fun recent(limit: Int = 20) = repo.recent(limit)

    suspend fun recentPage(pageSize: Int, cursor: String?): Pair<List<Story>, Cursor?> =
        repo.recentPage(pageSize, cursor)

    class RateLimitedException(msg: String): RuntimeException(msg)
}
