package com.sorenkai.core.model.auth

/**
 * Represents an authenticated user extracted from ID tokens.
 *
 * @property uid user UID
 * @property roles Set of roles assigned to the user. Defaults to [com.sorenkai.api.model.Role.USER].
 */
data class AuthUser(
    val uid: String,
    val roles: Set<Role> = setOf(Role.USER),
) {
    /**
     * Checks whether the user has at least one of the [required] roles.
     *
     * @param required Roles to check against.
     * @return `true` if the user has any matching role, or if [required] is empty.
     */
    fun hasAnyRole(vararg required: Role): Boolean = required.isEmpty() || roles.any { it in required.toSet() }
}
