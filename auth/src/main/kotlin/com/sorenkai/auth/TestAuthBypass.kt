package com.sorenkai.auth

import com.sorenkai.common.util.isProdEnv
import com.sorenkai.common.model.auth.Role

internal fun testUid(): String? {
    // Only allow test bypass when explicitly enabled for tests.
    // Conditions:
    // - ENV == "test" OR TEST_AUTH_ENABLED == "true" (case-insensitive)
    // - And never in prod (ENV=="prod" blocks regardless)
    val uid = System.getProperty("TEST_AUTH_UID") ?: System.getenv("TEST_AUTH_UID")
    if (uid.isNullOrBlank()) return null

    val env = (System.getenv("ENV") ?: System.getProperty("ENV"))?.lowercase()
    val enabledFlag =
        (
            System.getProperty("TEST_AUTH_ENABLED")
                ?: System.getenv("TEST_AUTH_ENABLED")
                ?: "false"
        ).lowercase()

    val enabled = (env == "test") || (enabledFlag == "true")
    return if (!isProdEnv() && enabled) uid else null
}

internal fun testRoles(): Set<Role> {
    val raw = System.getProperty("TEST_AUTH_ROLES") ?: System.getenv("TEST_AUTH_ROLES")
    if (raw.isNullOrBlank()) return setOf(Role.USER)
    return raw
        .split(',')
        .map { it.trim().lowercase() }
        .mapNotNull {
            when (it) {
                "user" -> Role.USER
                "mod", "moderator" -> Role.MOD
                "admin", "administrator" -> Role.ADMIN
                else -> null
            }
        }.toSet()
        .ifEmpty { setOf(Role.USER) }
}




