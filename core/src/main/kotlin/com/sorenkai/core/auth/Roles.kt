package com.sorenkai.core.auth

import com.sorenkai.core.model.auth.Role

fun extractRolesFromClaims(claims: Map<String, Any?>): Set<Role> {
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
