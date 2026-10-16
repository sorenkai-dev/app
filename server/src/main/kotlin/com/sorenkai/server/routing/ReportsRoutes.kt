package com.sorenkai.server.routing

import com.sorenkai.common.util.Constants.ADMIN_REPORT_DEFAULT_LIMIT
import com.sorenkai.common.util.Constants.ADMIN_REPORT_MAX_LIMIT
import com.sorenkai.core.dto.reports.ReportCreateRequest
import com.sorenkai.core.model.auth.Role
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import com.sorenkai.reports.services.ReportService
import com.sorenkai.server.errors.ErrorBody
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.ErrorResponses
import com.sorenkai.server.security.UserPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val reportsLog = LoggerFactory.getLogger("ReportsRoutesV2")

fun Route.reportsRoutes() {
    val reports by inject<ReportService>()

    route("/reports") {
        // Auth required for creating report (uid from principal)
        authenticate("zitadel") {
            post {
                val principal = call.principal<UserPrincipal>()
                val uid = principal?.user?.uid
                if (uid.isNullOrBlank()) {
                    val code = ErrorCode.UNAUTHORIZED
                    return@post call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, "Unauthorized")))
                }
                val req = call.receive<ReportCreateRequest>()
                try {
                    val created = reports.create(uid, req)
                    call.respond(HttpStatusCode.Created, created)
                } catch (e: ReportService.ValidationException) {
                    val code = ErrorCode.VALIDATION_FAILED
                    reportsLog.warn(
                        "Report create validation failed uid={} targetType={} targetId={} : {}",
                        uid,
                        req.targetType,
                        req.targetId,
                        e.message,
                    )
                    call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Bad request")))
                } catch (e: ReportService.ConflictException) {
                    val code = ErrorCode.CONFLICT
                    reportsLog.info("Duplicate report uid={} targetType={} targetId={}", uid, req.targetType, req.targetId)
                    call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Conflict")))
                }
            }
        }
    }
}

fun Route.adminReportsRoutesV2() {
    val reports by inject<ReportService>()

    authenticate("zitadel") {
        route("/admin/reports") {
            // Listing for admin/mod tools
            get {
                val p = call.principal<UserPrincipal>()
                val roles = p?.user?.roles ?: emptySet()
                val adminOk = roles.contains(Role.ADMIN) || roles.contains(Role.MOD)
                if (!adminOk) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Not found")),
                    )
                }
                val qp = call.request.queryParameters
                val statusParam =
                    qp
                        .getAll("status")
                        ?.mapNotNull { runCatching { ReportStatus.valueOf(it) }.getOrNull() }
                        ?.toSet()
                val targetType =
                    qp["targetType"]
                        ?.let { runCatching { TargetType.valueOf(it) }.getOrNull() }
                val targetId = qp["targetId"]
                val limit =
                    (
                        qp["limit"]?.toIntOrNull()
                            ?: ADMIN_REPORT_DEFAULT_LIMIT
                    ).coerceIn(1, ADMIN_REPORT_MAX_LIMIT)
                val offset = (qp["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
                val items = reports.listAdmin(statusParam, targetType, targetId, limit, offset)
                call.respond(items)
            }

            // Update status
            patch("/{id}/status") {
                val p = call.principal<UserPrincipal>()
                val roles = p?.user?.roles ?: emptySet()
                val adminOk = roles.contains(Role.ADMIN) || roles.contains(Role.MOD)
                val actorUid = p?.user?.uid
                if (!adminOk) {
                    return@patch call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Not found")),
                    )
                }
                val id = call.parameters["id"]
                val payload = call.receive<Map<String, String>>()
                val statusRaw = payload["status"]
                if (id.isNullOrBlank() || statusRaw.isNullOrBlank()) {
                    val code = ErrorCode.VALIDATION_FAILED
                    return@patch call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, "Missing id or status")))
                }
                val newStatus = runCatching { ReportStatus.valueOf(statusRaw) }.getOrNull()
                if (newStatus == null) {
                    val code = ErrorCode.VALIDATION_FAILED
                    return@patch call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, "Invalid status")))
                }
                try {
                    val ok = reports.updateStatus(actorUid ?: "", id, newStatus)
                    if (ok) {
                        call.respond(mapOf("ok" to true))
                    } else {
                        call.respond(
                            ErrorCode.NOT_FOUND.http,
                            ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Report not found")),
                        )
                    }
                } catch (e: ReportService.ConflictException) {
                    val code = ErrorCode.CONFLICT
                    reportsLog.info("Invalid transition reportId={} actorUid={} -> {}", id, actorUid, newStatus)
                    call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Conflict")))
                } catch (e: ReportService.ValidationException) {
                    val code = ErrorCode.VALIDATION_FAILED
                    reportsLog.warn("Status update validation failed reportId={} actorUid={} : {}", id, actorUid, e.message)
                    call.respond(code.http, ErrorResponses(false, ErrorBody(code.name, e.message ?: "Bad request")))
                }
            }
        }
    }
}
