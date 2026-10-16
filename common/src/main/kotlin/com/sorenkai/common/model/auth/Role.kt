package com.sorenkai.common.model.auth

/**
 * Application-level roles extracted from custom claims.
 *
 * These are used for authorization checks within routes and services.
 */
enum class Role {
    /** Regular authenticated user (default). */
    USER,

    /** Moderator role with elevated permissions. */
    MOD,

    /** Administrator role with full permissions. */
    ADMIN,
}




