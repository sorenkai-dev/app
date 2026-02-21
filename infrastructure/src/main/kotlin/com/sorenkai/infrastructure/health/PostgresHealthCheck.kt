package com.sorenkai.infrastructure.health

import com.sorenkai.common.util.Constants.MAX_MSG_SIZE
import com.sorenkai.common.util.Constants.TIMEOUT
import com.sorenkai.core.health.ComponentStatus
import com.sorenkai.core.health.HealthCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresHealthCheck(
    private val db: Lazy<Database?>,
) : HealthCheck {
    override val name: String = "postgres"

    override suspend fun check(): ComponentStatus =
        try {
            withTimeout(TIMEOUT) {
                val database = db.value ?: throw IllegalStateException("Database not initialized")
                withContext(Dispatchers.IO) {
                    transaction(database) {
                        TransactionManager.current().exec("SELECT 1") { }
                    }
                }
                ComponentStatus(ok = true)
            }
        } catch (t: Throwable) {
            ComponentStatus(ok = false, error = t.message?.take(MAX_MSG_SIZE))
        }
}
