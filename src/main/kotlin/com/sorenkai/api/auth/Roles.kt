package com.sorenkai.api.auth

/**
 * Simple role model extracted from Firebase custom claims.
 */
enum class Role { USER, MOD, ADMIN }

/** Principal-like structure we can pass around after verifying Firebase token. */
data class AuthUser(
    val uid: String,
    val roles: Set<Role> = setOf(Role.USER)
) {
    fun hasAnyRole(vararg required: Role): Boolean =
        required.isEmpty() || roles.any { it in required.toSet() }
}

internal fun extractRolesFromClaims(claims: Map<String, Any?>): Set<Role> {
    // Support both "roles" (array) and "role" (string) custom claims
    val rawRoles: List<String> = when (val v = claims["roles"]) {
        is List<*> -> v.filterIsInstance<String>()
        is String -> listOf(v)
        else -> when (val single = claims["role"]) {
            is String -> listOf(single)
            is List<*> -> single.filterIsInstance<String>()
            else -> emptyList()
        }
    }.map { it.trim().lowercase() }

    val mapped = rawRoles.mapNotNull { s ->
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
