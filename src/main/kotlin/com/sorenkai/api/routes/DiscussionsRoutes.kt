package com.sorenkai.api.routes

import com.sorenkai.api.auth.extractUidOrUnauthorized
import com.sorenkai.api.dto.paging.Page
import com.sorenkai.api.dto.paging.encode
import com.sorenkai.api.services.DiscussionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.discussionsRoutes(service: DiscussionService = DiscussionService()) {
    route("/v1/discussions") {
        post {
            val uid = call.extractUidOrUnauthorized() ?: return@post
            val req = call.receive<com.sorenkai.api.dto.DiscussionCreateRequest>()
            try {
                val created = service.create(uid, servicelet(req), call.request.headers["X-Forwarded-For"])
                call.respond(HttpStatusCode.Created, created)
            } catch (e: com.sorenkai.api.services.DiscussionService.ValidationException) {
                val code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED
                call.respond(code.http, com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Bad request")))
            } catch (e: com.sorenkai.api.services.DiscussionService.NotFoundException) {
                val code = com.sorenkai.api.errors.ErrorCode.NOT_FOUND
                call.respond(code.http, com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Not found")))
            }
        }

        get {
            val qp = call.request.queryParameters
            val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: 20
            val pageSize = pageSizeReq.coerceIn(1, 100)
            val cursor = qp["cursor"]
            val storyId = qp["storyId"]
            val parentId = qp["parentId"]

            val (items, next) = when {
                !parentId.isNullOrBlank() -> service.listByParent(parentId, pageSize, cursor)
                !storyId.isNullOrBlank() -> service.listByStory(storyId, pageSize, cursor)
                else -> service.listRoot(pageSize, cursor)
            }
            val body = Page(items = items, nextCursor = next?.encode())
            call.respond(body)
        }

        delete("/{id}") {
            val uid = call.extractUidOrUnauthorized() ?: return@delete
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    com.sorenkai.api.dto.response.ApiErrorResponse(
                        ok = false,
                        error = com.sorenkai.api.dto.response.ApiErrorBody(
                            code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.name,
                            message = "Missing discussion id",
                            details = mapOf("param" to "id")
                        )
                    )
                )
            }
            val ok = service.deleteOwn(uid, id, call.request.headers["X-Forwarded-For"])
            if (ok) call.respond(mapOf("ok" to true)) else call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
        }
    }
}

private fun servicelet(req: com.sorenkai.api.dto.DiscussionCreateRequest): com.sorenkai.api.services.DiscussionService.CreateRequest =
    com.sorenkai.api.services.DiscussionService.CreateRequest(
        kind = req.kind,
        body = req.body,
        storyId = req.storyId,
        parentId = req.parentId,
        hashtags = req.hashtags
    )
