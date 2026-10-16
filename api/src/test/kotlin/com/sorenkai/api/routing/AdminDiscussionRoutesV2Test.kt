package com.sorenkai.api.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.common.model.auth.AuthUser
import com.sorenkai.common.model.auth.UID
import com.sorenkai.common.model.response.UpdateResult
import com.sorenkai.discussions.model.Discussion
import com.sorenkai.discussions.model.DiscussionsStatus
import com.sorenkai.discussions.model.Kind
import com.sorenkai.discussions.repository.DiscussionWithLikes
import com.sorenkai.discussions.repository.IDiscussionRepository
import com.sorenkai.likes.repository.ILikeRepository
import com.sorenkai.server.configuration.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@ExperimentalTime
private class AdminInMemoryRepo :
    IDiscussionRepository,
    ILikeRepository {
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
        order: com.sorenkai.discussions.repository.DiscussionOrder,
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
        moderatedBy: UID?,
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

@OptIn(ExperimentalUuidApi::class)
@ExperimentalTime
class AdminDiscussionRoutesV2Test {
    private fun Application.testModule(repo: IDiscussionRepository) {
        configureSerialization()

        install(Authentication) {
            jwt("zitadel") {
                verifier(JWT.require(Algorithm.HMAC256("test-secret")).build())
                validate { cred ->
                    val roles = if (cred.subject == "mod-1") setOf(com.sorenkai.common.model.auth.Role.MOD) else emptySet()
                    com.sorenkai.api.security
                        .UserPrincipal(
                            AuthUser(cred.subject ?: "test", roles),
                        )
                }
            }
        }

        install(Koin) {
            modules(
                module {
                    val repoAsLikes = repo as ILikeRepository
                    single { DiscussionService(repo, repoAsLikes, writings = null, postRateLimiter = null) }
                },
            )
        }

        routing {
            route("/v2") {
                adminDiscussionRoutesV2()
            }
        }
    }

    private fun bearer(uid: String): String = "Bearer " + JWT.create().withSubject(uid).sign(Algorithm.HMAC256("test-secret"))

    @Test
    fun `patch status happy path`() =
        testApplication {
            val repo = AdminInMemoryRepo()
            application { testModule(repo) }

            // Seed a discussion via repository directly
            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt =
                            Clock.System
                                .now(),
                    ),
                )

            val body =
                buildJsonObject {
                    put("status", DiscussionsStatus.HIDDEN_BY_MODERATOR.name)
                    put("reason", "moderation")
                }.toString()

            val resp =
                client.patch("/v2/admin/discussions/${d.id}/status") {
                    header(HttpHeaders.Authorization, bearer("mod-1"))
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            assertTrue(resp.bodyAsText().contains("\"status\":\"HIDDEN_BY_MODERATOR\""))
            assertTrue(resp.bodyAsText().contains("\"reason\":\"moderation\""))
        }

    @Test
    fun `patch status invalid status`() =
        testApplication {
            val repo = AdminInMemoryRepo()
            application { testModule(repo) }
            val d =
                repo.insert(
                    Discussion(
                        id = Uuid.random(),
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt =
                            Clock.System
                                .now(),
                    ),
                )
            val body = buildJsonObject { put("status", "BAD") }.toString()
            val resp =
                client.patch("/v2/admin/discussions/${d.id}/status") {
                    header(HttpHeaders.Authorization, bearer("mod-1"))
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertTrue(resp.bodyAsText().contains("VALIDATION_FAILED"))
        }
}




