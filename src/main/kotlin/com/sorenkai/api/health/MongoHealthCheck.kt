package com.sorenkai.api.health

import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.response.ComponentStatus
import kotlinx.coroutines.withTimeout
import org.bson.BsonDocument
import org.bson.BsonInt32

/**
 * Health check for MongoDB connectivity.
 *
 * This check issues a lightweight `ping` command against the configured Mongo database
 * to verify that the connection is alive and responsive. It runs with a 5-second timeout
 * to avoid hanging the health endpoint if the database is slow or unreachable.
 *
 * ### Behavior:
 * - On success: returns [ComponentStatus] with `ok = true`.
 * - On failure: catches any [Throwable] and returns `ok = false` with the error message
 *   (truncated to 300 characters for readability).
 *
 * ### Purpose:
 * - Provides a simple signal for `/serverhealth` to confirm that MongoDB is available.
 * - Useful for operators and monitoring systems to quickly detect DB outages or misconfigurations.
 */
class MongoHealthCheck : HealthCheck {
    override val name: String = "mongo"

    override suspend fun check(): ComponentStatus = try {
        withTimeout(5000) {
            val db = Mongo.db
            db.runCommand<BsonDocument>(BsonDocument("ping", BsonInt32(1)))
            ComponentStatus(ok = true)
        }
    } catch (t: Throwable) {
        ComponentStatus(ok = false, error = t.message?.take(300))
    }
}
