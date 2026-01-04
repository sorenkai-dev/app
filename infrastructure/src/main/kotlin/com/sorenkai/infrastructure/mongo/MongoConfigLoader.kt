package com.sorenkai.infrastructure.mongo

import com.sorenkai.common.util.Constants.MONGO_CONNECT_TIMEOUT_DEFAULT
import com.sorenkai.common.util.Constants.MONGO_MAX_POOL_DEFAULT
import com.sorenkai.common.util.Constants.MONGO_MIN_POOL_DEFAULT
import com.sorenkai.common.util.Constants.MONGO_SERVER_SELECTION_DEFAULT
import com.sorenkai.common.util.Constants.MONGO_SOCKET_TIMEOUT_DEFAULT
import com.sorenkai.common.util.Constants.MONGO_WAIT_QUEUE_DEFAULT

object MongoConfigLoader {
    fun loadConfig(): MongoConfig {
        val uri =
            System.getProperty("MONGODB_URI")?.takeIf { it.isNotBlank() }
                ?: System.getenv("MONGODB_URI")
                ?: "mongodb://localhost:27017/sorenkai?retryWrites=false"

        fun envInt(
            name: String,
            default: Int,
        ): Int = System.getenv(name)?.toIntOrNull() ?: default

        fun envBool(
            name: String,
            default: Boolean,
        ): Boolean = System.getenv(name)?.equals("true", ignoreCase = true) ?: default

        return MongoConfig(
            uri = uri,
            maxPool = envInt("MONGO_MAX_POOL_SIZE", MONGO_MAX_POOL_DEFAULT),
            minPool = envInt("MONGO_MIN_POOL_SIZE", MONGO_MIN_POOL_DEFAULT),
            waitQueueMs = envInt("MONGO_WAIT_QUEUE_MS", MONGO_WAIT_QUEUE_DEFAULT),
            connectTimeoutMs = envInt("MONGO_CONNECT_TIMEOUT_MS", MONGO_CONNECT_TIMEOUT_DEFAULT),
            socketTimeoutMs = envInt("MONGO_SOCKET_TIMEOUT_MS", MONGO_SOCKET_TIMEOUT_DEFAULT),
            serverSelectionMs = envInt("MONGO_SERVER_SELECTION_MS", MONGO_SERVER_SELECTION_DEFAULT),
            retryWrites = envBool("MONGO_RETRY_WRITES", true),
        )
    }
}
