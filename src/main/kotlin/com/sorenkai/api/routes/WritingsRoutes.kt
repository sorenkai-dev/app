package com.sorenkai.api.routes

import com.sorenkai.api.dto.paging.Page
import com.sorenkai.api.security.AppCheckSecurity
import com.sorenkai.api.security.enforceAppCheck
import com.sorenkai.api.services.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Read-only routes for published writings.
 *
 * - GET /v1/writings: list published articles (limit/offset)
 * - GET /v1/writings/{slug}: fetch a single article
 *
 * Both endpoints require App Check when enforcement is enabled.
 */
fun Route.writingsRoutes(
    service: WritingService = WritingService(),
    enforce: Boolean = AppCheckSecurity.isEnforced(),
    allowedOrigins: Set<String> = AppCheckSecurity.parseAllowedOriginsFromEnv().ifEmpty { setOf("http://localhost:8080") }
) {
    route("/v1/writings") {
        get {
            if (!call.enforceAppCheck(enforce, allowedOrigins)) return@get
            val qp = call.request.queryParameters
            val limit = (qp["limit"]?.toIntOrNull() ?: 12).coerceIn(1, 50)
            val offset = (qp["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val items = service.listPublished(limit, offset)
            call.respond(Page(items = items, nextCursor = null))
        }
        get("/{slug}") {
            if (!call.enforceAppCheck(enforce, allowedOrigins)) return@get
            val slug = call.parameters["slug"]
            if (slug.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    com.sorenkai.api.dto.response.ApiErrorResponse(
                        ok = false,
                        error = com.sorenkai.api.dto.response.ApiErrorBody(
                            code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.name,
                            message = "Missing slug",
                            details = mapOf("param" to "slug")
                        )
                    )
                )
            }
            try {
                val item = service.getBySlug(slug)
                call.respond(item)
            } catch (e: WritingService.NotFoundException) {
                val code = com.sorenkai.api.errors.ErrorCode.NOT_FOUND
                call.respond(code.http, com.sorenkai.api.dto.response.ApiErrorResponse(false, com.sorenkai.api.dto.response.ApiErrorBody(code.name, e.message ?: "Not found")))
            }
        }
    }
}
