package com.sorenkai.server.configuration

import com.sorenkai.server.routing.adminAggregationRoutesV2
import com.sorenkai.server.routing.adminDiscussionRoutesV2
import com.sorenkai.server.routing.adminReportsRoutesV2
import com.sorenkai.server.routing.adminWritingsRoutesV2
import com.sorenkai.server.routing.communityRoutes
import com.sorenkai.server.routing.reportsRoutes
import com.sorenkai.server.routing.systemRoutes
import com.sorenkai.server.routing.writingsRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    routing {
        systemRoutes()
        route("/v2") {
            // v2 community and admin routes
            communityRoutes()
            adminDiscussionRoutesV2()
            adminReportsRoutesV2()
            adminWritingsRoutesV2()
            adminAggregationRoutesV2()
            // reports
            reportsRoutes()
            // public writings
            val writingService by inject<com.sorenkai.writings.services.WritingService>()
            writingsRoutes(writingService)
        }
    }
}
