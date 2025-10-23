package com.sorenkai.api.util

import java.util.UUID
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

fun newId(): String = UUID.randomUUID().toString()

fun now(): Instant = Clock.System.now()

// suspend fun appCheck = if (!com.sorenkai.api.security.AppCheckSecurity.enforce(call, enforce, allowedOrigins)) return@patch
