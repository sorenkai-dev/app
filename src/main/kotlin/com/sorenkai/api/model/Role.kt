package com.sorenkai.api.model

/**
 * Application-level roles extracted from Firebase custom claims.
 *
 * These are used for authorization checks within routes and services.
 */
enum class Role {
    /** Regular authenticated user (default). */
    USER,

    /** Moderator role with elevated permissions. */
    MOD,

    /** Administrator role with full permissions. */
    ADMIN
}
