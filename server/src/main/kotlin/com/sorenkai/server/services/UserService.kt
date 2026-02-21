package com.sorenkai.server.services

import com.sorenkai.core.auth.IUserService
import com.sorenkai.core.auth.UserData
import com.sorenkai.server.rate.JedisAdapter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.params.SetParams

class UserService(
    private val redis: JedisAdapter,
    private val httpClient: HttpClient,
    private val zitadelIssuer: String,
    private val zitadelPat: String,
) : IUserService {
    private val log = LoggerFactory.getLogger(UserService::class.java)
    private val cachePrefix = "user:data:"
    private val ttlSeconds = 86400L // 24 hours

    @Serializable
    private data class ZitadelUserResponse(
        val user: ZitadelUser,
    )

    @Serializable
    private data class ZitadelUser(
        val userId: String,
        val username: String,
        val human: ZitadelHuman? = null,
    )

    @Serializable
    private data class ZitadelHuman(
        val profile: ZitadelProfile? = null,
    )

    @Serializable
    private data class ZitadelProfile(
        val displayName: String? = null,
        val avatarUrl: String? = null,
    )

    override suspend fun getUser(userId: String): UserData {
        val cacheKey = "$cachePrefix$userId"

        // 1. Try Redis
        try {
            val cached = redis.get(cacheKey)
            if (cached != null) {
                return Json.decodeFromString<UserData>(cached)
            }
        } catch (redisError: Exception) {
            log.warn("Redis error fetching user data for {}: {}", userId, redisError.message)
        }

        // 2. Try Zitadel
        try {
            val response =
                httpClient.get("$zitadelIssuer/v2/users/$userId") {
                    header("Authorization", "Bearer $zitadelPat")
                }

            if (response.status == HttpStatusCode.OK) {
                val zitadelResponse = response.body<ZitadelUserResponse>()
                val zitadelUser = zitadelResponse.user

                val userData =
                    UserData(
                        userId = zitadelUser.userId,
                        username = zitadelUser.username,
                        displayName = zitadelUser.human?.profile?.displayName,
                        avatarUrl = zitadelUser.human?.profile?.avatarUrl,
                    )

                // 3. Store in Redis
                try {
                    redis.set(cacheKey, Json.encodeToString(userData), SetParams.setParams().ex(ttlSeconds))
                } catch (redisError: Exception) {
                    log.warn("Redis error storing user data for {}: {}", userId, redisError.message)
                }

                return userData
            } else {
                log.warn("Zitadel returned {} for userId {}", response.status, userId)
            }
        } catch (zitadelError: Exception) {
            log.error("Error fetching user from Zitadel for {}: {}", userId, zitadelError.message)
        }

        // 4. Fallback
        return UserData(userId = userId, username = userId)
    }
}
