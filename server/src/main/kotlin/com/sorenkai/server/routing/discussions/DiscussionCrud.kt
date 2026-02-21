package com.sorenkai.server.routing.discussions

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.dto.discussions.DiscussionCreateRequest
import com.sorenkai.core.rate.RateLimiter
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.util.withUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
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

    patch("/{id}") {
        val id = call.parameters["id"] ?: return@patch
        withUser { uid ->
            val payload = call.receive<Map<String, String>>()
            val newBody = payload["body"] ?: ""
            try {
                val ip = call.request.headers["X-Forwarded-For"]
                val updated = discussions.editOwn(uid, id, newBody, ip)
                if (updated != null) {
                    call.respond(HttpStatusCode.OK, updated)
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
            val updated = discussions.deleteOwn(uid, id, ip)
            if (updated != null) {
                call.respond(HttpStatusCode.OK, updated)
            } else {
                call.errorHandler(ErrorCode.NOT_FOUND)
            }
        }
    }

    post("/{id}/restore") {
        val id = call.parameters["id"] ?: return@post
        withUser { uid ->
            try {
                val updated = discussions.restoreOwn(uid, id)
                if (updated != null) {
                    call.respond(HttpStatusCode.OK, updated)
                } else {
                    call.errorHandler(ErrorCode.NOT_FOUND)
                }
            } catch (e: DiscussionService.ValidationException) {
                communityLog.warn("Validation failed restoring discussion id={} uid={} : {}", id, uid, e.message)
                call.errorHandler(ErrorCode.VALIDATION_FAILED, e.message)
            }
        }
    }
}
