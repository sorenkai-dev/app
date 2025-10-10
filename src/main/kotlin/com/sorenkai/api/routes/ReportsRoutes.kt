package com.sorenkai.api.routes

import com.sorenkai.api.auth.extractUidOrUnauthorized
import com.sorenkai.api.auth.requireRole
import com.sorenkai.api.dto.ReportCreateRequest
import com.sorenkai.api.dto.ReportResponse
import com.sorenkai.api.dto.paging.Page
import com.sorenkai.api.dto.paging.encode
import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.model.Role
import com.sorenkai.api.services.ReportService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Routes for abuse report submission and moderation.
 *
 * - POST /v1/reports (auth required): submit a report
 * - GET /v1/mod/reports (MOD/ADMIN): list reports with paging
 * - POST /v1/mod/reports/{id}/close (MOD/ADMIN): close a report
 */
fun Route.reportsRoutes(service: ReportService = ReportService()) {

    // Public reporting endpoint (auth required)
    post("/v1/reports") {
        // Enforce App Check + Origin allowlist
        if (!com.sorenkai.api.security.AppCheckSecurity.enforce(
                call,
                com.sorenkai.api.security.AppCheckSecurity.isEnforced(),
                com.sorenkai.api.security.AppCheckSecurity.parseAllowedOriginsFromEnv()
            )) return@post

        val uid = call.extractUidOrUnauthorized() ?: return@post
        val req = call.receive<ReportCreateRequest>()
        val created = service.create(uid, req)
        call.respond(
            HttpStatusCode.Created,
            ReportResponse(
                id = created.id,
                targetType = created.targetType,
                targetId = created.targetId,
                reason = created.reason,
                createdAt = created.createdAt,
                createdBy = created.createdBy,
                status = created.status
            )
        )
    }

    // Moderator-only endpoints
    route("/v1/mod/reports") {
        get {
            // Enforce App Check + Origin allowlist
            if (!com.sorenkai.api.security.AppCheckSecurity.enforce(
                    call,
                    com.sorenkai.api.security.AppCheckSecurity.isEnforced(),
                    com.sorenkai.api.security.AppCheckSecurity.parseAllowedOriginsFromEnv()
                )) return@get
            call.requireRole(Role.MOD, Role.ADMIN) ?: return@get
            val qp = call.request.queryParameters
            val statusParam = qp["status"]?.lowercase() ?: "open"
            val status = when (statusParam) {
                "open" -> ReportStatus.OPEN
                "closed" -> ReportStatus.CLOSED
                else -> ReportStatus.OPEN
            }
            val pageSizeReq = qp["pageSize"]?.toIntOrNull() ?: 20
            val pageSize = pageSizeReq.coerceIn(1, 100)
            val cursor = qp["cursor"]
            val (items, next) = service.page(status, pageSize, cursor)
            val body = Page(
                items = items.map {
                    ReportResponse(
                        id = it.id,
                        targetType = it.targetType,
                        targetId = it.targetId,
                        reason = it.reason,
                        createdAt = it.createdAt,
                        createdBy = it.createdBy,
                        status = it.status
                    )
                },
                nextCursor = next?.encode()
            )
            call.respond(body)
        }

        post("/{id}/close") {
            // Enforce App Check + Origin allowlist
            if (!com.sorenkai.api.security.AppCheckSecurity.enforce(
                    call,
                    com.sorenkai.api.security.AppCheckSecurity.isEnforced(),
                    com.sorenkai.api.security.AppCheckSecurity.parseAllowedOriginsFromEnv()
                )) return@post
            call.requireRole(Role.MOD, Role.ADMIN) ?: return@post
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    com.sorenkai.api.dto.response.ApiErrorResponse(
                        ok = false,
                        error = com.sorenkai.api.dto.response.ApiErrorBody(
                            code = com.sorenkai.api.errors.ErrorCode.VALIDATION_FAILED.name,
                            message = "Missing report id",
                            details = mapOf("param" to "id")
                        )
                    )
                )
            }
            val ok = service.close(id)
            if (ok) {
                call.respond(mapOf("ok" to true))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "NOT_FOUND"))
            }
        }
    }
}
