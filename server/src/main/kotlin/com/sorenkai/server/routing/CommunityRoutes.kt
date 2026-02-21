package com.sorenkai.server.routing

import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.paging.Page
import com.sorenkai.core.paging.encode
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.core.repo.DiscussionOrder
import com.sorenkai.server.routing.discussions.discussionCrud
import com.sorenkai.server.routing.discussions.discussionLikes
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import kotlin.text.isNullOrBlank
import kotlin.text.toIntOrNull

fun Route.communityRoutes() {
    val discussions by inject<DiscussionService>()
    val rateLimiter by inject<RateLimiter>()
    val log = LoggerFactory.getLogger(DiscussionService::class.java)

    route("/community/discussions") {
        authenticate("zitadel", optional = true) {
            get {
                val start = System.currentTimeMillis() // timer start
                val qp = call.request.queryParameters
                val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: MAX_PAGE_SIZE
                val pageSize = pageSizeReq.coerceIn(1, MAX_PAGE_SIZE)
                val cursor = qp["cursor"]
                val writingId = qp["writingId"]
                val parentId = qp["parentId"]
                val lang = qp["lang"]
                val order =
                    when (qp["order"]?.lowercase()) {
                        "oldest" -> DiscussionOrder.OLDEST
                        else -> DiscussionOrder.NEWEST
                    }

                val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
                val isModerator =
                    call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.hasAnyRole(
                        com.sorenkai.core.model.auth.Role.MOD,
                        com.sorenkai.core.model.auth.Role.ADMIN,
                    ) ?: false

                val (items, next) =
                    when {
                        !parentId.isNullOrBlank() -> discussions.listByParent(parentId, pageSize, cursor, currentUid, isModerator)
                        !writingId.isNullOrBlank() ->
                            discussions.listByWriting(
                                writingId,
                                pageSize,
                                cursor,
                                currentUid,
                                isModerator,
                                lang,
                                order,
                            )

                        else -> discussions.listRoot(pageSize, cursor, currentUid, isModerator, lang, order)
                    }
                val body = Page(items = items, nextCursor = next?.encode())

                log.info(
                    "[TIMING] GET /community/discussions completed in ${System.currentTimeMillis() - start}ms",
                ) // timer end and report

                call.respond(body)
            }
        }
        authenticate("zitadel") {
            discussionCrud(discussions, rateLimiter)
            discussionLikes(discussions)
        }
    }
}
