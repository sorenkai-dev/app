package com.sorenkai.api.config

import com.sorenkai.api.dto.StoryCreateRequest
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<StoryCreateRequest> { req ->
            val title = req.title.trim()
            val url = req.url.trim()
            val summary = req.summary?.trim()
            val tags = req.tags.map { it.trim() }

            val maxTitle = 200
            val maxUrl = 2048
            val maxSummary = 2000
            val maxTags = 8
            val maxTagLen = 24
            val tagRegex = Regex("^[a-z0-9-]+$")

            when {
                title.isBlank() -> ValidationResult.Invalid("title is required")
                title.length > maxTitle -> ValidationResult.Invalid("title must be <= $maxTitle chars")
                url.isBlank() -> ValidationResult.Invalid("url is required")
                url.length > maxUrl -> ValidationResult.Invalid("url must be <= $maxUrl chars")
                !(url.startsWith("http://") || url.startsWith("https://")) ->
                    ValidationResult.Invalid("url must start with http:// or https://")
                summary != null && summary.length > maxSummary ->
                    ValidationResult.Invalid("summary must be <= $maxSummary chars")
                tags.size > maxTags -> ValidationResult.Invalid("too many tags (max $maxTags)")
                tags.any { it.isEmpty() } -> ValidationResult.Invalid("tags must be non-empty when provided")
                tags.any { it.length > maxTagLen } -> ValidationResult.Invalid("each tag must be <= $maxTagLen chars")
                tags.any { !tagRegex.matches(it) } -> ValidationResult.Invalid("tags must be lowercase letters, numbers, or hyphens")
                else -> ValidationResult.Valid
            }
        }
        // Comment validation
        validate<com.sorenkai.api.dto.CommentCreateRequest> { req ->
            val body = req.body.trim()
            val maxLen = 2000
            when {
                body.isEmpty() -> ValidationResult.Invalid("body is required")
                body.length > maxLen -> ValidationResult.Invalid("body must be <= $maxLen chars")
                else -> ValidationResult.Valid
            }
        }
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
