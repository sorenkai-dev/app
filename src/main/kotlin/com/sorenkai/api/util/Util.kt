package com.sorenkai.api.util

import java.util.UUID
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import org.bson.types.ObjectId

// Generate a MongoDB ObjectId hex string for use as primary keys
fun newId(): String = ObjectId().toHexString()

fun now(): Instant = Clock.System.now()

// suspend fun appCheck = if (!com.sorenkai.api.security.AppCheckSecurity.enforce(call, enforce, allowedOrigins)) return@patch
