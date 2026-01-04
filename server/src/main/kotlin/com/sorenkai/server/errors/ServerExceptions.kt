package com.sorenkai.server.errors

/**
 * Base exception type for API-domain errors.
 *
 * Wraps an [ErrorCode] and an optional details map to provide
 * structured error responses.
 *
 * @property code the structured error code (maps to HTTP status via [ErrorCode.http])
 * @property message human-readable error message describing the failure
 * @property details optional key-value metadata for additional context
 *
 * Thrown by services or routes when a request cannot be fulfilled.
 * These exceptions are caught by [com.sorenkai.server.configuration.configureExceptionMapping] and
 * converted into [ErrorResponses].
 */
class ServerExceptions(
    val code: ErrorCode,
    override val message: String,
    val details: Map<String, String>? = null,
) : RuntimeException(message)
