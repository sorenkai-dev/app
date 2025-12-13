package com.sorenkai.server.routing

import com.sorenkai.core.services.AdminService
import com.sorenkai.server.errors.ErrorBody
import com.sorenkai.server.errors.ErrorCode
import com.sorenkai.server.errors.ErrorResponses
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.adminRoutes(service: AdminService = AdminService()) {
    route("/v1/admin") {
        route("/users/{uid}") {
            get("/export") {
//                if (call.requireRole(Role.ADMIN) == null) return@get
                val uid = call.parameters["uid"]
                if (uid.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses(
                            ok = false,
                            error =
                                ErrorBody(
                                    code = ErrorCode.VALIDATION_FAILED.name,
                                    message = "Missing user id",
                                    details = mapOf("param" to "uid"),
                                ),
                        ),
                    )
                }
                val export = service.exportByUid(uid)
                call.respond(export)
            }

            post("/delete") {
//                if (call.requireRole(Role.ADMIN) == null) return@post
                val uid = call.parameters["uid"]
                if (uid.isNullOrBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponses(
                            ok = false,
                            error =
                                ErrorBody(
                                    code = ErrorCode.VALIDATION_FAILED.name,
                                    message = "Missing user id",
                                    details = mapOf("param" to "uid"),
                                ),
                        ),
                    )
                }
                val result = service.deleteByUid(uid)
                call.respond(
                    mapOf(
                        "ok" to true,
//                        "uid" to result.uid,
//                        "reportsDeleted" to result.reportsDeleted,
                    ),
                )
            }
        }
    }
}
