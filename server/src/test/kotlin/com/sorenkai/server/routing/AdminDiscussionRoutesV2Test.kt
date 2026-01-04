package com.sorenkai.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.repo.IDiscussionRepository
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

@kotlin.time.ExperimentalTime
private class AdminInMemoryRepo :
    IDiscussionRepository,
    com.sorenkai.core.repo.ILikeRepository {
    private val store = mutableMapOf<String, Discussion>()

    override suspend fun insert(discussion: Discussion): Discussion {
        store[discussion.id] = discussion
        return discussion
    }

    override suspend fun findById(id: String): Discussion? = store[id]

    override suspend fun listPosts(
        writingSlug: String?,
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
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(status = status, moderatedBy = moderatedBy, moderatedAt = moderatedAt, reason = reason)
        return true
    }

    override suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ) = com.sorenkai.core.model.common.UpdateResult.Success

    override suspend fun setLocked(
        id: String,
        locked: Boolean,
    ) = com.sorenkai.core.model.common.UpdateResult.Success

    override suspend fun like(
        targetId: String,
        userId: UID,
    ) {}

    override suspend fun unlike(
        targetId: String,
        userId: UID,
    ) {}

    override suspend fun countLikes(targetIds: List<String>): Map<String, Long> = emptyMap()

    override suspend fun getLikedTargets(
        targetIds: List<String>,
        userId: UID,
    ): Set<String> = emptySet()
}

@kotlin.time.ExperimentalTime
class AdminDiscussionRoutesV2Test {
    private fun Application.testModule(repo: IDiscussionRepository) {
        configureSerialization()

        install(Authentication) {
            jwt("zitadel") {
                verifier(JWT.require(Algorithm.HMAC256("test-secret")).build())
                validate { cred ->
                    val roles = if (cred.subject == "mod-1") setOf(com.sorenkai.core.model.auth.Role.MOD) else emptySet()
                    com.sorenkai.server.security
                        .UserPrincipal(
                            com.sorenkai.core.model.auth
                                .AuthUser(cred.subject ?: "test", roles),
                        )
                }
            }
        }

        install(Koin) {
            modules(
                module {
                    val repoAsLikes = repo as com.sorenkai.core.repo.ILikeRepository
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
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt =
                            kotlin.time.Clock.System
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
            assertTrue(resp.bodyAsText().contains("\"ok\":true"))
        }

    @Test
    fun `patch status invalid status`() =
        testApplication {
            val repo = AdminInMemoryRepo()
            application { testModule(repo) }
            val d =
                repo.insert(
                    Discussion(
                        kind = Kind.POST,
                        userId = "u1",
                        body = "x",
                        createdAt =
                            kotlin.time.Clock.System
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
