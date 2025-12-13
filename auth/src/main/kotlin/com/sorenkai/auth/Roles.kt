package com.sorenkai.auth

import com.sorenkai.auth.model.Role

/**
 * Utility to extract application roles from custom claims.
 *
 * Supports both `roles` (array) and `role` (single string) claims.
 * Case-insensitive mapping:
 * - `"user"` → [com.sorenkai.api.model.Role.USER]
 * - `"mod"` / `"moderator"` → [com.sorenkai.api.model.Role.MOD]
 * - `"admin"` / `"administrator"` → [com.sorenkai.api.model.Role.ADMIN]
 *
 * Falls back to [com.sorenkai.api.model.Role.USER] if no recognized roles are found.
 *
 * @param claims Map of token claims.
 * @return A non-empty set of roles (defaults to [com.sorenkai.api.model.Role.USER]).
 */
internal fun extractRolesFromClaims(claims: Map<String, Any?>): Set<Role> {
    val rawRoles: List<String> =
        when (val v = claims["roles"]) {
            is List<*> -> v.filterIsInstance<String>()
            is String -> listOf(v)
            else ->
                when (val single = claims["role"]) {
                    is String -> listOf(single)
                    is List<*> -> single.filterIsInstance<String>()
                    else -> emptyList()
                }
        }.map { it.trim().lowercase() }

    val mapped =
        rawRoles
            .mapNotNull { s ->
                when (s) {
                    "user" -> Role.USER
                    "mod", "moderator" -> Role.MOD
                    "admin", "administrator" -> Role.ADMIN
                    else -> null
                }
            }.toMutableSet()

    if (mapped.isEmpty()) mapped.add(Role.USER)
    return mapped
}
