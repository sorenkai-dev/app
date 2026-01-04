package com.sorenkai.server.routing.discussions

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.util.withUser
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

fun Route.discussionLikes(discussions: DiscussionService) {
    val communityLog = LoggerFactory.getLogger("CommunityRoutes")

    post("/{id}/like") {
        val id = call.parameters["id"] ?: return@post
        withUser { uid ->
            try {
                val updated = discussions.like(uid, id)
                call.respond(updated)
            } catch (e: DiscussionService.NotFoundException) {
                communityLog.info("Like discussion not found id={} uid={} ", id, uid)
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            } catch (e: DiscussionService.ValidationException) {
                communityLog.warn("Validation failed liking discussion id={} uid={} : {}", id, uid, e.message)
                call.errorHandler(
                    ErrorCode.VALIDATION_FAILED,
                    e.message,
                )
            }
        }
    }

    delete("/{id}/like") {
        val id = call.parameters["id"] ?: return@delete
        withUser { uid ->
            try {
                val updated = discussions.unlike(uid, id)
                call.respond(updated)
            } catch (e: DiscussionService.NotFoundException) {
                communityLog.info("Unlike discussion not found id={} uid={} ", id, uid)
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            } catch (e: DiscussionService.ValidationException) {
                val code = ErrorCode.VALIDATION_FAILED
                communityLog.warn("Validation failed unliking discussion id={} uid={} : {}", id, uid, e.message)
                call.errorHandler(ErrorCode.VALIDATION_FAILED, e.message)
            }
        }
    }
}
