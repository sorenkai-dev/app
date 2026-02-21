package com.sorenkai.server.routing.writings

import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.util.withUser
import com.sorenkai.writings.services.WritingService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

fun Route.writingLikes(service: WritingService) {
    val log = LoggerFactory.getLogger("WritingRoutes")

    post("/{id}/like") {
        val id = call.parameters["id"] ?: return@post
        withUser { uid ->
            try {
                val updated = service.like(id, uid)
                call.respond(updated)
            } catch (e: WritingService.NotFoundException) {
                log.info("Like writing not found id={} uid={} ", id, uid)
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
    }

    delete("/{id}/like") {
        val id = call.parameters["id"] ?: return@delete
        withUser { uid ->
            try {
                val updated = service.unlike(id, uid)
                call.respond(updated)
            } catch (e: WritingService.NotFoundException) {
                log.info("Unlike writing not found id={} uid={} ", id, uid)
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
    }
}
