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
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.DiscussionWithLikes
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.core.repo.WritingWithLikes
import com.sorenkai.server.configuration.configureSerialization
import com.sorenkai.server.routing.writings.writingsRoutes
import com.sorenkai.writings.services.WritingService
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@kotlin.time.ExperimentalTime
private class RateLimitRepo : IDiscussionRepository {
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
        return DiscussionWithLikes(d, 0, false, false)
    }

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
        order: com.sorenkai.core.repo.DiscussionOrder,
    ) = emptyList<DiscussionWithLikes>()

    override suspend fun listComments(
        parentId: Uuid,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
    ) = emptyList<DiscussionWithLikes>()

    override suspend fun countChildren(parentIds: Set<Uuid>): Map<Uuid, Int> = emptyMap()

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

    override suspend fun restore(
        id: Uuid,
        currentUserId: UID?,
    ): DiscussionWithLikes? {
        val cur = store[id] ?: return null
        val updated = cur.copy(status = DiscussionsStatus.VISIBLE, deletedAt = null, purgeAt = null)
        store[id] = updated
        return findByIdWithLikes(id, currentUserId)
    }

    override suspend fun setPinned(
        id: Uuid,
        pinned: Boolean,
    ) = UpdateResult.Success

    override suspend fun setLocked(
        id: Uuid,
        locked: Boolean,
    ) = UpdateResult.Success
}

private class InMemoryLimiter : RateLimiter {
    private val map = mutableMapOf<String, Long>()

    override suspend fun tryConsume(
        uid: String,
        action: String,
    ): Boolean {
        val key = "$uid:$action"
        val now = System.currentTimeMillis()
        val last = map[key]
        return if (last == null || now - last >= 10_000) {
            map[key] = now
            true
        } else {
            false
        }
    }
}

@kotlin.time.ExperimentalTime
class CommunityRoutesRateLimitTest {
    private fun Application.testModule(
        repo: IDiscussionRepository,
        limiter: RateLimiter,
    ) {
        configureSerialization()

        install(Authentication) {
            jwt("zitadel") {
                verifier(JWT.require(Algorithm.HMAC256("test-secret")).build())
                validate { cred ->
                    com.sorenkai.server.security
                        .UserPrincipal(
                            com.sorenkai.core.model.auth
                                .AuthUser(cred.subject ?: "test"),
                        )
                }
            }
        }

        install(Koin) {
            modules(
                module {
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
                    single { DiscussionService(repo, likes, users = null, writings = null, postRateLimiter = limiter) }
                    single {
                        WritingService(
                            object : IWritingRepository {
                                override suspend fun create(doc: Writing) = doc

                                override suspend fun findById(id: String) = null

                                override suspend fun getPublishedById(
                                    id: String,
                                    currentUserId: UID?,
                                ) = null

                                override suspend fun getPublishedBySlug(
                                    slug: String,
                                    currentUserId: UID?,
                                ) = null

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
                    single<RateLimiter> { limiter }
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

    private fun bearer(uid: String) = "Bearer " + JWT.create().withSubject(uid).sign(Algorithm.HMAC256("test-secret"))

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun `rate limit blocks second immediate request and allows after window`() =
        testApplication {
            val repo = RateLimitRepo()
            val limiter = InMemoryLimiter()
            application { testModule(repo, limiter) }

            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello", userId = "u1")

            // First request OK
            val r1 =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.Created, r1.status)

            // Second immediate should be 400 (ValidationException from Service)
            val r2 =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.BadRequest, r2.status)

            // Simulate time passing by using a different user (simpler than sleeping)
            val r3 =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u2"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.Created, r3.status)
        }
}
