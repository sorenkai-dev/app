@file:Suppress("TooManyFunctions")

package com.sorenkai.community.discussions.services

import com.sorenkai.common.util.Constants.MAX_BODY_SIZE
import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.dto.discussions.DiscussionResponsePublic
import com.sorenkai.core.dto.discussions.toPublicResponse
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.paging.Cursor
import com.sorenkai.core.paging.decodeCursorOrNull
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Discussions application service (v2).
 *
 * Note: Routing integration is being migrated; not all methods may be used yet.
 */
class DiscussionService(
    private val discussions: IDiscussionRepository,
    private val likes: ILikeRepository,
    private val writings: IWritingRepository? = null,
    private val postRateLimiter: RateLimiter? = null,
) {
    class ValidationException(
        message: String,
    ) : RuntimeException(message)

    class NotFoundException(
        message: String,
    ) : RuntimeException(message)

    class ForbiddenException(
        message: String,
    ) : RuntimeException(message)

    /**
     * Create a post or comment. Validates inputs and optionally checks a rate limiter.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun create(
        uid: String,
        req: DiscussionCreateRequest,
        ip: String? = null,
    ): DiscussionResponsePublic {
        validateCreate(uid, req)
        // Best-effort rate limit
        if (
            postRateLimiter != null &&
            !postRateLimiter.tryConsume(
                uid,
                RateLimiter.Companion.Action.DISCUSSION_POST,
            )
        ) {
            throw ValidationException("Rate limit exceeded")
        }

        // If linking to a writing, ensure it exists (when repository is provided)
        req.writingId
            ?.takeUnless { it.isBlank() }
            ?.let { writingId ->
                writings?.let { repo ->
                    repo.findById(writingId)
                        ?: throw NotFoundException("Writing not found: $writingId")
                }
            }

        val now = Clock.System.now()
        val toInsert =
            Discussion(
                kind = req.kind,
                writingId = req.writingId,
                parentId = req.parentId,
                userId = uid,
                body = req.body.trim(),
                lang = req.lang,
                createdAt = now,
                updatedAt = now,
            )
        return discussions.insert(toInsert).toPublicResponse()
    }

    /** List top-level posts (optionally writing-linked). Cursor is currently ignored; returns next = null. */
    suspend fun listRoot(
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
        lang: String? = null,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        cursor?.decodeCursorOrNull() // parsed but not used yet in v2
        val items =
            discussions
                .listPosts(
                    writingId = null,
                    lang = lang,
                    statuses = setOf(DiscussionsStatus.VISIBLE),
                    limit = pageSize,
                    offset = 0,
                )
        return enrichWithLikes(items, currentUid) to null
    }

    /** List posts for a specific writing. Cursor is currently ignored; returns next = null. */
    suspend fun listByWriting(
        writingId: String,
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
        lang: String? = null,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        cursor?.decodeCursorOrNull()
        val items =
            discussions
                .listPosts(
                    writingId = writingId,
                    lang = lang,
                    statuses = setOf(DiscussionsStatus.VISIBLE),
                    limit = pageSize,
                    offset = 0,
                )
        return enrichWithLikes(items, currentUid) to null
    }

    /** List comments under a parent discussion. Cursor is currently ignored; returns next = null. */
    suspend fun listByParent(
        parentId: String,
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        cursor?.decodeCursorOrNull()
        val items =
            discussions
                .listComments(
                    parentId = parentId,
                    statuses = setOf(DiscussionsStatus.VISIBLE),
                    limit = pageSize,
                    offset = 0,
                )
        return enrichWithLikes(items, currentUid) to null
    }

    private suspend fun enrichWithLikes(
        items: List<Discussion>,
        currentUid: UID?,
    ): List<DiscussionResponsePublic> {
        if (items.isEmpty()) return emptyList()

        val ids = items.map { it.id }
        val counts = likes.countLikes(ids)
        val likedByMe = currentUid?.let { likes.getLikedTargets(ids, it) } ?: emptySet()

        return items.map {
            it.toPublicResponse(
                isLikedByMe = likedByMe.contains(it.id),
                likesOverride = counts[it.id]?.toInt(),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun editOwn(
        uid: String,
        id: String,
        newBody: String,
        ip: String? = null,
    ): Boolean {
        val trimmed = newBody.trim()
        if (trimmed.length !in 1..MAX_BODY_SIZE) throw ValidationException("Body must be 1..$MAX_BODY_SIZE chars")
        val existing = discussions.findById(id) ?: return false
        if (existing.userId != uid) throw ValidationException("Not the author")
        return discussions.updateBody(id, trimmed, Clock.System.now())
    }

    /**
     * Soft-delete by setting status to DELETED_BY_AUTHOR if author matches.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun deleteOwn(
        uid: String,
        id: String,
        ip: String? = null,
    ): Boolean {
        val existing = discussions.findById(id) ?: return false
        if (existing.userId != uid) throw ValidationException("Not the author")
        return discussions.setStatus(
            id = id,
            status = DiscussionsStatus.DELETED_BY_AUTHOR,
            moderatedBy = null,
            moderatedAt = null,
            reason = "Author deleted",
        )
    }

    private fun validateCreate(
        uid: String,
        req: DiscussionCreateRequest,
    ) {
        if (uid.isBlank()) throw ValidationException("Missing uid")
        val body = req.body.trim()
        if (body.length !in 1..MAX_BODY_SIZE) throw ValidationException("Body must be 1..$MAX_BODY_SIZE chars")
        when (req.kind) {
            Kind.POST -> {
                // For posts, parentId must be null. writingSlug is optional.
                if (!req.parentId.isNullOrBlank()) throw ValidationException("POST cannot have parentId")
            }
            Kind.COMMENT -> {
                if (req.parentId.isNullOrBlank()) throw ValidationException("COMMENT requires parentId")
            }
        }
    }

    // ----------------------
    // Admin / Moderator APIs
    // ----------------------

    @OptIn(ExperimentalTime::class)
    suspend fun setStatus(
        actorUid: String,
        id: String,
        status: DiscussionsStatus,
        reason: String?,
    ): Boolean {
        if (actorUid.isBlank()) throw ValidationException("Missing moderator uid")
        discussions.findById(id) ?: return false
        val now = Clock.System.now()
        return discussions.setStatus(
            id = id,
            status = status,
            moderatedBy = actorUid,
            moderatedAt = now,
            reason = reason?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult = discussions.setPinned(id, pinned)

    suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult = discussions.setLocked(id, locked)

    // ----------------------
    // Likes (per-user, idempotent)
    // ----------------------

    suspend fun like(
        uid: String,
        id: String,
    ): DiscussionResponsePublic {
        if (uid.isBlank()) throw ValidationException("Missing uid")
        val discussion = discussions.findById(id) ?: throw NotFoundException("Discussion not found")
        likes.like(id, uid)
        return enrichWithLikes(listOf(discussion), uid).first()
    }

    suspend fun unlike(
        uid: String,
        id: String,
    ): DiscussionResponsePublic {
        if (uid.isBlank()) throw ValidationException("Missing uid")
        val discussion = discussions.findById(id) ?: throw NotFoundException("Discussion not found")
        likes.unlike(id, uid)
        return enrichWithLikes(listOf(discussion), uid).first()
    }
}
