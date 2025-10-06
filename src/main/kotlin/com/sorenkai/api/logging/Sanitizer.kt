package com.sorenkai.api.logging

import com.sorenkai.api.logging.Sanitizer.sanitize


/**
 * Utility for sanitizing log messages to prevent leaking sensitive data.
 *
 * This object provides regex-based replacements for common sensitive patterns
 * such as:
 * - Authorization headers (e.g., `Authorization: Bearer <token>`)
 * - JSON fields containing authorization tokens
 * - Email addresses
 *
 * ### Behavior:
 * - Replaces bearer tokens with `[REDACTED]`.
 * - Replaces email addresses with `[EMAIL REDACTED]`.
 * - Leaves all other content untouched.
 *
 * ### Usage:
 * Use [sanitize] before writing any log message that may contain
 * user-supplied headers, JSON, or other data. This ensures that sensitive
 * information (tokens, emails) never makes it into logs.
 *
 * Example:
 * ```
 * val raw = """Authorization: Bearer abc123"""
 * val safe = Sanitizer.sanitize(raw)
 * // safe == "Authorization: Bearer [REDACTED]"
 * ```
 */
object Sanitizer {
    // (?i) = case-insensitive; capture the header name and scheme, replace the credential with [REDACTED]
    private val authHeaderRegex = Regex(
        pattern = """(?i)(authorization\s*[:=]\s*bearer)\s+([^\s\"]+)"""
    )

    // JSON style: "authorization":"Bearer token" (allow spaces after colon)
    private val authJsonRegex = Regex(
        pattern = """(?i)("authorization"\s*:\s*"bearer)\s+([^"]+)"""
    )

    // Mask email addresses (basic pattern)
    private val emailRegex = Regex(
        pattern = """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""
    )

    /**
     * Returns a sanitized copy of the input [message] with sensitive values redacted.
     *
     * @param message the raw log message, possibly containing sensitive data
     * @return the sanitized version with tokens/emails replaced, or an empty string if null
     */
    fun sanitize(message: String?): String {
        if (message.isNullOrEmpty()) return message ?: ""
        var s = message
        s = authHeaderRegex.replace(s) { m ->
            val prefix = m.groupValues[1]
            "$prefix [REDACTED]"
        }
        s = authJsonRegex.replace(s) { m ->
            val prefix = m.groupValues[1]
            "$prefix [REDACTED]"
        }
        s = emailRegex.replace(s) { _ -> "[EMAIL REDACTED]" }
        return s
    }
}
