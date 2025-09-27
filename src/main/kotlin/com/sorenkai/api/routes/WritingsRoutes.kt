package com.sorenkai.api.routes

import com.sorenkai.api.dto.StoryResponse
import com.sorenkai.api.dto.paging.Page
import com.sorenkai.api.dto.paging.encode
import com.sorenkai.api.security.AppCheckSecurity
import com.sorenkai.api.services.StoryService
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.application.call
import io.ktor.server.response.respond

fun Route.writingsRoutes(
    service: StoryService = StoryService(),
    enforce: Boolean = AppCheckSecurity.isEnforced(),
    allowedOrigins: Set<String> = AppCheckSecurity.parseAllowedOriginsFromEnv().ifEmpty { setOf("http://localhost:8080") }
) {
    route("/v1/writings/stories") {
        get {
            if (!AppCheckSecurity.enforce(call, enforce, allowedOrigins)) return@get

            val qp = call.request.queryParameters
            val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: 20
            val pageSize = pageSizeReq.coerceIn(1, 100)
            val cursor = qp["cursor"]

            val (items, next) = service.recentPage(pageSize, cursor)
            val body = Page(
                items = items.map {
                    StoryResponse(
                        id = it.id,
                        title = it.title,
                        url = it.url,
                        summary = it.summary,
                        tags = it.tags,
                        createdAt = it.createdAt,
                        createdBy = it.createdBy
                    )
                },
                nextCursor = next?.encode()
            )
            call.respond(body)
        }
    }
}
