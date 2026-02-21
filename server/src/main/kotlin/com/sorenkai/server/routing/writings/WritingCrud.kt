package com.sorenkai.server.routing.writings

import com.sorenkai.core.model.writings.Writing
import com.sorenkai.server.util.withUser
import com.sorenkai.writings.services.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.writingCrud(service: WritingService) {
    post {
        withUser { uid ->
            val doc = call.receive<Writing>()
            // Ensure author matches or something? Currently WritingService doesn't check auth for createAdmin
            val created = service.createAdmin(doc)
            if (created) {
                call.respond(HttpStatusCode.Created)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
