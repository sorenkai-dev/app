package com.sorenkai.api.health

import com.sorenkai.common.util.Constants.MAX_MSG_SIZE
import com.sorenkai.common.util.Constants.TIMEOUT
import com.sorenkai.api.health.ComponentStatus
import com.sorenkai.api.health.HealthCheck
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.withTimeout
import org.bson.BsonDocument
import org.bson.BsonInt32
import kotlin.time.Duration.Companion.milliseconds

class MongoHealthCheck(
    private val database: Lazy<MongoDatabase>,
) : HealthCheck {
    override val name: String = "mongo"

    override suspend fun check(): ComponentStatus =
        try {
            withTimeout(TIMEOUT.milliseconds) {
                val db = database.value
                db.runCommand<BsonDocument>(BsonDocument("ping", BsonInt32(1)))
                ComponentStatus(ok = true)
            }
        } catch (expected: Exception) {
            ComponentStatus(ok = false, error = expected.message?.take(MAX_MSG_SIZE))
        }
}




