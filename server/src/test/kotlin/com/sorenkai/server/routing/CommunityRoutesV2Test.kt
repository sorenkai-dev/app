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
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.infrastructure.mongo.MongoProvider.client
import com.sorenkai.server.configuration.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.utils.EmptyContent.contentType
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

// Simple in-memory fake for routing tests
@kotlin.time.ExperimentalTime
private class InMemoryRepo : IDiscussionRepository {
    private val store = mutableMapOf<String, Discussion>()

    override suspend fun insert(discussion: Discussion): Discussion {
        store[discussion.id] = discussion
        return discussion
    }

    override suspend fun findById(id: String): Discussion? = store[id]

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> =
        store.values
            .asSequence()
            .filter { it.kind == Kind.POST }
            .filter { statuses.contains(it.status) }
            .filter { if (writingId == null) it.writingId.isNullOrBlank() else it.writingId == writingId }
            .filter { if (lang == null) true else it.lang == lang }
            .sortedWith(compareByDescending<Discussion> { it.isPinned }.thenByDescending { it.createdAt })
            .drop(offset)
            .take(limit)
            .toList()

    override suspend fun listComments(
        parentId: String,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> =
        store.values
            .asSequence()
            .filter { it.kind == Kind.COMMENT && it.parentId == parentId }
            .filter { statuses.contains(it.status) }
            .sortedBy { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    override suspend fun updateBody(
        id: String,
        body: String,
        updatedAt: kotlin.time.Instant,
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(body = body, updatedAt = updatedAt)
        return true
    }

    override suspend fun setStatus(
        id: String,
        status: DiscussionsStatus,
        moderatedBy: String?,
        moderatedAt: kotlin.time.Instant?,
        reason: String?,
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(status = status, moderatedBy = moderatedBy, moderatedAt = moderatedAt, reason = reason)
        return true
    }

    override suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult {
        val cur =
            store[id] ?: return UpdateResult
                .Failure("Discussion not found")
        store[id] = cur.copy(isPinned = pinned)
        return UpdateResult.Success
    }

    override suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult {
        val cur =
            store[id] ?: return UpdateResult
                .Failure("Discussion not found")
        store[id] = cur.copy(isLocked = locked)
        return UpdateResult.Success
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
                    single { DiscussionService(repo, likes, writings = null, postRateLimiter = null) }
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
            }
        }
    }

    private fun bearer(uid: String): String {
        val token = JWT.create().withSubject(uid).sign(Algorithm.HMAC256("test-secret"))
        return "Bearer $token"
    }

    @Test
    fun `create and like discussion happy path`() =
        testApplication {
            val repo = InMemoryRepo()
            val likes =
                object : ILikeRepository {
                    val store = mutableMapOf<String, MutableSet<String>>()

                    override suspend fun like(
                        targetId: String,
                        userId: UID,
                    ) {
                        store.getOrPut(targetId) { mutableSetOf() }.add(userId)
                    }

                    override suspend fun unlike(
                        targetId: String,
                        userId: UID,
                    ) {
                        store[targetId]?.remove(userId)
                    }

                    override suspend fun countLikes(targetIds: List<String>): Map<String, Long> =
                        targetIds.associateWith { store[it]?.size?.toLong() ?: 0L }

                    override suspend fun getLikedTargets(
                        targetIds: List<String>,
                        userId: UID,
                    ): Set<String> =
                        targetIds
                            .filter {
                                store[it]?.contains(userId) == true
                            }.toSet()
                }
            application { testModule(repo, likes) }

            // Create
            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello")
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
        }

    @Test
    fun `validation error when missing auth`() =
        testApplication {
            val repo = InMemoryRepo()
            val likes =
                object : ILikeRepository {
                    override suspend fun like(
                        targetId: String,
                        userId: UID,
                    ) {}

                    override suspend fun unlike(
                        targetId: String,
                        userId: UID,
                    ) {}

                    override suspend fun countLikes(targetIds: List<String>) = emptyMap<String, Long>()

                    override suspend fun getLikedTargets(
                        targetIds: List<String>,
                        userId: UID,
                    ) = emptySet<String>()
                }
            application { testModule(repo, likes) }

            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello")
            val resp =
                client.post("/v2/community/discussions") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
}
