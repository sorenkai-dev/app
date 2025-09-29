package com.sorenkai.api.services

import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.model.Discussion
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.DiscussionRepository
import com.sorenkai.api.repo.StoryRepository

class DiscussionService(
    private val repo: DiscussionRepository = DiscussionRepository(),
    private val stories: StoryRepository = StoryRepository(),
    private val audit: AuditLogRepository = AuditLogRepository()
) {
    class ValidationException(msg: String) : RuntimeException(msg)
    class NotFoundException(msg: String) : RuntimeException(msg)

    data class CreateRequest(
        val kind: Discussion.Kind,
        val body: String,
        val storyId: String? = null,
        val parentId: String? = null,
        val hashtags: List<String> = emptyList()
    )

    suspend fun create(uid: String, req: CreateRequest, ip: String?): Discussion {
        val body = req.body.trim()
        if (body.isEmpty()) throw ValidationException("body is required")
        if (body.length > 5000) throw ValidationException("body must be <= 5000 chars")
        if (req.kind == Discussion.Kind.POST) {
            // if linked to a story, ensure it exists
            if (!req.storyId.isNullOrBlank()) {
                stories.findById(req.storyId) ?: throw NotFoundException("Story not found")
            }
        } else {
            // COMMENT requires a parent
            if (req.parentId.isNullOrBlank()) throw ValidationException("parentId is required for comments")
        }
        val item = Discussion(
            kind = req.kind,
            storyId = req.storyId,
            parentId = req.parentId,
            userId = uid,
            body = body,
            hashtags = req.hashtags
        )
        val created = repo.create(item)
        runCatching {
            audit.create(
                com.sorenkai.api.model.AuditLog(
                    entityType = com.sorenkai.api.model.AuditLog.EntityType.DISCUSSION,
                    entityId = created.id,
                    action = com.sorenkai.api.model.AuditLog.Action.CREATE,
                    userId = uid,
                    ip = ip,
                    changes = emptyMap()
                )
            )
        }.onFailure { t ->
            io.ktor.util.logging.KtorSimpleLogger("Audit").warn("Failed to write audit log for discussion create: ${t.message}")
        }
        return created
    }

    suspend fun deleteOwn(uid: String, id: String, ip: String?): Boolean {
        val ok = repo.deleteOwn(id, uid)
        if (ok) {
            runCatching {
                audit.create(
                    com.sorenkai.api.model.AuditLog(
                        entityType = com.sorenkai.api.model.AuditLog.EntityType.DISCUSSION,
                        entityId = id,
                        action = com.sorenkai.api.model.AuditLog.Action.DELETE,
                        userId = uid,
                        ip = ip,
                        changes = emptyMap()
                    )
                )
            }.onFailure { t ->
                io.ktor.util.logging.KtorSimpleLogger("Audit").warn("Failed to write audit log for discussion delete: ${t.message}")
            }
        }
        return ok
    }

    suspend fun listRoot(pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> = repo.listRoot(pageSize, cursor)
    suspend fun listByStory(storyId: String, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> = repo.listByStory(storyId, pageSize, cursor)
    suspend fun listByParent(parentId: String, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> = repo.listByParent(parentId, pageSize, cursor)
}
