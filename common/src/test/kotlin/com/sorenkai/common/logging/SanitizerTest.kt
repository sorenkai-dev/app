package com.sorenkai.common.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SanitizerTest {
    @Test
    fun `redacts bearer token in header style`() {
        val input = "Authorization: Bearer abc123.def456"
        val out = Sanitizer.sanitize(input)
        assertEquals("Authorization: Bearer [REDACTED]", out)
    }

    @Test
    fun `redacts bearer token in JSON style`() {
        val input = "{\"authorization\":\"Bearer secret.token.here\"}"
        val out = Sanitizer.sanitize(input)
        assertEquals("{\"authorization\":\"Bearer [REDACTED]\"}", out)
    }

    @Test
    fun `redacts email addresses`() {
        val input = "user email is alice@example.com and bob.smith@sub.domain.org"
        val out = Sanitizer.sanitize(input)
        assertEquals("user email is [EMAIL REDACTED] and [EMAIL REDACTED]", out)
    }

    @Test
    fun `no-op on clean message`() {
        val input = "Hello world"
        val out = Sanitizer.sanitize(input)
        assertEquals(input, out)
    }
}




