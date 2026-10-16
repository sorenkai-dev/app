package com.sorenkai.api.security

import com.sorenkai.common.model.auth.AuthUser

/**
 * Ktor principal wrapper around common AuthUser to avoid bringing Ktor into :common.
 */
data class UserPrincipal(
    val user: AuthUser,
)




