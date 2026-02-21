package com.sorenkai.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.paging.Cursor
import com.sorenkai.core.repo.DiscussionOrder
import com.sorenkai.core.repo.DiscussionWithLikes
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.core.repo.WritingWithLikes
import com.sorenkai.infrastructure.mongo.MongoProvider.client
import com.sorenkai.server.configuration.configureSerialization
import com.sorenkai.server.routing.writings.writingsRoutes
import com.sorenkai.writings.services.WritingService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Simple in-memory fake for routing tests
@OptIn(ExperimentalUuidApi::class)
@kotlin.time.ExperimentalTime
private class InMemoryRepo(
    private val likes: ILikeRepository,
) : IDiscussionRepository {
    private val store = mutableMapOf<Uuid, Discussion>()

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
        val count = likes.countDiscussionLikes(setOf(id))[id] ?: 0L
        val likedByMe = currentUserId?.let { likes.getLikedDiscussions(setOf(id), it).contains(id) } ?: false
        return DiscussionWithLikes(d, count, likedByMe, false)
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
                .asSequence()
                .filter { it.kind == Kind.POST }
                .filter { statuses.contains(it.status) }
                .filter { if (writingId == null) it.writingId.isNullOrBlank() else it.writingId == writingId }
                .filter { if (lang == null) true else it.lang == lang }
                .sortedWith(
                    compareByDescending<Discussion> { it.isPinned }
                        .then { d1, d2 ->
                            val cmp = d1.createdAt.compareTo(d2.createdAt)
                            if (order == DiscussionOrder.NEWEST) -cmp else cmp
                        },
                ).drop(offset)
                .take(limit)
                .toList()

        val ids = filtered.map { it.id }.toSet()
        val counts = likes.countDiscussionLikes(ids)
        val likedByMeSet = currentUserId?.let { likes.getLikedDiscussions(ids, it) } ?: emptySet()

        return filtered.map {
            DiscussionWithLikes(it, counts[it.id] ?: 0L, likedByMeSet.contains(it.id), false)
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
                .asSequence()
                .filter { it.kind == Kind.COMMENT && it.parentId == parentId }
                .filter { statuses.contains(it.status) }
                .sortedBy { it.createdAt }
                .drop(offset)
                .take(limit)
                .toList()

        val ids = filtered.map { it.id }.toSet()
        val counts = likes.countDiscussionLikes(ids)
        val likedByMeSet = currentUserId?.let { likes.getLikedDiscussions(ids, it) } ?: emptySet()

        return filtered.map {
            DiscussionWithLikes(it, counts[it.id] ?: 0L, likedByMeSet.contains(it.id), false)
        }
    }

    override suspend fun countChildren(parentIds: Set<Uuid>): Map<Uuid, Int> =
        store.values
            .filter { it.parentId != null && parentIds.contains(it.parentId) }
            .groupBy { it.parentId!! }
            .mapValues { it.value.size }

    override suspend fun updateBody(
        id: Uuid,
        body: String,
        updatedAt: kotlin.time.Instant,
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
        moderatedBy: UID?,
        moderatedAt: kotlin.time.Instant?,
        reason: String?,
        deletedAt: kotlin.time.Instant?,
        purgeAt: kotlin.time.Instant?,
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
        val cur =
            store[id] ?: return UpdateResult
                .Failure("Discussion not found")
        store[id] = cur.copy(isPinned = pinned)
        return UpdateResult.Success
    }

    override suspend fun setLocked(
        id: Uuid,
        locked: Boolean,
    ): UpdateResult {
        val cur =
            store[id] ?: return UpdateResult
                .Failure("Discussion not found")
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

@kotlin.time.ExperimentalTime
class CommunityRoutesV2Test {
    private fun Application.testModule(
        repo: IDiscussionRepository,
        likes: ILikeRepository,
    ) {
        // Minimal modules: serialization + auth + DI + routing
        configureSerialization()
        install(Authentication) {
            jwt("zitadel") {
                verifier(
                    JWT
                        .require(Algorithm.HMAC256("test-secret"))
                        .build(),
                )
                validate { cred ->
                    // Accept any token; subject will be present
                    com.sorenkai.server.security
                        .UserPrincipal(
                            com.sorenkai.core.model.auth
                                .AuthUser(cred.subject ?: "test"),
                        )
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        install(Koin) {
            modules(
                module {
                    single { DiscussionService(repo, likes, users = null, writings = null, postRateLimiter = null) }
                    single {
                        WritingService(
                            object : IWritingRepository {
                                override suspend fun create(doc: Writing) = doc

                                override suspend fun findById(id: String) = null

                                override suspend fun getPublishedById(
                                    id: String,
                                    currentUserId: UID?,
                                ) = WritingWithLikes(
                                    Writing(
                                        id = "w1",
                                        title = "title",
                                        language = "en",
                                        slug = "slug",
                                        writingsStatus = com.sorenkai.core.model.writings.WritingsStatus.PUBLISHED,
                                        authorId = "u1",
                                        createdAt =
                                            kotlin.time.Clock.System
                                                .now(),
                                        updatedAt = null,
                                    ),
                                    0,
                                    false,
                                )

                                override suspend fun getPublishedBySlug(
                                    slug: String,
                                    currentUserId: UID?,
                                ) = WritingWithLikes(
                                    Writing(
                                        id = "w1",
                                        title = "title",
                                        language = "en",
                                        slug = slug,
                                        writingsStatus = com.sorenkai.core.model.writings.WritingsStatus.PUBLISHED,
                                        authorId = "u1",
                                        createdAt =
                                            kotlin.time.Clock.System
                                                .now(),
                                        updatedAt = null,
                                    ),
                                    0,
                                    false,
                                )

                                override suspend fun getPublishedByLangAndId(
                                    lang: String,
                                    id: String,
                                    currentUserId: UID?,
                                ) = null

                                override suspend fun listPublished(
                                    limit: Int,
                                    offset: Int,
                                    lang: String?,
                                    currentUserId: UID?,
                                ) = emptyList<WritingWithLikes>()

                                override suspend fun listByTag(
                                    tag: String,
                                    limit: Int,
                                    offset: Int,
                                    lang: String?,
                                    currentUserId: UID?,
                                ) = emptyList<WritingWithLikes>()

                                override suspend fun distinctTags() = emptyList<String>()

                                override suspend fun incShare(id: String) = null

                                override suspend fun incView(id: String) = null

                                override suspend fun incSalesClick(id: String) = null

                                override suspend fun deleteById(id: String) = false

                                override suspend fun updateStatus(
                                    id: String,
                                    status: com.sorenkai.core.model.writings.WritingsStatus,
                                ) = false

                                override suspend fun setFeatured(
                                    id: String,
                                    featured: Boolean,
                                ) = false

                                override suspend fun updateSalesLink(
                                    id: String,
                                    salesLink: String?,
                                    jsonLd: kotlinx.serialization.json.JsonObject?,
                                ) = false

                                override suspend fun updateCoverUrl(
                                    id: String,
                                    coverUrl: String?,
                                    jsonLd: kotlinx.serialization.json.JsonObject?,
                                ) = false

                                override suspend fun updatePublisher(
                                    id: String,
                                    jsonLd: kotlinx.serialization.json.JsonObject?,
                                ) = false
                            },
                            likes,
                            users = null,
                        )
                    }
                    single<com.sorenkai.core.rate.RateLimiter> {
                        object : com.sorenkai.core.rate.RateLimiter {
                            override suspend fun tryConsume(
                                uid: String,
                                action: String,
                            ) = true
                        }
                    }
                },
            )
        }

        routing {
            route("/v2") {
                communityRoutes()
                val writingService by inject<WritingService>()
                writingsRoutes(writingService)
            }
        }
    }

    private fun bearer(uid: String): String {
        val token = JWT.create().withSubject(uid).sign(Algorithm.HMAC256("test-secret"))
        return "Bearer $token"
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `create and like discussion happy path`() =
        testApplication {
            val likes =
                object : ILikeRepository {
                    val discussionLikes = mutableMapOf<Uuid, MutableSet<String>>()
                    val writingLikes = mutableMapOf<String, MutableSet<String>>()

                    override suspend fun likeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {
                        discussionLikes.getOrPut(discussionId) { mutableSetOf() }.add(userId)
                    }

                    override suspend fun unlikeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {
                        discussionLikes[discussionId]?.remove(userId)
                    }

                    override suspend fun likeWriting(
                        writingId: String,
                        userId: UID,
                    ) {
                        writingLikes.getOrPut(writingId) { mutableSetOf() }.add(userId)
                    }

                    override suspend fun unlikeWriting(
                        writingId: String,
                        userId: UID,
                    ) {
                        writingLikes[writingId]?.remove(userId)
                    }

                    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> =
                        discussionIds.associateWith { discussionLikes[it]?.size?.toLong() ?: 0L }

                    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> =
                        writingIds.associateWith { writingLikes[it]?.size?.toLong() ?: 0L }

                    override suspend fun getLikedDiscussions(
                        discussionIds: Set<Uuid>,
                        userId: UID,
                    ): Set<Uuid> = discussionIds.filter { discussionLikes[it]?.contains(userId) == true }.toSet()

                    override suspend fun getLikedWritings(
                        writingIds: Set<String>,
                        userId: UID,
                    ): Set<String> = writingIds.filter { writingLikes[it]?.contains(userId) == true }.toSet()
                }
            val repo = InMemoryRepo(likes)
            application { testModule(repo, likes) }

            // Create
            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello", userId = "u1")
            val createResp =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }

            assertEquals(HttpStatusCode.Created, createResp.status)

            val createdJson = createResp.bodyAsText()

            // Stable assertions for create
            assertTrue(createdJson.contains("\"id\":"))
            assertTrue(createdJson.contains("\"body\":\"hello\""))
            assertFalse(createdJson.contains("moderatedBy"))

            // Extract id (loose parsing is fine here)
            val idRegex = Regex("\"id\":\"([^\"]+)\"")
            val id = idRegex.find(createdJson)!!.groupValues[1]

            // Like
            val likeResp =
                client.post("/v2/community/discussions/$id/like") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                }

            assertEquals(HttpStatusCode.OK, likeResp.status)

            val likedJson = likeResp.bodyAsText()
            assertTrue(likedJson.contains("\"likesCount\":1"))
            assertTrue(likedJson.contains("\"isLikedByMe\":true"))

            // Writing Like
            val wLikeResp =
                client.post("/v2/writings/w1/like") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                }
            assertEquals(HttpStatusCode.OK, wLikeResp.status)
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `edit and delete discussion return authoritative state`() =
        testApplication {
            val likes =
                @OptIn(ExperimentalUuidApi::class)
                object : ILikeRepository {
                    override suspend fun likeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun likeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> = emptyMap()

                    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> = emptyMap()

                    override suspend fun getLikedDiscussions(
                        discussionIds: Set<Uuid>,
                        userId: UID,
                    ): Set<Uuid> = emptySet()

                    override suspend fun getLikedWritings(
                        writingIds: Set<String>,
                        userId: UID,
                    ): Set<String> = emptySet()
                }
            val repo = InMemoryRepo(likes)
            application { testModule(repo, likes) }

            // 1. Create
            val req = DiscussionCreateRequest(kind = Kind.POST, body = "initial", userId = "u1")
            val createResp =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        kotlinx.serialization.json.Json
                            .encodeToString(req),
                    )
                }
            val idRegex = Regex("\"id\":\"([^\"]+)\"")
            val id = idRegex.find(createResp.bodyAsText())!!.groupValues[1]

            // 2. Edit
            val editResp =
                client.patch("/v2/community/discussions/$id") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(
                        kotlinx.serialization.json.Json
                            .encodeToString(mapOf("body" to "updated")),
                    )
                }
            assertEquals(HttpStatusCode.OK, editResp.status)
            val editedJson = editResp.bodyAsText()
            assertTrue(editedJson.contains("\"body\":\"updated\""))
            assertTrue(editedJson.contains("\"id\":\"$id\""))

            // 3. Delete
            val deleteResp =
                client.delete("/v2/community/discussions/$id") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                }
            assertEquals(HttpStatusCode.OK, deleteResp.status)
            val deletedJson = deleteResp.bodyAsText()
            assertTrue(deletedJson.contains("\"status\":\"DELETED_BY_AUTHOR\""))
            assertTrue(deletedJson.contains("\"id\":\"$id\""))
        }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `validation error when missing auth`() =
        testApplication {
            val likes =
                @OptIn(ExperimentalUuidApi::class)
                object : ILikeRepository {
                    override suspend fun likeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun likeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> = emptyMap()

                    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> = emptyMap()

                    override suspend fun getLikedDiscussions(
                        discussionIds: Set<Uuid>,
                        userId: UID,
                    ): Set<Uuid> = emptySet()

                    override suspend fun getLikedWritings(
                        writingIds: Set<String>,
                        userId: UID,
                    ): Set<String> = emptySet()
                }
            val repo = InMemoryRepo(likes)
            application { testModule(repo, likes) }

            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello", userId = "u1")
            val resp =
                client.post("/v2/community/discussions") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            val status = resp.status
            // In SorenKai, auth failure on a route often results in 404 if the route is nested in authenticate
            // but let's be flexible in the test for now as long as it's not 200.
            assertTrue(status == HttpStatusCode.Unauthorized || status == HttpStatusCode.NotFound)
        }

    @Test
    fun `get writing by slug backward compatibility`() =
        testApplication {
            val likes =
                @OptIn(ExperimentalUuidApi::class)
                object : ILikeRepository {
                    override suspend fun likeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeDiscussion(
                        discussionId: Uuid,
                        userId: UID,
                    ) {}

                    override suspend fun likeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun unlikeWriting(
                        writingId: String,
                        userId: UID,
                    ) {}

                    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> = emptyMap()

                    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> = emptyMap()

                    override suspend fun getLikedDiscussions(
                        discussionIds: Set<Uuid>,
                        userId: UID,
                    ): Set<Uuid> = emptySet()

                    override suspend fun getLikedWritings(
                        writingIds: Set<String>,
                        userId: UID,
                    ): Set<String> = emptySet()
                }
            val repo = InMemoryRepo(likes)
            application { testModule(repo, likes) }

            val resp = client.get("/v2/writings?slug=slug")
            assertEquals(HttpStatusCode.OK, resp.status)
            val json = resp.bodyAsText()
            assertTrue(json.contains("\"slug\":\"slug\""))
            assertTrue(json.contains("\"id\":\"w1\""))
        }
}
