package com.sorenkai.server.security

import com.sorenkai.core.model.auth.AuthUser

/**
 * Ktor principal wrapper around core AuthUser to avoid bringing Ktor into :core.
 */
data class UserPrincipal(
    val user: AuthUser,
)
