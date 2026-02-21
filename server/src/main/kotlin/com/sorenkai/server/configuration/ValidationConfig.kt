package com.sorenkai.server.configuration

import com.sorenkai.common.util.Constants.VALIDATION_MAX_LENGTH
import com.sorenkai.core.dto.reports.ReportCreateRequest
import com.sorenkai.infrastructure.repo.DiscussionsTable.reason
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult

/**
 * Configures Ktor's [RequestValidation] plugin to enforce constraints on incoming DTOs.
 *
 * Responsibilities:
 * - Attach validation rules at the framework level so requests are rejected early,
 *   before hitting services or repositories.
 * - Produce clear, structured error messages when fields are missing or invalid.
 *
 * ### Currently validates:
 * - [com.sorenkai.api.dto.ReportCreateRequest]:
 *   - `targetId`: required, non-blank.
 *   - `reason`: required, non-blank, max length 2000 characters.
 *
 * ### Failure behavior:
 * - On invalid input, Ktor raises a [RequestValidationException],
 *   which is mapped to a `400 Bad Request` by [configureExceptionMapping].
 *
 * ### Example:
 * ```json
 * {
 *   "ok": false,
 *   "error": {
 *     "code": "VALIDATION_FAILED",
 *     "message": "Validation failed",
 *     "details": { "reasons": "reason is required" }
 *   }
 * }
 * ```
 */
fun Application.configureValidation() {
    install(RequestValidation) {
        // Report validation
        validate<ReportCreateRequest> { req ->
            val targetId = req.targetId.trim()
            when {
                targetId.isEmpty() -> ValidationResult.Invalid("targetId is required")
                else -> ValidationResult.Valid
            }
        }
    }
}
