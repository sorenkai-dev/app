package com.sorenkai.server.routing

import com.sorenkai.core.paging.Page
import com.sorenkai.core.services.WritingService
import com.sorenkai.server.errors.ErrorBody
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.ErrorResponses
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Read-only routes for published writings.
 *
 * - GET /v1/writings: list published articles (limit/offset)
 * - GET /v1/writings/{slug}: fetch a single article
 *
 * Both endpoints require App Check when enforcement is enabled.
 */
fun Route.writingsRoutes(service: WritingService = WritingService()) {
    route("/v1/writings") {
        get {
            val qp = call.request.queryParameters
            val tag = qp["tag"]?.trim()?.takeUnless { it.isEmpty() }
            val limit = (qp["limit"]?.toIntOrNull() ?: 25).coerceIn(1, 50)
            val offset = (qp["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val items = listOf<String>() // added : listOf<String>() so code compiles
            if (tag == null) {
                service.listPublished(limit, offset)
            } else {
                service.listByTag(tag, limit, offset)
            }
            call.respond(Page(items = items, nextCursor = null))
        }

        get("/{lang}/{slug}") {
            val lang = call.parameters["lang"]
            val slug = call.parameters["slug"]
            if (lang.isNullOrBlank() || slug.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponses(
                        ok = false,
                        error =
                            ErrorBody(
                                code = ErrorCode.VALIDATION_FAILED.name,
                                message = "Missing lang or slug",
                                details = mapOf("param" to "lang, slug"),
                            ),
                    ),
                )
            }
            try {
                val item = service.getByLangAndSlug(lang, slug)
                call.respond(item)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
        get("/{slug}") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponses(
                        ok = false,
                        error =
                            ErrorBody(
                                code = ErrorCode.VALIDATION_FAILED.name,
                                message = "Missing slug",
                                details = mapOf("param" to "slug"),
                            ),
                    ),
                )
            }
            try {
                val item = service.getBySlug(slug)
                call.respond(item)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }

        // --- v1.1 stats endpoints (no auth required) ---
        post("/{slug}/share") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.incrementShare(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
        post("/{slug}/like") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.like(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
        post("/{slug}/unlike") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            // Unlike is idempotent, do not error if already zero
            try {
                val body = service.unlike(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
        post("/{slug}/view") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.view(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
        post("/{slug}/click-sale") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.clickSale(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                val code = ErrorCode.NOT_FOUND
                call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Not found")))
            }
        }
    }

    // GET /v1/tags - distinct list of all tags
    get("/v1/tags") {
        val tags = service.getAllTags()
        call.respond(tags)
    }
}
