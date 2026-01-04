package com.sorenkai.core.model.auth

/**
 * Type alias for authenticated user identifier (UID).
 *
 * Using a dedicated alias improves clarity at call sites and allows
 * future migration to a value class without widespread signature churn.
 */
typealias UID = String
