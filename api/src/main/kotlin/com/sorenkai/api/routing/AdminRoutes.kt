package com.sorenkai.api.routing

import com.sorenkai.api.dto.admin.AdminOk
import com.sorenkai.api.dto.admin.AnalyticsStub
import com.sorenkai.api.dto.admin.DashboardSummary
import com.sorenkai.api.dto.admin.HealthOverview
import com.sorenkai.common.model.auth.Role
import com.sorenkai.common.model.response.UpdateResult
import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.model.WritingPublisher
import com.sorenkai.writings.model.WritingsStatus
import com.sorenkai.api.errors.ErrorBody
import com.sorenkai.api.errors.ErrorCode
import com.sorenkai.api.errors.ErrorResponses
import com.sorenkai.api.errors.errorHandler
import com.sorenkai.api.security.UserPrincipal
import com.sorenkai.writings.service.DocxToMarkdownService
import com.sorenkai.writings.service.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val adminV2Log = LoggerFactory.getLogger("AdminRoutesV2")

// -----------------------------
// Admin writings routes and admin-only endpoints
// -----------------------------
fun Route.adminWritingsRoutesV2() {
    val writings by inject<WritingService>()
    val docxService = DocxToMarkdownService()

    authenticate("zitadel") {
        route("/admin/writings") {
            post("/preview") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@post call.errorHandler(ErrorCode.NOT_FOUND)
                }

                val multipart = call.receiveMultipart()
                var content: String? = null
                var title: String? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileName = part.originalFileName ?: ""
                        if (!fileName.endsWith(".docx", ignoreCase = true)) {
                            part.dispose()
                            return@forEachPart
                        }

                        val input = part.provider().toInputStream()
                        content = docxService.convertToMarkdown(input)
                        // Simple title inference from filename if not already set
                        if (title == null) {
                            title = fileName.removeSuffix(".docx").replace("-", " ").replace("_", " ")
                        }
                    }
                    part.dispose()
                }

                if (content.isNullOrBlank()) {
                    return@post call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "No valid .docx content found",
                    )
                }

                call.respond(
                    mapOf(
                        "content" to content,
                        "title" to title,
                    ),
                )
            }

            post {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@post call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val doc = call.receive<Writing>()
                val ok = writings.createAdmin(doc)
                if (ok) {
                    call.respond(
                        AdminOk(),
                    )
                } else {
                    call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Failed to create writing",
                    )
                }
            }

            patch("/{id}/status") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val id = call.parameters["id"]
                val payload = call.receive<Map<String, String>>()
                val raw = payload["status"]
                val status = runCatching { raw?.let { WritingsStatus.valueOf(it) } }.getOrNull()
                if (id.isNullOrBlank() || status == null) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing id or invalid status",
                    )
                }
                val ok = writings.updateStatusAdmin(id, status)
                if (ok) {
                    call.respond(
                        AdminOk(),
                    )
                } else {
                    call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Writing not found",
                    )
                }
            }

            patch("/{id}/featured") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val id = call.parameters["id"]
                val payload = call.receive<Map<String, Boolean>>()
                val featured = payload["featured"] ?: false
                if (id.isNullOrBlank()) {
                    ErrorCode.VALIDATION_FAILED
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing id",
                    )
                }
                val ok = writings.setFeaturedAdmin(id, featured)
                if (ok) {
                    call.respond(
                        AdminOk(),
                    )
                } else {
                    call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Writing not found",
                    )
                }
            }

            patch("/{id}/sales-link") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) return@patch call.errorHandler(ErrorCode.NOT_FOUND)
                val id = call.parameters["id"] ?: return@patch call.errorHandler(ErrorCode.VALIDATION_FAILED, "Missing id")

                val payload = call.receive<Map<String, String?>>()
                val salesLink = payload["salesLink"]
                val isbn = payload["isbn"]

                val ok = writings.updateSalesLinkAdmin(id, salesLink, isbn)
                if (ok) {
                    call.respond(AdminOk())
                } else {
                    call.errorHandler(ErrorCode.VALIDATION_FAILED, "Writing not found")
                }
            }

            patch("/{id}/cover-url") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) return@patch call.errorHandler(ErrorCode.NOT_FOUND)
                val id = call.parameters["id"] ?: return@patch call.errorHandler(ErrorCode.VALIDATION_FAILED, "Missing id")

                val payload = call.receive<Map<String, String?>>()
                val coverUrl = payload["coverUrl"]

                val ok = writings.updateCoverUrlAdmin(id, coverUrl)
                if (ok) {
                    call.respond(AdminOk())
                } else {
                    call.errorHandler(ErrorCode.VALIDATION_FAILED, "Writing not found")
                }
            }

            patch("/{id}/publisher") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) return@patch call.errorHandler(ErrorCode.NOT_FOUND)
                val id = call.parameters["id"] ?: return@patch call.errorHandler(ErrorCode.VALIDATION_FAILED, "Missing id")

                val publisher = call.receive<WritingPublisher>()

                val ok = writings.updatePublisherAdmin(id, publisher)
                if (ok) {
                    call.respond(AdminOk())
                } else {
                    call.errorHandler(ErrorCode.VALIDATION_FAILED, "Writing not found")
                }
            }

            delete("/{id}") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@delete call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    ErrorCode.VALIDATION_FAILED
                    return@delete call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing id",
                    )
                }
                val ok = writings.deleteAdmin(id)
                if (ok) {
                    call.respond(
                        AdminOk(),
                    )
                } else {
                    call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Writing not found",
                    )
                }
            }
        }
    }
}

fun Route.adminAggregationRoutesV2() {
    authenticate("zitadel") {
        route("/admin") {
            // Dashboard summary (minimal; placeholder counts)
            get("/dashboard") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Not found")),
                    )
                }
                val summary =
                    DashboardSummary(
                        reportsOpen = 0,
                        reportsInReview = 0,
                        reportsResolved = 0,
                        discussionsHiddenOrRemoved = 0,
                        writingsSubmitted = 0,
                        writingsPublished = 0,
                    )
                call.respond(summary)
            }

            // Health proxy (reuse existing readiness in simple form)
            get("/health") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Not found")),
                    )
                }
                call.respond(HealthOverview(ok = true))
            }

            // Analytics placeholder
            get("/analytics") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponses(false, ErrorBody(ErrorCode.NOT_FOUND.name, "Not found")),
                    )
                }
                call.respond(AnalyticsStub())
            }
        }
    }
}

// -----------------------------
// Helpers
// -----------------------------
private fun ApplicationCall.requireAdmin(): Pair<Boolean, String?> {
    val principal = this.principal<UserPrincipal>()
    val uid = principal?.user?.uid
    val roles = principal?.user?.roles ?: emptySet()
    val isAdmin = roles.contains(Role.ADMIN) || roles.contains(Role.MOD)
    return if (!uid.isNullOrBlank() && isAdmin) true to uid else false to null
}




