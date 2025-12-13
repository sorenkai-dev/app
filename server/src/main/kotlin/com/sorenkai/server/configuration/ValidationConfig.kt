package com.sorenkai.server.configuration

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
        validate<com.sorenkai.api.dto.ReportCreateRequest> { req ->
            val targetId = req.targetId.trim()
            val reason = req.reason.trim()
            val maxLen = 2000
            when {
                targetId.isEmpty() -> ValidationResult.Invalid("targetId is required")
                reason.isEmpty() -> ValidationResult.Invalid("reason is required")
                reason.length > maxLen -> ValidationResult.Invalid("reason must be <= $maxLen chars")
                else -> ValidationResult.Valid
            }
        }
    }
}
