package com.sorenkai.server.routing.writings

import com.sorenkai.common.util.Constants.HALF_PAGE_SIZE
import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.paging.Page
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.security.UserPrincipal
import com.sorenkai.writings.services.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Routes for writings.
 *
 * - Public: list, get, stats
 * - Authenticated: upload (crud), like/unlike
 */
fun Route.writingsRoutes(service: WritingService) {
    route("/writings") {
        authenticate("zitadel", optional = true) {
            get {
                val qp = call.request.queryParameters
                val slug = qp["slug"]?.trim()?.takeUnless { it.isEmpty() }
                val currentUid = call.principal<UserPrincipal>()?.user?.uid

                if (slug != null) {
                    try {
                        val item = service.getBySlug(slug, currentUid)
                        return@get call.respond(item)
                    } catch (e: WritingService.NotFoundException) {
                        return@get call.errorHandler(ErrorCode.NOT_FOUND, e.message)
                    }
                }

                val tag = qp["tag"]?.trim()?.takeUnless { it.isEmpty() }
                val lang = qp["lang"]?.trim()?.takeUnless { it.isEmpty() }
                val limit = (qp["limit"]?.toIntOrNull() ?: HALF_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
                val offset = (qp["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
                val items =
                    if (tag == null) {
                        service.listPublished(limit, offset, currentUid, lang)
                    } else {
                        service.listByTag(tag, limit, offset, currentUid, lang)
                    }
                call.respond(Page(items = items, nextCursor = null))
            }

            get("/{id}") {
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    return@get call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing id",
                        mapOf("param" to "id"),
                    )
                }
                try {
                    val currentUid = call.principal<UserPrincipal>()?.user?.uid
                    val item = service.getById(id, currentUid)
                    call.respond(item)
                } catch (e: WritingService.NotFoundException) {
                    call.errorHandler(ErrorCode.NOT_FOUND, e.message)
                }
            }
        }

        // --- v1.1 stats endpoints (no auth required) ---
        post("/{id}/share") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.incrementShare(id)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{id}/view") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.view(id)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{id}/click-sale") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.clickSale(id)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(
                    ErrorCode.NOT_FOUND,
                    e.message,
                )
            }
        }

        authenticate("zitadel") {
            writingCrud(service)
            writingLikes(service)
        }
    }

    // GET /tags - distinct list of all tags
    get("/tags") {
        val tags = service.getAllTags()
        call.respond(tags)
    }
}
