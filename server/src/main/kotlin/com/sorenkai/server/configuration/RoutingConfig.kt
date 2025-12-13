package com.sorenkai.server.configuration

import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        route("/v2") {
//            healthRoutes()
//            adminRoutes()
//            discussionRoutes()
//            reportRoutes()
//            writingRoutes()
        }
    }
}
