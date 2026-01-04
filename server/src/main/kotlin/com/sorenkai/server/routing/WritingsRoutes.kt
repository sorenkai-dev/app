package com.sorenkai.server.routing

import com.sorenkai.common.util.Constants.HALF_PAGE_SIZE
import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.paging.Page
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.writings.services.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
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
fun Route.writingsRoutes(service: WritingService) {
    route("/writings") {
        get {
            val qp = call.request.queryParameters
            val tag = qp["tag"]?.trim()?.takeUnless { it.isEmpty() }
            val limit = (qp["limit"]?.toIntOrNull() ?: HALF_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
            val offset = (qp["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
            val items =
                if (tag == null) {
                    service.listPublished(limit, offset, currentUid)
                } else {
                    service.listByTag(tag, limit, offset, currentUid)
                }
            call.respond(Page(items = items, nextCursor = null))
        }

        get("/{lang}/{slug}") {
            val lang = call.parameters["lang"]
            val slug = call.parameters["slug"]
            if (lang.isNullOrBlank() || slug.isNullOrBlank()) {
                return@get call.errorHandler(
                    ErrorCode.VALIDATION_FAILED,
                    "Missing slug",
                    mapOf("param" to "slug"),
                )
            }
            try {
                val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
                val item = service.getByLangAndSlug(lang, slug, currentUid)
                call.respond(item)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        get("/{slug}") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) {
                return@get call.errorHandler(
                    ErrorCode.VALIDATION_FAILED,
                    "Missing slug",
                    mapOf("param" to "slug"),
                )
            }
            try {
                val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
                val item = service.getBySlug(slug, currentUid)
                call.respond(item)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
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
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{slug}/like") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
            if (currentUid == null) return@post call.errorHandler(ErrorCode.VALIDATION_FAILED, "Auth required")
            try {
                val body = service.like(slug, currentUid)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{slug}/unlike") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            val currentUid = call.principal<com.sorenkai.server.security.UserPrincipal>()?.user?.uid
            if (currentUid == null) return@post call.errorHandler(ErrorCode.VALIDATION_FAILED, "Auth required")
            // Unlike is idempotent, do not error if already zero
            try {
                val body = service.unlike(slug, currentUid)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{slug}/view") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.view(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(ErrorCode.NOT_FOUND, e.message)
            }
        }
        post("/{slug}/click-sale") {
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) return@post call.respond(HttpStatusCode.BadRequest)
            try {
                val body = service.clickSale(slug)
                call.respond(HttpStatusCode.OK, body)
            } catch (e: WritingService.NotFoundException) {
                call.errorHandler(
                    ErrorCode.NOT_FOUND,
                    e.message,
                )
            }
        }
    }

    // GET /tags - distinct list of all tags
    get("/tags") {
        val tags = service.getAllTags()
        call.respond(tags)
    }
}
