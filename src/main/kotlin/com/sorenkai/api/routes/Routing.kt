package com.sorenkai.api.routes

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

suspend fun initRepos() {
    com.sorenkai.api.repo.ReportRepository().ensureIndexes()
    com.sorenkai.api.repo.DiscussionRepository().ensureIndexes()
    com.sorenkai.api.repo.WritingRepository().ensureIndexes()
}

fun Application.configureRouting() {

    routing {
        reportsRoutes()
        adminRoutes()
        discussionsRoutes()
        writingsRoutes()
        healthRoutes()
        docsRoutes()
    }
}
