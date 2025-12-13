package com.sorenkai.server.routing

/**
 * Routes for creating and managing discussions (posts and comments).
 *
 * Secured by Auth. Exposes:
 * - POST /v1/discussions: create post/comment
 * - GET /v1/discussions: list posts or filter by writingSlug/parentId with cursor paging
 * - PATCH /v1/discussions/{id}: edit own post/comment
 * - DELETE /v1/discussions/{id}: delete own post/comment
 */
fun biteMe() {}
// fun Route.discussionsRoutes(service: DiscussionService = DiscussionService()) {
//
//    route("/v1/discussions") {
//        post {
//            val uid = call.extractUidOrUnauthorized() ?: return@post
//            val req = call.receive<com.sorenkai.api.dto.DiscussionCreateRequest>()
//            try {
//                val created = service.create(uid, servicelet(req), call.request.headers["X-Forwarded-For"])
//                call.respond(HttpStatusCode.Created, created)
//            } catch (e: DiscussionService.ValidationException) {
//                val code = com.sorenkai.ErrorCode.VALIDATION_FAILED
//                call.respond(
//                    code.http,
//                    com.sorenkai.server.errors.ApiErrorResponse(
//                        false,
//                        com.sorenkai.server.errors.ApiErrorBody(
//                            code.name,
//                            e.message ?: "Bad request",
//                        ),
//                    ),
//                )
//            } catch (e: DiscussionService.NotFoundException) {
//                val code = com.sorenkai.ErrorCode.NOT_FOUND
//                call.respond(
//                    code.http,
//                    com.sorenkai.server.errors.ApiErrorResponse(
//                        false,
//                        com.sorenkai.server.errors.ApiErrorBody(
//                            code.name,
//                            e.message ?: "Not found",
//                        ),
//                    ),
//                )
//            }
//        }
//
//        get {
//            val qp = call.request.queryParameters
//            val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: 20
//            val pageSize = pageSizeReq.coerceIn(1, 100)
//            val cursor = qp["cursor"]
//            val writingSlug = qp["writingSlug"]
//            val parentId = qp["parentId"]
//
//            val (items, next) =
//                when {
//                    !parentId.isNullOrBlank() -> service.listByParent(parentId, pageSize, cursor)
//                    !writingSlug.isNullOrBlank() -> service.listByWriting(writingSlug, pageSize, cursor)
//                    else -> service.listRoot(pageSize, cursor)
//                }
//            val body = Page(items = items, nextCursor = next?.encode())
//            call.respond(body)
//        }
//
//        patch("/{id}") {
//            val uid = call.extractUidOrUnauthorized() ?: return@patch
//            val id = call.parameters["id"]
//            if (id.isNullOrBlank()) {
//                return@patch call.respond(
//                    HttpStatusCode.BadRequest,
//                    com.sorenkai.server.errors.ApiErrorResponse(
//                        ok = false,
//                        error =
//                            com.sorenkai.server.errors.ApiErrorBody(
//                                code = com.sorenkai.ErrorCode.VALIDATION_FAILED.name,
//                                message = "Missing discussion id",
//                                details = mapOf("param" to "id"),
//                            ),
//                    ),
//                )
//            }
//            val payload = call.receive<Map<String, String>>()
//            val newBody = payload["body"] ?: ""
//            val ok =
//                try {
//                    service.editOwn(uid, id, newBody, call.request.headers["X-Forwarded-For"])
//                } catch (e: DiscussionService.ValidationException) {
//                    val code = com.sorenkai.ErrorCode.VALIDATION_FAILED
//                    return@patch call.respond(
//                        code.http,
//                        com.sorenkai.server.errors.ApiErrorResponse(
//                            false,
//                            com.sorenkai.server.errors.ApiErrorBody(
//                                code.name,
//                                e.message ?: "Bad request",
//                            ),
//                        ),
//                    )
//                }
//            if (ok) call.respond(mapOf("ok" to true)) else call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
//        }
//
//        delete("/{id}") {
//            val uid = call.extractUidOrUnauthorized() ?: return@delete
//            val id = call.parameters["id"]
//            if (id.isNullOrBlank()) {
//                return@delete call.respond(
//                    HttpStatusCode.BadRequest,
//                    com.sorenkai.server.errors.ApiErrorResponse(
//                        ok = false,
//                        error =
//                            com.sorenkai.server.errors.ApiErrorBody(
//                                code = com.sorenkai.ErrorCode.VALIDATION_FAILED.name,
//                                message = "Missing discussion id",
//                                details = mapOf("param" to "id"),
//                            ),
//                    ),
//                )
//            }
//            val ok = service.deleteOwn(uid, id, call.request.headers["X-Forwarded-For"])
//            if (ok) call.respond(mapOf("ok" to true)) else call.respond(HttpStatusCode.NotFound, mapOf("ok" to false))
//        }
//    }
// }
//
// private fun servicelet(req: com.sorenkai.api.dto.DiscussionCreateRequest): DiscussionService.CreateRequest =
//    DiscussionService.CreateRequest(
//        kind = req.kind,
//        body = req.body,
//        writingSlug = req.writingSlug,
//        parentId = req.parentId,
//        hashtags = req.hashtags,
//    )
