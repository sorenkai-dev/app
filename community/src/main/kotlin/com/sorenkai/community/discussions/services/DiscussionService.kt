@file:Suppress("TooManyFunctions")

package com.sorenkai.community.discussions.services

import com.sorenkai.common.util.Constants.MAX_BODY_SIZE
import com.sorenkai.core.auth.IUserService
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
import com.sorenkai.core.repo.DiscussionOrder
import com.sorenkai.core.repo.DiscussionWithLikes
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Discussions application service (v2).
 *
 * Note: Routing integration is being migrated; not all methods may be used yet.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiscussionService(
    private val discussions: IDiscussionRepository,
    private val likes: ILikeRepository,
    private val users: IUserService? = null,
    private val writings: IWritingRepository? = null,
    private val postRateLimiter: RateLimiter? = null,
) {
    private val log = LoggerFactory.getLogger(DiscussionService::class.java)
    private val objectIdRegex = Regex("^[0-9a-fA-F]{24}$")
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private fun uid(value: String): UID =
        value.takeIf { it.isNotBlank() }
            ?: throw ValidationException("Missing uid")

    class ValidationException(
        message: String,
    ) : RuntimeException(message)

    class NotFoundException(
        message: String,
    ) : RuntimeException(message)

    class ForbiddenException(
        message: String,
    ) : RuntimeException(message)

    private fun parseUuid(
        value: String,
        field: String,
    ): Uuid =
        runCatching { Uuid.parse(value) }
            .getOrElse { throw ValidationException("Invalid $field format") }

    private fun parseOptionalUuid(value: String?): Uuid? =
        value
            ?.trim()
            ?.takeUnless { it.isEmpty() || it == "null" || it == "undefined" }
            ?.let { parseUuid(it, "id") }

    /**
     * Create a post or comment. Validates inputs and optionally checks a rate limiter.
     */

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

        // Sanitize optional writingId (MongoDB string)
        val writingId =
            req.writingId
                ?.trim()
                ?.takeUnless { it.isEmpty() || it == "null" || it == "undefined" }
                ?.also { id ->
                    writings?.findById(id)
                        ?: throw NotFoundException("Writing not found: $id")
                }

        val now = Clock.System.now()

        val toInsert =
            Discussion(
                id = Uuid.random(),
                kind = req.kind,
                writingId = writingId,
                parentId = req.parentId,
                userId = uid,
                body = req.body.trim(),
                lang = req.lang,
                status = req.status ?: DiscussionsStatus.VISIBLE,
                createdAt = req.createdAt ?: now,
            )

        val inserted = discussions.insert(toInsert)
        val author = users?.getUser(uid)

        return inserted.toPublicResponse(author = author)
    }

    /** List top-level posts (optionally writing-linked). Cursor is currently ignored; returns next = null. */
    suspend fun listRoot(
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
        isModerator: Boolean = false,
        lang: String? = null,
        order: DiscussionOrder = DiscussionOrder.NEWEST,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        val t0 = System.currentTimeMillis() // timer start

        cursor?.decodeCursorOrNull() // parsed but not used yet in v2
        val statuses = mutableSetOf(DiscussionsStatus.VISIBLE, DiscussionsStatus.DELETED_BY_AUTHOR)
        if (isModerator) {
            statuses.add(DiscussionsStatus.DELETED_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_REPORTS)
        }
        val items =
            discussions
                .listPosts(
                    writingId = null,
                    lang = lang,
                    statuses = statuses,
                    limit = pageSize,
                    offset = 0,
                    currentUserId = currentUid,
                    isModerator = isModerator,
                    order = order,
                )

        log.info(
            "[TIMING] listPosts() took ${System.currentTimeMillis() - t0}ms (count=${items.size})",
        ) // end timer

        return enrich(items) to null
    }

    /** List posts for a specific writing. Cursor is currently ignored; returns next = null. */
    suspend fun listByWriting(
        writingId: String,
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
        isModerator: Boolean = false,
        lang: String? = null,
        order: DiscussionOrder = DiscussionOrder.NEWEST,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        if (!objectIdRegex.matches(writingId)) throw ValidationException("Invalid writingId format")
        cursor?.decodeCursorOrNull()
        val statuses = mutableSetOf(DiscussionsStatus.VISIBLE, DiscussionsStatus.DELETED_BY_AUTHOR)
        if (isModerator) {
            statuses.add(DiscussionsStatus.DELETED_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_REPORTS)
        }
        val items =
            discussions
                .listPosts(
                    writingId = writingId,
                    lang = lang,
                    statuses = statuses,
                    limit = pageSize,
                    offset = 0,
                    currentUserId = currentUid,
                    isModerator = isModerator,
                    order = order,
                )
        return enrich(items) to null
    }

    /** List comments under a parent discussion. Cursor is currently ignored; returns next = null. */
    suspend fun listByParent(
        parentId: String,
        pageSize: Int,
        cursor: String?,
        currentUid: UID? = null,
        isModerator: Boolean = false,
    ): Pair<List<DiscussionResponsePublic>, Cursor?> {
        val pid = parseUuid(parentId, "parentId")
        cursor?.decodeCursorOrNull()
        val statuses = mutableSetOf(DiscussionsStatus.VISIBLE, DiscussionsStatus.DELETED_BY_AUTHOR)
        if (isModerator) {
            statuses.add(DiscussionsStatus.DELETED_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_MODERATOR)
            statuses.add(DiscussionsStatus.HIDDEN_BY_REPORTS)
        }
        val items =
            discussions
                .listComments(
                    parentId = pid,
                    statuses = statuses,
                    limit = pageSize,
                    offset = 0,
                    currentUserId = currentUid,
                    isModerator = isModerator,
                )
        return enrich(items) to null
    }

    private suspend fun enrich(items: List<DiscussionWithLikes>): List<DiscussionResponsePublic> {
        val t1 = System.currentTimeMillis() // timer start

        if (items.isEmpty()) return emptyList()

        // Resolve users
        val userIds = items.map { it.discussion.userId }.distinct()
        val userMap =
            userIds.associateWith { uid ->
                users?.getUser(uid)
            }

        // Fetch child counts
        val discussionIds = items.map { it.discussion.id }.toSet()
        val childCounts = discussions.countChildren(discussionIds)

        val t2 = System.currentTimeMillis()
        val itemsMap =
            items.map {
                val d = it.discussion
                val effectiveBody =
                    if (d.status == DiscussionsStatus.DELETED_BY_AUTHOR) {
                        "This post has been deleted by the author."
                    } else if (d.status == DiscussionsStatus.DELETED_BY_MODERATOR) {
                        "This post has been deleted by a moderator."
                    } else {
                        d.body
                    }

                d.copy(body = effectiveBody).toPublicResponse(
                    isLikedByMe = it.isLikedByMe,
                    isReportedByMe = it.isReportedByMe,
                    likesOverride = it.likesCount.toInt(),
                    author = userMap[d.userId],
                    childCount = childCounts[d.id] ?: 0,
                )
            }
        log.info(
            "[TIMING] mapping took ${System.currentTimeMillis() - t2}ms",
        )

        log.info(
            "[TIMING] enrich() took ${System.currentTimeMillis() - t1}ms",
        )

        return itemsMap
    }

    @OptIn(ExperimentalTime::class)
    suspend fun editOwn(
        uid: String,
        id: String,
        newBody: String,
        ip: String? = null,
    ): DiscussionResponsePublic? {
        val uuid = parseUuid(id, "id")
        val trimmed = newBody.trim()
        if (trimmed.length !in 1..MAX_BODY_SIZE) throw ValidationException("Body must be 1..$MAX_BODY_SIZE chars")
        val existing = discussions.findById(uuid) ?: return null
        if (existing.userId != uid) throw ValidationException("Not the author")
        val updated = discussions.updateBody(uuid, trimmed, Clock.System.now(), uid) ?: return null
        return enrich(listOf(updated)).firstOrNull()
    }

    /**
     * Soft-delete by setting status to DELETED_BY_AUTHOR if author matches.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun deleteOwn(
        uid: String,
        id: String,
        ip: String? = null,
    ): DiscussionResponsePublic? {
        val uuid = parseUuid(id, "id")
        val existing = discussions.findById(uuid) ?: return null
        if (existing.userId != uid) throw ValidationException("Not the author")
        val now = Clock.System.now()
        val updated =
            discussions.setStatus(
                id = uuid,
                status = DiscussionsStatus.DELETED_BY_AUTHOR,
                moderatedBy = null,
                moderatedAt = null,
                reason = "Author deleted",
                deletedAt = now,
                purgeAt = now.plus(24.toDuration(DurationUnit.HOURS)),
                currentUserId = uid,
            ) ?: return null
        return enrich(listOf(updated)).firstOrNull()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun restoreOwn(
        uid: String,
        id: String,
    ): DiscussionResponsePublic? {
        val uuid = parseUuid(id, "id")
        val existing = discussions.findById(uuid) ?: return null

        if (existing.userId != uid) throw ValidationException("Not the author")
        if (existing.status != DiscussionsStatus.DELETED_BY_AUTHOR) throw ValidationException("Post was not deleted by author")

        val now = Clock.System.now()
        val purgeAt = existing.purgeAt
        if (purgeAt == null || now >= purgeAt) throw ValidationException("Grace period has expired")

        val restored = discussions.restore(uuid, uid) ?: return null
        return enrich(listOf(restored)).firstOrNull()
    }

    private fun validateCreate(
        uid: String,
        req: DiscussionCreateRequest,
    ) {
        if (uid.isBlank()) throw ValidationException("Missing uid")
        val body = req.body.trim()
        if (body.length !in 1..MAX_BODY_SIZE) throw ValidationException("Body must be 1..$MAX_BODY_SIZE chars")

        req.writingId?.trim()?.takeUnless { it.isEmpty() || it == "null" || it == "undefined" }?.let {
            if (!objectIdRegex.matches(it)) throw ValidationException("Invalid writingId format")
        }

        when (req.kind) {
            Kind.POST -> {
                // For posts, parentId must be null. writingId is optional.
                if (req.parentId != null) throw ValidationException("POST cannot have parentId")
            }
            Kind.COMMENT -> {
                if (req.parentId == null) throw ValidationException("COMMENT requires parentId")
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
    ): DiscussionResponsePublic? {
        val uuid = parseUuid(id, "id")
        if (actorUid.isBlank()) throw ValidationException("Missing moderator uid")
        discussions.findById(uuid) ?: return null
        val now = Clock.System.now()

        val (deletedAt, purgeAt) =
            when (status) {
                DiscussionsStatus.DELETED_BY_MODERATOR -> now to null
                DiscussionsStatus.DELETED_BY_AUTHOR -> now to now.plus(24.hours)
                else -> null to null
            }

        val updated =
            discussions.setStatus(
                id = uuid,
                status = status,
                moderatedBy = actorUid,
                moderatedAt = now,
                reason = reason?.takeIf { it.isNotBlank() },
                deletedAt = deletedAt,
                purgeAt = purgeAt,
                currentUserId = actorUid,
            ) ?: return null
        return enrich(listOf(updated)).firstOrNull()
    }

    suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult = discussions.setPinned(parseUuid(id, "id"), pinned)

    suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult = discussions.setLocked(parseUuid(id, "id"), locked)

    // ----------------------
    // Likes (per-user, idempotent)
    // ----------------------

    suspend fun like(
        uid: String,
        id: String,
    ): DiscussionResponsePublic {
        val uuid = parseUuid(id, "id")
        if (uid.isBlank()) throw ValidationException("Missing uid")
        discussions.findById(uuid) ?: throw NotFoundException("Discussion not found")
        likes.likeDiscussion(uuid, uid)
        val enriched = discussions.findByIdWithLikes(uuid, uid) ?: throw NotFoundException("Discussion not found after like")
        return enrich(listOf(enriched)).first()
    }

    suspend fun unlike(
        uid: String,
        id: String,
    ): DiscussionResponsePublic {
        val uuid = parseUuid(id, "id")
        if (uid.isBlank()) throw ValidationException("Missing uid")
        discussions.findById(uuid) ?: throw NotFoundException("Discussion not found")
        likes.unlikeDiscussion(uuid, uid)
        val enriched = discussions.findByIdWithLikes(uuid, uid) ?: throw NotFoundException("Discussion not found after unlike")
        return enrich(listOf(enriched)).first()
    }
}
