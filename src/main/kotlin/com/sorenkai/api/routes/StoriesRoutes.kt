package com.sorenkai.api.routes

import com.sorenkai.api.auth.extractUidOrUnauthorized
import com.sorenkai.api.dto.StoryCreateRequest
import com.sorenkai.api.dto.StoryResponse
import com.sorenkai.api.dto.paging.Page
import com.sorenkai.api.dto.paging.encode
import com.sorenkai.api.services.CommentService
import com.sorenkai.api.services.StoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.storiesRoutes(
    service: StoryService = StoryService(),
    commentService: CommentService = CommentService(),
    enforce: Boolean = com.sorenkai.api.security.AppCheckSecurity.isEnforced(),
    allowedOrigins: Set<String> = com.sorenkai.api.security.AppCheckSecurity.parseAllowedOriginsFromEnv().ifEmpty { setOf("http://localhost:8080") }
) {

    route("/v1/stories") {

        get {
            if (!com.sorenkai.api.security.AppCheckSecurity.enforce(call, enforce, allowedOrigins)) return@get
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

        post {
            val uid = call.extractUidOrUnauthorized() ?: return@post
            val req = call.receive<StoryCreateRequest>()

            val created = service.create(uid, req)

            call.respond(
                HttpStatusCode.Created,
                StoryResponse(
                    id = created.id,
                    title = created.title,
                    url = created.url,
                    summary = created.summary,
                    tags = created.tags,
                    createdAt = created.createdAt,
                    createdBy = created.createdBy
                )
            )
        }
    }

    // Story comments endpoints
    route("/v1/stories/{id}/comments") {
        get {
            val storyId = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.name,
                        message = "Missing story id",
                        details = mapOf("param" to "id")
                    )
                )
            )
            val qp = call.request.queryParameters
            val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: 20
            val pageSize = pageSizeReq.coerceIn(1, 100)
            val cursor = qp["cursor"]

            val (items, next) = commentService.page(storyId, pageSize, cursor)
            val body = Page(
                items = items.map {
                    com.sorenkai.api.dto.CommentResponse(
                        id = it.id,
                        storyId = it.storyId,
                        body = it.body,
                        createdAt = it.createdAt,
                        createdBy = it.createdBy
                    )
                },
                nextCursor = next?.encode()
            )
            call.respond(body)
        }

        post {
            val uid = call.extractUidOrUnauthorized() ?: return@post
            val storyId = call.parameters["id"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                com.sorenkai.api.dto.response.ApiErrorResponse(
                    ok = false,
                    error = com.sorenkai.api.dto.response.ApiErrorBody(
                        code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.name,
                        message = "Missing story id",
                        details = mapOf("param" to "id")
                    )
                )
            )
            val req = call.receive<com.sorenkai.api.dto.CommentCreateRequest>()

            val created = commentService.create(uid, storyId, req)

            call.respond(
                HttpStatusCode.Created,
                com.sorenkai.api.dto.CommentResponse(
                    id = created.id,
                    storyId = created.storyId,
                    body = created.body,
                    createdAt = created.createdAt,
                    createdBy = created.createdBy
                )
            )
        }
    }
}
