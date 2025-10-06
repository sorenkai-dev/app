package com.sorenkai.api.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.response.ComponentStatus
import com.sorenkai.api.dto.response.HealthResult
import kotlinx.coroutines.withTimeout
import org.bson.BsonDocument
import org.bson.BsonInt32
import java.lang.management.ManagementFactory

/**
 * Service for aggregating backend health information across dependencies and runtime.
 */
class HealthService {

    /**
     * Perform health checks against backend dependencies and runtime.
     *
     * @return [HealthResult] containing status of each component and overall result.
     */
    suspend fun checkHealth(): HealthResult {
        val components = mutableMapOf<String, ComponentStatus>()

        // --- MongoDB ---
        val mongoStatus = try {
            withTimeout(5000) {
                val db = Mongo.db
                db.runCommand<BsonDocument>(BsonDocument("ping", BsonInt32(1)))
                ComponentStatus(ok = true)
            }
        } catch (t: Throwable) {
            ComponentStatus(ok = false, error = t.message ?: t::class.simpleName)
        }
        components["mongo"] = mongoStatus

        // --- Firebase Auth ---
        val firebaseAuthStatus = try {
            FirebaseAuth.getInstance() // ensures init
            ComponentStatus(ok = true)
        } catch (t: Throwable) {
            ComponentStatus(ok = false, error = t.message ?: t::class.simpleName)
        }
        components["firebaseAuth"] = firebaseAuthStatus

        // --- Firebase FCM (stub for now) ---
        val firebaseFcmStatus = try {
            FirebaseMessaging.getInstance() // verifies init but not send
            ComponentStatus(ok = true)
        } catch (t: Throwable) {
            ComponentStatus(ok = false, error = "FCM check not implemented: ${t.message ?: t::class.simpleName}")
        }
        components["firebaseFcm"] = firebaseFcmStatus

        // --- Runtime Info ---
        val runtime = ManagementFactory.getRuntimeMXBean()
        val memory = ManagementFactory.getMemoryMXBean()
        val threads = ManagementFactory.getThreadMXBean()

        val runtimeInfo = mapOf(
            "uptimeMs" to runtime.uptime.toString(),
            "heapUsedMb" to (memory.heapMemoryUsage.used / (1024 * 1024)).toString(),
            "heapCommittedMb" to (memory.heapMemoryUsage.committed / (1024 * 1024)).toString(),
            "threads" to threads.threadCount.toString()
        )
        components["runtime"] = ComponentStatus(ok = true, error = runtimeInfo.toString())

        // --- Deployment Metadata ---
        val deploymentInfo = mapOf(
            "env" to (System.getenv("ENV") ?: "unknown"),
            "version" to (System.getenv("VERSION") ?: "dev"),
            "commit" to (System.getenv("GIT_COMMIT") ?: "none")
        )
        components["deployment"] = ComponentStatus(ok = true, error = deploymentInfo.toString())

        val overallOk = components.values.all { it.ok }
        return HealthResult(
            ok = overallOk,
            components = components,
            error = if (overallOk) null else "One or more dependencies failed"
        )
    }
}
