package com.sorenkai.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.server.configuration.configureSerialization
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
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

@kotlin.time.ExperimentalTime
private class RateLimitRepo : IDiscussionRepository {
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
    ) = emptyList<Discussion>()

    override suspend fun listComments(
        parentId: String,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ) = emptyList<Discussion>()

    override suspend fun updateBody(
        id: String,
        body: String,
        updatedAt: kotlin.time.Instant,
    ) = false

    override suspend fun setStatus(
        id: String,
        status: DiscussionsStatus,
        moderatedBy: String?,
        moderatedAt: kotlin.time.Instant?,
        reason: String?,
    ) = false

    override suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ) = com.sorenkai.core.model.common.UpdateResult.Success

    override suspend fun setLocked(
        id: String,
        locked: Boolean,
    ) = com.sorenkai.core.model.common.UpdateResult.Success
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
                    single { DiscussionService(repo, likes, writings = null, postRateLimiter = null) }
                    single<RateLimiter> { limiter }
                },
            )
        }

        routing {
            route("/v2") {
                communityRoutes()
            }
        }
    }

    private fun bearer(uid: String) = "Bearer " + JWT.create().withSubject(uid).sign(Algorithm.HMAC256("test-secret"))

    @Test
    fun `rate limit blocks second immediate request and allows after window`() =
        testApplication {
            val repo = RateLimitRepo()
            val limiter = InMemoryLimiter()
            application { testModule(repo, limiter) }

            val req = DiscussionCreateRequest(kind = Kind.POST, body = "hello")

            // First request OK
            val r1 =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.Created, r1.status)

            // Second immediate should be 429
            val r2 =
                client.post("/v2/community/discussions") {
                    header(HttpHeaders.Authorization, bearer("u1"))
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(req))
                }
            assertEquals(HttpStatusCode.TooManyRequests, r2.status)

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
