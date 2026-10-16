package com.sorenkai.api.routing.writings

import com.sorenkai.api.util.withUser
import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.service.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.writingCrud(service: WritingService) {
    post {
        withUser { _ ->
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




