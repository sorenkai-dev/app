package com.sorenkai.api.configuration

import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoWriteException
import com.sorenkai.api.errors.ErrorBody
import com.sorenkai.api.errors.ErrorCode
import com.sorenkai.api.errors.ErrorResponses
import com.sorenkai.api.errors.ServerExceptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.micrometer.core.instrument.config.validate.ValidationException
import kotlinx.serialization.SerializationException

private const val MONGO_DUPLICATE_KEY_CODE = 11000

/**
 * Configures global exception handling for the API.
 *
 * Installs [StatusPages] to intercept exceptions thrown during request processing
 * and translate them into structured JSON error responses using [ErrorResponses].
 * Each known exception type is mapped to an [ErrorCode] and HTTP status code,
 * ensuring consistent error reporting across the API.
 *
 * ### Mappings:
 * - [ServerExceptions]: Domain-level exceptions explicitly thrown by services, mapped to their
 *   associated [ErrorCode] and message.
 * - [RequestValidationException]: Triggered by Ktor's validation plugin, mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400) with detailed reasons.
 * - [ValidationException]: Infrastructure-level validation failures mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400).
 * - [IllegalArgumentException]: Invalid argument failures mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400).
 * - [SerializationException]: Request deserialization failures mapped to
 *   [ErrorCode.VALIDATION_FAILED] (400).
 * - [MongoWriteException]: Duplicate key errors map to [ErrorCode.CONFLICT] (409),
 *   all others to [ErrorCode.INTERNAL] (500).
 * - [MongoBulkWriteException]: Bulk write failures mapped to [ErrorCode.INTERNAL] (500).
 * - [NotFoundException]: Resource lookup failures mapped to [ErrorCode.NOT_FOUND] (404).
 * - [ErrorCode.RATE_LIMITED]: Rate limiting exceptions mapped to (429).
 * - [Throwable] (catch-all): Any unhandled exception mapped to [ErrorCode.INTERNAL] (500)
 *   with a generic message and full stack trace logged.
 *
 * All error responses conform to [ErrorResponses]:
 * - `ok = false`
 * - `error.code` - one of [ErrorCode]
 * - `error.message` - human-readable reason
 * - `error.details` - optional map with structured context
 * - `requestId` - call ID for tracing
 */
fun Application.configureExceptionMapping() {
    install(StatusPages) {
        exception<ServerExceptions> { call, e ->
            call.respond(
                e.code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code = e.code.name, message = e.message, details = e.details),
                    requestId = call.callId,
                ),
            )
        }

        exception<RequestValidationException> { call, e ->
            val code = ErrorCode.VALIDATION_FAILED
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(
                        code = code.name,
                        message = "Validation failed",
                        details = mapOf("reasons" to e.reasons.joinToString("; ")),
                    ),
                    requestId = call.callId,
                ),
            )
        }

        exception<ValidationException> { call, e ->
            val code = ErrorCode.VALIDATION_FAILED
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, e.message ?: "Bad request"),
                    requestId = call.callId,
                ),
            )
        }

        exception<IllegalArgumentException> { call, e ->
            val code = ErrorCode.VALIDATION_FAILED
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, e.message ?: "Invalid argument"),
                    requestId = call.callId,
                ),
            )
        }

        exception<SerializationException> { call, e ->
            val code = ErrorCode.VALIDATION_FAILED
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, "Serialization error: ${e.message}"),
                    requestId = call.callId,
                ),
            )
        }

        exception<MongoWriteException> { call, e ->
            val code = if (e.error.code == MONGO_DUPLICATE_KEY_CODE) ErrorCode.CONFLICT else ErrorCode.INTERNAL
            val message = if (e.error.code == MONGO_DUPLICATE_KEY_CODE) "Duplicate key" else (e.message ?: "Mongo write error")
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, message),
                    requestId = call.callId,
                ),
            )
        }

        exception<MongoBulkWriteException> { call, e ->
            val code = ErrorCode.INTERNAL
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, e.message ?: "Mongo bulk write error"),
                    requestId = call.callId,
                ),
            )
        }

        exception<NotFoundException> { call, e ->
            val code = ErrorCode.NOT_FOUND
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code.name, e.message ?: "Not found"),
                    requestId = call.callId,
                ),
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error(
                "Unhandled exception on ${call.request.httpMethod.value} ${call.request.path()}",
                cause,
            )
            val code = ErrorCode.INTERNAL
            call.respond(
                code.http,
                ErrorResponses(
                    ok = false,
                    error = ErrorBody(code = code.name, message = "Internal server error."),
                    requestId = call.callId,
                ),
            )
        }
    }
}
