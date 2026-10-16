package com.sorenkai.common.model.user

interface IUserService {
    /**
     * Resolves a userId to robust user data, utilizing caching where possible.
     */
    suspend fun getUser(userId: String): UserData
}




