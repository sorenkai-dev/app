package com.sorenkai.infrastructure.health

import com.sorenkai.common.util.Constants.MAX_MSG_SIZE
import com.sorenkai.common.util.Constants.TIMEOUT
import com.sorenkai.core.health.ComponentStatus
import com.sorenkai.core.health.HealthCheck
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
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
 * - On failure: catches any [Exception] and returns `ok = false` with the error message
 *   (truncated to 300 characters for readability).
 *
 * ### Purpose:
 * - Provides a simple signal for `/serverhealth` to confirm that MongoDB is available.
 * - Useful for operators and monitoring systems to quickly detect DB outages or misconfigurations.
 */
class MongoHealthCheck(
    private val databaseProvider: Lazy<IMongoDatabaseProvider>,
) : HealthCheck {
    override val name: String = "mongo"

    override suspend fun check(): ComponentStatus =
        try {
            withTimeout(TIMEOUT) {
                val db = databaseProvider.value.database
                db.runCommand<BsonDocument>(BsonDocument("ping", BsonInt32(1)))
                ComponentStatus(ok = true)
            }
        } catch (expected: Exception) {
            ComponentStatus(ok = false, error = expected.message?.take(MAX_MSG_SIZE))
        }
}
