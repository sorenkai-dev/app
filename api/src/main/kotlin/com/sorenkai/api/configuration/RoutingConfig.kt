package com.sorenkai.api.configuration


import com.sorenkai.api.routing.adminWritingsRoutesV2
import com.sorenkai.api.routing.systemRoutes
import com.sorenkai.api.routing.writings.writingsRoutes
import com.sorenkai.writings.service.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    routing {
        options("{...}") {
            call.respond(HttpStatusCode.OK)
        }
        systemRoutes()
        route("/v2") {
            adminWritingsRoutesV2()
            // public writings
            val writingService by inject<WritingService>()
            writingsRoutes(writingService)
        }
    }
}




