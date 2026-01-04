package com.sorenkai.server.routing

import com.sorenkai.community.discussions.services.DiscussionService
import com.sorenkai.core.dto.admin.AdminOk
import com.sorenkai.core.dto.admin.AnalyticsStub
import com.sorenkai.core.dto.admin.DashboardSummary
import com.sorenkai.core.dto.admin.HealthOverview
import com.sorenkai.core.model.auth.Role
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.server.errors.ErrorBody
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.ErrorResponses
import com.sorenkai.server.errors.errorHandler
import com.sorenkai.server.security.UserPrincipal
import com.sorenkai.writings.services.WritingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val adminV2Log = LoggerFactory.getLogger("AdminRoutesV2")

/**
 * V2 admin routes for discussions (status/pin/lock)
 */
fun Route.adminDiscussionRoutesV2() {
    val discussions by inject<DiscussionService>()

    authenticate("zitadel") {
        route("/admin/discussions") {
            // Set status and reason
            patch("/{id}/status") {
                val (adminOk, actorUid) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(ErrorCode.NOT_FOUND)
                }
                val id = call.parameters["id"]
                if (actorUid.isNullOrBlank() || id.isNullOrBlank()) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing uid or id",
                    )
                }
                val payload = call.receive<Map<String, String?>>()
                val statusRaw = payload["status"]
                val reason = payload["reason"]
                val status = runCatching { statusRaw?.let { DiscussionsStatus.valueOf(it) } }.getOrNull()
                if (status == null) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Invalid status",
                    )
                }
                val ok = discussions.setStatus(actorUid, id, status, reason)
                if (ok) {
                    call.respond(
                        mapOf("ok" to true),
                    )
                } else {
                    call.errorHandler(
                        ErrorCode.NOT_FOUND,
                        "Discussion not found",
                    )
                }
            }

            // Pin toggle
            patch("/{id}/pin") {
                val (adminOk, actorUid) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val id = call.parameters["id"]
                if (actorUid.isNullOrBlank() || id.isNullOrBlank()) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing uid or id",
                    )
                }
                val payload = call.receive<Map<String, Boolean>>()
                val value = payload["value"] ?: false
                when (val res = discussions.setPinned(id, value)) {
                    is UpdateResult.Success -> call.respond(mapOf("ok" to true))
                    is UpdateResult.Failure ->
                        call.errorHandler(
                            ErrorCode.NOT_FOUND,
                            res.message,
                        )
                }
            }

            // Lock toggle
            patch("/{id}/lock") {
                val (adminOk, actorUid) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val id = call.parameters["id"]
                if (actorUid.isNullOrBlank() || id.isNullOrBlank()) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing uid or id",
                    )
                }
                val payload = call.receive<Map<String, Boolean>>()
                val value = payload["value"] ?: false
                when (val res = discussions.setLocked(id, value)) {
                    is UpdateResult.Success -> call.respond(mapOf("ok" to true))
                    is UpdateResult.Failure ->
                        call.errorHandler(
                            ErrorCode.NOT_FOUND,
                            res.message,
                        )
                }
            }
        }
    }
}

// -----------------------------
// Admin writings routes and admin-only endpoints
// -----------------------------
fun Route.adminWritingsRoutesV2() {
    val writings by inject<WritingService>()
    authenticate("zitadel") {
        route("/admin/writings") {
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

            patch("/{slug}/status") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val slug = call.parameters["slug"]
                val payload = call.receive<Map<String, String>>()
                val raw = payload["status"]
                val status = runCatching { raw?.let { WritingsStatus.valueOf(it) } }.getOrNull()
                if (slug.isNullOrBlank() || status == null) {
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing slug or invalid status",
                    )
                }
                val ok = writings.updateStatusAdmin(slug, status)
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

            patch("/{slug}/featured") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@patch call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val slug = call.parameters["slug"]
                val payload = call.receive<Map<String, Boolean>>()
                val featured = payload["featured"] ?: false
                if (slug.isNullOrBlank()) {
                    ErrorCode.VALIDATION_FAILED
                    return@patch call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing slug",
                    )
                }
                val ok = writings.setFeaturedAdmin(slug, featured)
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

            delete("/{slug}") {
                val (adminOk, _) = call.requireAdmin()
                if (!adminOk) {
                    return@delete call.errorHandler(
                        ErrorCode.NOT_FOUND,
                    )
                }
                val slug = call.parameters["slug"]
                if (slug.isNullOrBlank()) {
                    ErrorCode.VALIDATION_FAILED
                    return@delete call.errorHandler(
                        ErrorCode.VALIDATION_FAILED,
                        "Missing slug",
                    )
                }
                val ok = writings.deleteAdmin(slug)
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
