package com.sorenkai.api.errors

import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoWriteException
import com.sorenkai.api.dto.response.ApiErrorBody
import com.sorenkai.api.dto.response.ApiErrorResponse
import com.sorenkai.api.services.DiscussionService.ValidationException
import com.sorenkai.api.services.ReportService.RateLimitedException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.plugins.callid.callId

/**
 * Configures global exception handling for the API.
 *
 * This function installs [StatusPages] to intercept exceptions thrown during request
 * processing and translate them into structured JSON error responses. Each known exception
 * type is mapped to an [ErrorCode] and [HttpStatusCode], ensuring consistent error reporting
 * across the API.
 *
 * ### Mappings:
 * - [ApiException]: Domain-level exceptions explicitly thrown by services, mapped to their
 *   associated [ErrorCode] and message.
 * - [RequestValidationException]: Triggered by Ktor's validation plugin, mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400 Bad Request) with detailed reasons.
 * - [MongoWriteException], [MongoBulkWriteException]: Database-level exceptions. Duplicate key
 *   errors map to [ErrorCode.CONFLICT] (409), others to [ErrorCode.INTERNAL] (500).
 * - [RateLimitedException] (including [com.sorenkai.api.services.DiscussionService.RateLimitedException]):
 *   Indicates client exceeded request limits, mapped to [ErrorCode.RATE_LIMITED] (429).
 * - [ValidationException]: Generic service-level validation failures mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400).
 * - [NotFoundException]: Resource lookup failures mapped to [ErrorCode.NOT_FOUND] (404).
 * - [Throwable] (catch-all): Any unhandled exception is mapped to [ErrorCode.INTERNAL] (500)
 *   with a generic "Internal server error" message.
 *
 * The resulting error responses conform to [com.sorenkai.api.dto.response.ApiErrorResponse],
 * which includes:
 * - `ok = false`
 * - `error.code` (one of [ErrorCode])
 * - `error.message` (human-readable reason)
 * - `error.details` (optional map with structured context)
 *
 * This setup ensures:
 * - No exceptions propagate as raw stack traces
 * - Clients always receive a consistent JSON error schema
 * - Developers can extend by throwing [ApiException] with meaningful codes/messages
 */
fun Application.configureExceptionMapping() {
    install(StatusPages) {
        // Domain-level API exceptions -> structured JSON with proper HTTP status
        exception<ApiException> { call, e ->
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(
                    code = e.code.name,
                    message = e.message,
                    details = e.details
                ),
                requestId = call.callId
            )
            call.respond(e.code.http, body)
        }

        // Ktor RequestValidation -> 400 BAD REQUEST with reasons
        exception<RequestValidationException> { call, e ->
            val details = mapOf("reasons" to e.reasons.joinToString("; "))
            val code = ErrorCode.VALIDATION_FAILED
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(
                    code = code.name,
                    message = "Validation failed",
                    details = details
                ),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }

        // Mongo duplicate key -> 409 CONFLICT
        exception<MongoWriteException> { call, e ->
            val code = if (e.error.code == 11000) ErrorCode.CONFLICT else ErrorCode.INTERNAL
            val message = if (e.error.code == 11000) "Duplicate key" else (e.message ?: "Mongo write error")
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(
                    code = code.name,
                    message = message,
                    details = null
                ),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }
        exception<MongoBulkWriteException> { call, e ->
            val hasDup = e.writeErrors.any { it.code == 11000 }
            val code = if (hasDup) ErrorCode.CONFLICT else ErrorCode.INTERNAL
            val message = if (hasDup) "Duplicate key" else (e.message ?: "Mongo bulk write error")
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(
                    code = code.name,
                    message = message,
                    details = null
                ),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }

        // Service-level common exceptions
        exception<RateLimitedException> { call, e ->
            val code = ErrorCode.RATE_LIMITED
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(code.name, e.message ?: "Rate limited"),
                requestId = call.callId
            )
            // Suggest a default retry-after of 30 seconds if not specified by the service
            call.response.headers.append("Retry-After", "30", safeOnly = false)
            call.respond(code.http, body)
        }
        exception<com.sorenkai.api.services.DiscussionService.RateLimitedException> { call, e ->
            val code = ErrorCode.RATE_LIMITED
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(code.name, e.message ?: "Rate limited"),
                requestId = call.callId
            )
            call.response.headers.append("Retry-After", "30", safeOnly = false)
            call.respond(code.http, body)
        }
        exception<ValidationException> { call, e ->
            val code = ErrorCode.VALIDATION_FAILED
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(code.name, e.message ?: "Bad request"),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }
        exception<NotFoundException> { call, e ->
            val code = ErrorCode.NOT_FOUND
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(code.name, e.message ?: "Not found"),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }

        // Fallback: unhandled exceptions -> 500 with generic code
        exception<Throwable> { call, cause ->
            val code = ErrorCode.INTERNAL
            val body = ApiErrorResponse(
                ok = false,
                error = ApiErrorBody(
                    code = code.name,
                    message = "Internal server error."
                ),
                requestId = call.callId
            )
            call.respond(code.http, body)
        }
    }
}
