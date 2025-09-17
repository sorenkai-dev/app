package com.sorenkai.api.logging

/**
 * Utilities to sanitize log messages to avoid leaking sensitive data.
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
     * Returns a sanitized copy of the input message with sensitive values redacted.
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
