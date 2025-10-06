package com.sorenkai.api.services

import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.model.Discussion
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.DiscussionRepository
import com.sorenkai.api.repo.WritingRepository

/**
 * Application service handling discussion posts and comments.
 *
 * Responsibilities:
 * - Validate requests for creating/editing/deleting discussions
 * - Coordinate with repositories and audit logging
 * - Apply post rate limiting (fixed window) via injected RateLimiter implementation
 */
class DiscussionService(
    private val repo: DiscussionRepository = DiscussionRepository(),
    private val writings: WritingRepository = WritingRepository(),
    private val audit: AuditLogRepository = AuditLogRepository(),
    private val postLimiter: com.sorenkai.api.rate.RateLimiter = com.sorenkai.api.rate.RtdbPostRateLimiter()
) {
    class ValidationException(msg: String) : RuntimeException(msg)
    class NotFoundException(msg: String) : RuntimeException(msg)
    class RateLimitedException(msg: String) : RuntimeException(msg)

    data class CreateRequest(
        val kind: Discussion.Kind,
        val body: String,
        val writingSlug: String? = null,
        val parentId: String? = null,
        val hashtags: List<String> = emptyList()
    )

    private fun extractHashtags(body: String): List<String> {
        // Simple extractor: words starting with #, letters/numbers/underscore/dash
        return Regex("(?<!\\w)#([A-Za-z0-9_-]{1,50})").findAll(body)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()
    }

    suspend fun create(uid: String, req: CreateRequest, ip: String?): Discussion {
        val body = req.body.trim()
        if (body.isEmpty()) throw ValidationException("body is required")
        if (body.length > 5000) throw ValidationException("body must be <= 5000 chars")
        if (req.kind == Discussion.Kind.POST) {
            // if linked to an article, ensure it exists first
            if (!req.writingSlug.isNullOrBlank()) {
                writings.getPublishedBySlug(req.writingSlug)
                    ?: throw NotFoundException("Writing not found")
            }
            // then apply rate limiting
            val allowed = postLimiter.checkAndIncrement(uid)
            if (!allowed) throw RateLimitedException("Too many posts in a short period")
        } else {
            // COMMENT requires a parent
            if (req.parentId.isNullOrBlank()) throw ValidationException("parentId is required for comments")
        }
        val hashtags = extractHashtags(body)
        val item = Discussion(
            kind = req.kind,
            writingSlug = req.writingSlug,
            parentId = req.parentId,
            userId = uid,
            body = body,
            hashtags = hashtags
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
        }.onFailure {
        t ->
            io.ktor.util.logging.KtorSimpleLogger("Audit").warn("Failed to write audit log for discussion create: ${t.message}")
        }
        return created
    }

    suspend fun editOwn(uid: String, id: String, newBody: String, ip: String?): Boolean {
        val body = newBody.trim()
        if (body.isEmpty()) throw ValidationException("body is required")
        if (body.length > 5000) throw ValidationException("body must be <= 5000 chars")
        val hashtags = extractHashtags(body)
        val now = com.sorenkai.api.util.now()
        val ok = repo.updateOwn(id, uid, body, hashtags, now)
        if (ok) {
            runCatching {
                audit.create(
                    com.sorenkai.api.model.AuditLog(
                        entityType = com.sorenkai.api.model.AuditLog.EntityType.DISCUSSION,
                        entityId = id,
                        action = com.sorenkai.api.model.AuditLog.Action.EDIT,
                        userId = uid,
                        ip = ip,
                        changes = mapOf("body" to "updated", "hashtags" to hashtags.joinToString(","))
                    )
                )
            }.onFailure { t ->
                io.ktor.util.logging.KtorSimpleLogger("Audit").warn("Failed to write audit log for discussion edit: ${t.message}")
            }
        }
        return ok
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
    suspend fun listByWriting(writingSlug: String, pageSize: Int, cursor: String?) = repo.listByWriting(writingSlug, pageSize, cursor)
    suspend fun listByParent(parentId: String, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> = repo.listByParent(parentId, pageSize, cursor)
}
