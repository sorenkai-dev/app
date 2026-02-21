package com.sorenkai.community.discussions.services

import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.dto.discussions.DiscussionResponsePublic
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.repo.DiscussionOrder
import com.sorenkai.core.repo.DiscussionWithLikes
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private class FakeLikeRepository : ILikeRepository {
    val store = ConcurrentHashMap<Uuid, MutableSet<String>>()
    val writingStore = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun likeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) {
        store.computeIfAbsent(discussionId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlikeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) {
        store[discussionId]?.remove(userId)
    }

    override suspend fun likeWriting(
        writingId: String,
        userId: UID,
    ) {
        writingStore.computeIfAbsent(writingId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlikeWriting(
        writingId: String,
        userId: UID,
    ) {
        writingStore[writingId]?.remove(userId)
    }

    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> =
        discussionIds.associateWith { tid -> store[tid]?.size?.toLong() ?: 0L }

    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> =
        writingIds.associateWith { tid -> writingStore[tid]?.size?.toLong() ?: 0L }

    override suspend fun getLikedDiscussions(
        discussionIds: Set<Uuid>,
        userId: UID,
    ): Set<Uuid> = discussionIds.filter { tid -> store[tid]?.contains(userId) == true }.toSet()

    override suspend fun getLikedWritings(
        writingIds: Set<String>,
        userId: UID,
    ): Set<String> = writingIds.filter { tid -> writingStore[tid]?.contains(userId) == true }.toSet()
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private class FakeDiscussionRepository(
    private val likes: ILikeRepository,
) : IDiscussionRepository {
    private val store = ConcurrentHashMap<Uuid, Discussion>()

    override suspend fun insert(discussion: Discussion): Discussion {
        store[discussion.id] = discussion
        return discussion
    }

    override suspend fun findById(id: Uuid): Discussion? = store[id]

    override suspend fun findByIdWithLikes(
        id: Uuid,
        currentUserId: UID?,
        isModerator: Boolean,
    ): DiscussionWithLikes? {
        val d = store[id] ?: return null
        if (!isModerator && d.deletedAt != null && d.userId != currentUserId) return null
        // simplistic purge check:
        val pAt = d.purgeAt
        if (pAt != null && pAt < Clock.System.now() && (countChildren(setOf(id))[id] ?: 0) == 0) return null

        val count = likes.countDiscussionLikes(setOf(id))[id] ?: 0L
        val likedByMe =
            currentUserId?.let {
                likes.getLikedDiscussions(setOf(id), it).contains(id)
            } ?: false
        val isReportedByMe = currentUserId?.let { reports.any { r -> r.createdBy == it && r.targetId == id } } ?: false
        return DiscussionWithLikes(d, count, likedByMe, isReportedByMe)
    }

    private val reports = mutableListOf<com.sorenkai.core.model.reports.Report>()

    fun addReport(r: com.sorenkai.core.model.reports.Report) {
        reports.add(r)
    }

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
        order: DiscussionOrder,
    ): List<DiscussionWithLikes> {
        val filtered =
            store.values
                .filter { it.kind == Kind.POST }
                .filter { statuses.contains(it.status) }
                .filter { writingId == null || it.writingId == writingId }
                .filter { if (lang == null) true else it.lang == lang }
                .filter { isModerator || it.deletedAt == null || it.userId == currentUserId }
                .filter {
                    val pAt = it.purgeAt
                    pAt == null || pAt > Clock.System.now() || (countChildren(setOf(it.id))[it.id] ?: 0) > 0
                }.sortedWith(
                    compareByDescending<Discussion> { it.isPinned }
                        .then { d1, d2 ->
                            val cmp = d1.createdAt.compareTo(d2.createdAt)
                            if (order == DiscussionOrder.NEWEST) -cmp else cmp
                        },
                ).drop(offset)
                .take(limit)

        val ids = filtered.map { it.id }.toSet()
        val counts = likes.countDiscussionLikes(ids)
        val likedByMeSet = currentUserId?.let { likes.getLikedDiscussions(ids, it) } ?: emptySet()
        val reportedIds =
            currentUserId?.let { uid ->
                reports.filter { it.createdBy == uid && ids.contains(it.targetId) }.map { it.targetId }.toSet()
            } ?: emptySet()

        return filtered.map {
            DiscussionWithLikes(
                it,
                counts[it.id]
                    ?: 0L,
                likedByMeSet.contains(it.id),
                reportedIds.contains(it.id),
            )
        }
    }

    override suspend fun listComments(
        parentId: Uuid,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
    ): List<DiscussionWithLikes> {
        val filtered =
            store.values
                .filter { it.kind == Kind.COMMENT && it.parentId == parentId }
                .filter { statuses.contains(it.status) }
                .filter { isModerator || it.deletedAt == null || it.userId == currentUserId }
                .filter {
                    val pAt = it.purgeAt
                    pAt == null || pAt > Clock.System.now() || (countChildren(setOf(it.id))[it.id] ?: 0) > 0
                }.sortedBy { it.createdAt }
                .drop(offset)
                .take(limit)

        val ids = filtered.map { it.id }.toSet()
        val counts = likes.countDiscussionLikes(ids)
        val likedByMeSet = currentUserId?.let { likes.getLikedDiscussions(ids, it) } ?: emptySet()
        val reportedIds =
            currentUserId?.let { uid ->
                reports.filter { it.createdBy == uid && ids.contains(it.targetId) }.map { it.targetId }.toSet()
            } ?: emptySet()

        return filtered.map {
            DiscussionWithLikes(
                it,
                counts[it.id]
                    ?: 0L,
                likedByMeSet.contains(it.id),
                reportedIds.contains(it.id),
            )
        }
    }

    override suspend fun countChildren(parentIds: Set<Uuid>): Map<Uuid, Int> =
        store.values
            .filter { it.parentId != null && parentIds.contains(it.parentId) && it.deletedAt == null }
            .groupBy { it.parentId!! }
            .mapValues { it.value.size }

    override suspend fun updateBody(
        id: Uuid,
        body: String,
        updatedAt: Instant,
        currentUserId: UID?,
    ): DiscussionWithLikes? {
        val cur = store[id] ?: return null
        val updated = cur.copy(body = body, updatedAt = updatedAt)
        store[id] = updated
        return findByIdWithLikes(id, currentUserId)
    }

    override suspend fun setStatus(
        id: Uuid,
        status: DiscussionsStatus,
        moderatedBy: String?,
        moderatedAt: Instant?,
        reason: String?,
        deletedAt: Instant?,
        purgeAt: Instant?,
        currentUserId: UID?,
    ): DiscussionWithLikes? {
        val cur = store[id] ?: return null
        val updated =
            cur.copy(
                status = status,
                moderatedBy = moderatedBy,
                moderatedAt = moderatedAt,
                reason = reason,
                deletedAt = deletedAt,
                purgeAt = purgeAt,
            )
        store[id] = updated
        return findByIdWithLikes(id, currentUserId)
    }

    override suspend fun setPinned(
        id: Uuid,
        pinned: Boolean,
    ): UpdateResult {
        val cur = store[id] ?: return UpdateResult.Failure("Discussion not found")
        store[id] = cur.copy(isPinned = pinned)
        return UpdateResult.Success
    }

    override suspend fun setLocked(
        id: Uuid,
        locked: Boolean,
    ): UpdateResult {
        val cur = store[id] ?: return UpdateResult.Failure("Discussion not found")
        store[id] = cur.copy(isLocked = locked)
        return UpdateResult.Success
    }

    override suspend fun restore(
        id: Uuid,
        currentUserId: UID?,
    ): DiscussionWithLikes? {
        val cur = store[id] ?: return null
        val updated = cur.copy(status = DiscussionsStatus.VISIBLE, deletedAt = null, purgeAt = null)
        store[id] = updated
        return findByIdWithLikes(id, currentUserId)
    }
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiscussionServiceTest {
    @Test
    fun create_post_success_and_projection() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null, writings = null, postRateLimiter = null)
            val resp: DiscussionResponsePublic =
                service.create(
                    uid = "user-1",
                    req = DiscussionCreateRequest(kind = Kind.POST, body = "Hello world", userId = "user-1"),
                )
            assertEquals("Hello world", resp.body)
            assertEquals(DiscussionsStatus.VISIBLE, resp.status)
            // Public projection should not expose moderatedBy/At (not present in type)
            assertNotNull(resp.createdAt)
            assertNotNull(resp.id)
        }

    @Test
    fun create_with_custom_status_and_time() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null, writings = null, postRateLimiter = null)
            val customTime = Instant.fromEpochMilliseconds(123456789)
            val resp: DiscussionResponsePublic =
                service.create(
                    uid = "user-1",
                    req =
                        DiscussionCreateRequest(
                            kind = Kind.POST,
                            body = "Custom",
                            userId = "user-1",
                            status = DiscussionsStatus.HIDDEN_BY_MODERATOR,
                            createdAt = customTime,
                        ),
                )
            assertEquals("Custom", resp.body)
            assertEquals(DiscussionsStatus.HIDDEN_BY_MODERATOR, resp.status)
            assertEquals(customTime, resp.createdAt)
        }

    @Test
    fun create_invalid_writingId_fails() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            try {
                service.create(
                    "u1",
                    DiscussionCreateRequest(
                        kind = Kind.POST,
                        body = "hi",
                        userId = "u1",
                        writingId = "not-a-mongo-id",
                    ),
                )
                org.junit.jupiter.api.Assertions
                    .fail("Should have failed")
            } catch (e: DiscussionService.ValidationException) {
                assertEquals("Invalid writingId format", e.message)
            }
        }

    @Test
    fun create_invalid_parentId_fails() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            try {
                service.create(
                    "u1",
                    DiscussionCreateRequest(
                        kind = Kind.COMMENT,
                        body = "hi",
                        userId = "u1",
                        parentId = null,
                    ),
                )
                org.junit.jupiter.api.Assertions
                    .fail("Should have failed")
            } catch (e: DiscussionService.ValidationException) {
                assertEquals("COMMENT requires parentId", e.message)
            }
        }

    @Test
    fun create_with_string_null_ids_succeeds() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            // Simulate frontend sending "null" or "undefined" as strings in writingId
            val resp =
                service.create(
                    "u1",
                    DiscussionCreateRequest(
                        kind = Kind.POST,
                        body = "hi",
                        userId = "u1",
                        writingId = "null",
                        parentId = null,
                    ),
                )
            assertEquals("hi", resp.body)
            assertNull(resp.writingId)
            assertNull(resp.parentId)
        }
    }

    @Test
    fun admin_set_status_sets_moderatedAt_internally() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null, writings = null)
            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            val result =
                service.setStatus(
                    actorUid = "mod-1",
                    id = d.id.toString(),
                    status = DiscussionsStatus.HIDDEN_BY_MODERATOR,
                    reason = "spam",
                )
            assertNotNull(result)
            val after = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.HIDDEN_BY_MODERATOR, after.status)
            assertEquals("mod-1", after.moderatedBy)
            assertNotNull(after.moderatedAt)
            assertEquals("spam", after.reason)
        }

    @Test
    fun like_unlike_idempotent() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt = Clock.System.now(),
                    ),
                )
            val r1 = service.like("user-1", d.id.toString())
            assertEquals(1, r1.likesCount)
            val r2 = service.like("user-1", d.id.toString())
            assertEquals(1, r2.likesCount)
            val r3 = service.unlike("user-1", d.id.toString())
            assertEquals(0, r3.likesCount)
            val r4 = service.unlike("user-1", d.id.toString())
            assertEquals(0, r4.likesCount)
        }

    @Test
    fun edit_and_delete_own() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                    ),
                )
            val edited = service.editOwn("u1", d.id.toString(), "y")
            assertNotNull(edited)
            val deleted = service.deleteOwn("u1", d.id.toString())
            assertNotNull(deleted)
            val after = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.DELETED_BY_AUTHOR, after.status)
        }

    @Test
    fun listRoot_filters_by_language() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)

            repo.insert(
                Discussion(
                    id = Uuid.random(),
                    kind = Kind.POST,
                    body = "En",
                    lang = "en",
                    userId = "u1",
                    createdAt = Clock.System.now(),
                ),
            )
            repo.insert(
                Discussion(
                    id = Uuid.random(),
                    kind = Kind.POST,
                    body = "Es",
                    lang = "es",
                    userId = "u1",
                    createdAt = Clock.System.now(),
                ),
            )

            val enResult = service.listRoot(10, null, lang = "en").first
            assertEquals(1, enResult.size)
            assertEquals("En", enResult[0].body)

            val esResult = service.listRoot(10, null, lang = "es").first
            assertEquals(1, esResult.size)
            assertEquals("Es", esResult[0].body)

            val allResult = service.listRoot(10, null, lang = null).first
            assertEquals(2, allResult.size)
        }

    @Test
    fun listRoot_shows_likedByMe_when_user_provided() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)

            val d1 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        body = "Post 1",
                        userId = "u1",
                        createdAt = Clock.System.now(),
                    ),
                )
            val d2 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        body = "Post 2",
                        userId = "u1",
                        createdAt = Clock.System.now(),
                    ),
                )

            likes.likeDiscussion(d1.id, "user-A")

            // User A should see d1 as liked
            val resultA =
                service
                    .listRoot(
                        10,
                        null,
                        currentUid = "user-A",
                    ).first
            val p1A = resultA.find { it.id.toString() == d1.id.toString() }!!
            val p2A = resultA.find { it.id.toString() == d2.id.toString() }!!
            assertTrue(p1A.isLikedByMe)
            assertEquals(1, p1A.likesCount)
            assertFalse(p2A.isLikedByMe)
            assertEquals(0, p2A.likesCount)

            // Anonymous should see none as liked
            val resultAnon = service.listRoot(10, null, currentUid = null).first
            assertFalse(resultAnon.any { it.isLikedByMe })
            assertEquals(1, resultAnon.find { it.id.toString() == d1.id.toString() }!!.likesCount)
        }

    @Test
    fun listRoot_ordering_and_childCount() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "user-1"

            // Create three posts with different times
            val d1 = service.create(uid, DiscussionCreateRequest(kind = Kind.POST, body = "First", userId = uid))
            delay(10)
            val d2 = service.create(uid, DiscussionCreateRequest(kind = Kind.POST, body = "Second", userId = uid))
            delay(10)
            val d3 = service.create(uid, DiscussionCreateRequest(kind = Kind.POST, body = "Third", userId = uid))

            // Add a reply to the second post
            service.create(
                uid,
                DiscussionCreateRequest(
                    kind = Kind.COMMENT,
                    body = "Reply to second",
                    userId = uid,
                    parentId =
                        d2
                            .id,
                ),
            )

            // Test newest first (default)
            val newest = service.listRoot(10, null, uid, order = DiscussionOrder.NEWEST).first
            assertEquals("Third", newest[0].body)
            assertEquals("Second", newest[1].body)
            assertEquals("First", newest[2].body)

            // Test oldest first
            val oldest = service.listRoot(10, null, uid, order = DiscussionOrder.OLDEST).first
            assertEquals("First", oldest[0].body)
            assertEquals("Second", oldest[1].body)
            assertEquals("Third", oldest[2].body)

            // Test childCount
            assertEquals(0, newest[0].childCount) // Third
            assertEquals(1, newest[1].childCount) // Second
            assertEquals(0, newest[2].childCount) // First
        }

    @Test
    fun author_delete_grace_period_visibility() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"
            val other = "u2"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Secret",
                        createdAt = Clock.System.now(),
                    ),
                )

            // Author deletes
            service.deleteOwn(uid, d.id.toString())

            val post = repo.findById(d.id)!!
            assertNotNull(post.deletedAt)
            assertNotNull(post.purgeAt)
            assertEquals(DiscussionsStatus.DELETED_BY_AUTHOR, post.status)

            // Author should still see it
            val authorList = service.listRoot(10, null, currentUid = uid).first
            assertTrue(authorList.any { it.id.toString() == d.id.toString() })

            // Other user should NOT see it
            val otherList = service.listRoot(10, null, currentUid = other).first
            assertFalse(otherList.any { it.id.toString() == d.id.toString() })

            // Moderator should see it
            val modList = service.listRoot(10, null, currentUid = "mod", isModerator = true).first
            assertTrue(modList.any { it.id.toString() == d.id.toString() })
        }

    @Test
    fun purge_eligibility_after_purgeAt() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Old",
                        createdAt = Clock.System.now(),
                        deletedAt = Clock.System.now().minus(25.toDuration(kotlin.time.DurationUnit.HOURS)),
                        purgeAt = Clock.System.now().minus(1.toDuration(kotlin.time.DurationUnit.HOURS)),
                        status = DiscussionsStatus.DELETED_BY_AUTHOR,
                    ),
                )

            // Even author should NOT see it now because it is past purgeAt and has no children
            val authorList = service.listRoot(10, null, currentUid = uid).first
            assertFalse(authorList.any { it.id.toString() == d.id.toString() })
        }

    @Test
    fun moderator_delete_no_purgeAt() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val modUid = "mod1"
            val authorUid = "u1"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = authorUid,
                        body = "Rule break",
                        createdAt = Clock.System.now(),
                    ),
                )

            // Moderator sets status
            service.setStatus(
                modUid,
                d.id.toString(),
                DiscussionsStatus.DELETED_BY_MODERATOR,
                "Policy violation",
            )

            val post = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.DELETED_BY_MODERATOR, post.status)
            assertNotNull(post.deletedAt)
            assertNull(post.purgeAt)

            // Author should NOT see it (it was deleted by moderator, not by author)
            // Wait, the rule is "Rows with deletedAt != null: Are excluded ... unless requester is the author".
            // It doesn't distinguish who set deletedAt.
            // However, standard queries usually only include VISIBLE and DELETED_BY_AUTHOR (during grace period).
            // My listRoot uses setOf(VISIBLE, DELETED_BY_AUTHOR).
            val authorList = service.listRoot(10, null, currentUid = authorUid).first
            assertFalse(authorList.any { it.id.toString() == d.id.toString() })

            // Moderator should see it
            val modList = service.listRoot(10, null, currentUid = modUid, isModerator = true).first
            assertTrue(modList.any { it.id.toString() == d.id.toString() })
        }

    @Test
    fun author_restore_within_grace_period() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Original",
                        createdAt = Clock.System.now(),
                    ),
                )
            service.deleteOwn(uid, d.id.toString())

            val deleted = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.DELETED_BY_AUTHOR, deleted.status)

            val restored = service.restoreOwn(uid, d.id.toString())
            assertNotNull(restored)
            assertEquals("Original", restored!!.body)

            val final = repo.findById(d.id)!!
            assertEquals(DiscussionsStatus.VISIBLE, final.status)
            assertNull(final.deletedAt)
            assertNull(final.purgeAt)
        }

    @Test
    fun restore_rejection_expired() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Old",
                        createdAt = Clock.System.now(),
                        deletedAt = Clock.System.now().minus(48.hours),
                        purgeAt = Clock.System.now().minus(24.hours),
                        status = DiscussionsStatus.DELETED_BY_AUTHOR,
                    ),
                )

            try {
                service.restoreOwn(uid, d.id.toString())
                org.junit.jupiter.api.Assertions
                    .fail("Should have failed")
            } catch (e: DiscussionService.ValidationException) {
                assertEquals("Grace period has expired", e.message)
            }
        }

    @Test
    fun restore_rejection_moderator_deleted() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Bad",
                        createdAt = Clock.System.now(),
                        deletedAt = Clock.System.now(),
                        status = DiscussionsStatus.DELETED_BY_MODERATOR,
                    ),
                )

            try {
                service.restoreOwn(uid, d.id.toString())
                org.junit.jupiter.api.Assertions
                    .fail("Should have failed")
            } catch (e: DiscussionService.ValidationException) {
                assertEquals("Post was not deleted by author", e.message)
            }
        }

    @Test
    fun child_preservation_visibility() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            // Create post with child
            val p =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Parent",
                        createdAt = Clock.System.now(),
                    ),
                )
            repo.insert(
                Discussion(
                    id = Uuid.random(),
                    kind = Kind.COMMENT,
                    parentId = p.id,
                    userId = "u2",
                    body = "Child",
                    createdAt = Clock.System.now(),
                ),
            )

            // Author deletes parent, and grace period expires
            repo.insert(
                p.copy(
                    status = DiscussionsStatus.DELETED_BY_AUTHOR,
                    deletedAt = Clock.System.now().minus(48.hours),
                    purgeAt = Clock.System.now().minus(24.hours),
                ),
            )

            // Even though it's purged, it has children, so it MUST remain queryable
            val authorList = service.listRoot(10, null, currentUid = uid).first
            assertTrue(
                authorList.any { it.id.toString() == p.id.toString() },
                "Parent should be visible because it has children",
            )

            val pResponse = authorList.find { it.id.toString() == p.id.toString() }!!
            assertEquals("This post has been deleted by the author.", pResponse.body)
        }

    @Test
    fun placeholder_body_correctness() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)
            val uid = "u1"

            val d1 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Author hide",
                        createdAt = Clock.System.now(),
                        status = DiscussionsStatus.DELETED_BY_AUTHOR,
                        deletedAt = Clock.System.now(),
                    ),
                )
            val d2 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = uid,
                        body = "Mod hide",
                        createdAt = Clock.System.now(),
                        status = DiscussionsStatus.DELETED_BY_MODERATOR,
                        deletedAt = Clock.System.now(),
                    ),
                )

            val authorList = service.listRoot(10, null, currentUid = uid).first
            assertEquals(
                "This post has been deleted by the author.",
                authorList.find { it.id.toString() == d1.id.toString() }?.body,
            )

            val modList = service.listRoot(10, null, currentUid = "mod", isModerator = true).first
            assertEquals(
                "This post has been deleted by a moderator.",
                modList.find { it.id.toString() == d2.id.toString() }?.body,
            )
        }

    @Test
    fun `listRoot identifies reported posts`() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)

            val p1 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "p1",
                        lang = "en",
                        createdAt = Clock.System.now(),
                    ),
                )
            val p2 =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "p2",
                        lang = "en",
                        createdAt = Clock.System.now(),
                    ),
                )

            repo.addReport(
                com.sorenkai.core.model.reports.Report(
                    targetType = com.sorenkai.core.model.reports.TargetType.POST,
                    targetId = p1.id,
                    reason = "spam",
                    createdBy = "me",
                ),
            )

            val (items, _) = service.listRoot(pageSize = 10, cursor = null, currentUid = "me")
            assertEquals(2, items.size)

            val p1Resp = items.find { it.id == p1.id }!!
            val p2Resp = items.find { it.id == p2.id }!!

            assertTrue(p1Resp.isReportedByMe)
            assertFalse(p2Resp.isReportedByMe)

            val (itemsAnon, _) = service.listRoot(pageSize = 10, cursor = null, currentUid = null)
            assertFalse(itemsAnon.any { it.isReportedByMe })
        }

    @Test
    fun `listRoot returns posts with writingId when writingId is null`() =
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeDiscussionRepository(likes)
            val service = DiscussionService(repo, likes, users = null)

            val p1 = repo.insert(
                Discussion(
                    id = Uuid.random(),
                    kind = Kind.POST,
                    userId = "u1",
                    body = "p1",
                    lang = "en",
                    createdAt = Clock.System.now(),
                    writingId = null
                )
            )
            val p2 = repo.insert(
                Discussion(
                    id = Uuid.random(),
                    kind = Kind.POST,
                    userId = "u1",
                    body = "p2",
                    lang = "en",
                    createdAt = Clock.System.now(),
                    writingId = "w1"
                )
            )

            val (items, _) = service.listRoot(pageSize = 10, cursor = null)
            assertEquals(2, items.size, "Should return both posts regardless of writingId")
            assertTrue(items.any { it.id == p1.id })
            assertTrue(items.any { it.id == p2.id })

            // Verify listByWriting still works correctly (filters)
            val (itemsW1, _) = service.listByWriting(writingId = "w1", pageSize = 10, cursor = null)
            assertEquals(1, itemsW1.size)
            assertEquals(p2.id, itemsW1.first().id)
        }
}
