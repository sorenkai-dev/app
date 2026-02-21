package com.sorenkai.core.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)
