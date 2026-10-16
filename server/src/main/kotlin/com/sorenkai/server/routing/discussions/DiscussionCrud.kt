package com.sorenkai.server.routing.discussions

import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.paging.Page
import com.sorenkai.core.paging.encode
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.util.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

fun Route.discussionCrud(
    discussions: DiscussionService,
    rateLimiter: RateLimiter,
) {
    val communityLog = LoggerFactory.getLogger("CommunityRoutes")
    post {
        withUser { uid ->
            val req = call.receive<DiscussionCreateRequest>()
            // Rate limiting: 1 action per 10s per user per action type
            val action =
                when (req.kind) {
                    Kind.POST -> RateLimiter.Companion.Action.DISCUSSION_POST
                    Kind.COMMENT -> RateLimiter.Companion.Action.DISCUSSION_COMMENT
                }
            val allowed = rateLimiter.tryConsume(uid, action)
            if (!allowed) {
                communityLog.info("Rate limited create discussion uid={} action={}", uid, action)
                call.errorHandler(ErrorCode.RATE_LIMITED, "Too Many Requests. Try again shortly.")
                return@withUser
            }
            try {
                val ip = call.request.headers["X-Forwarded-For"]
                val created = discussions.create(uid, req, ip)
                call.respond(HttpStatusCode.Created, created)
            } catch (e: DiscussionService.ValidationException) {
                communityLog.warn("Validation failed creating discussion by uid={} : {}", uid, e.message)
                call.errorHandler(ErrorCode.VALIDATION_FAILED, e.message)
            } catch (e: DiscussionService.NotFoundException) {
                communityLog.info("Create discussion referenced entity not found by uid={} : {}", uid, e.message)
                call.errorHandler(ErrorCode.NOT_FOUND)
            }
        }
    }

    get {
        val qp = call.request.queryParameters
        val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: MAX_PAGE_SIZE
        val pageSize = pageSizeReq.coerceIn(1, MAX_PAGE_SIZE)
        val cursor = qp["cursor"]
        val writingId = qp["writingId"]
        val parentId = qp["parentId"]
        val lang = qp["lang"]

        val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid

        val (items, next) =
            when {
                !parentId.isNullOrBlank() -> discussions.listByParent(parentId, pageSize, cursor, currentUid)
                !writingId.isNullOrBlank() -> discussions.listByWriting(writingId, pageSize, cursor, currentUid, lang)
                else -> discussions.listRoot(pageSize, cursor, currentUid, lang)
            }
        val body = Page(items = items, nextCursor = next?.encode())
        call.respond(body)
    }

    patch("/{id}") {
        val id = call.parameters["id"] ?: return@patch
        withUser { uid ->
            val payload = call.receive<Map<String, String>>()
            val newBody = payload["body"] ?: ""
            try {
                val ip = call.request.headers["X-Forwarded-For"]
                val ok = discussions.editOwn(uid, id, newBody, ip)
                if (ok) {
                    call.respond(mapOf("ok" to true))
                } else {
                    call.errorHandler(ErrorCode.NOT_FOUND, "Discussion not found")
                }
            } catch (e: DiscussionService.ValidationException) {
                communityLog.warn("Validation failed editing discussion id={} uid={} : {}", id, uid, e.message)
                call.errorHandler(ErrorCode.VALIDATION_FAILED, e.message)
            }
        }
    }

    delete("/{id}") {
        val id = call.parameters["id"] ?: return@delete
        withUser { uid ->
            val ip = call.request.headers["X-Forwarded-For"]
            val ok = discussions.deleteOwn(uid, id, ip)
            if (ok) {
                call.respond(mapOf("ok" to true))
            } else {
                call.errorHandler(ErrorCode.NOT_FOUND)
            }
        }
    }
}
