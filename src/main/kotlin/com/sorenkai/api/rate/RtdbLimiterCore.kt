package com.sorenkai.api.rate

import com.google.firebase.database.FirebaseDatabase
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Shared RTDB-backed fixed-window rate limiter with safe in-memory fallback.
 *
 * Storage model in RTDB at path: rateLimits/{bucket}/{uid} with fields:
 * - s: window start epoch seconds
 * - c: count in current window
 */
internal object RtdbLimiterCore {
    private val log = KtorSimpleLogger("RateLimiter")

    private data class Window(var s: Long, var c: Int)

    // In-memory fallback store per bucket
    private val localBuckets = ConcurrentHashMap<String, ConcurrentHashMap<String, Window>>()

    suspend fun checkAndIncrement(
        bucket: String,
        uid: String,
        windowSeconds: Long,
        limit: Int
    ): Boolean {
        val rtdbUrl = (System.getenv("FIREBASE_RTDB_URL") ?: System.getenv("FIREBASE_DATABASE_URL"))?.trim().orEmpty()
        return if (rtdbUrl.isNotEmpty()) {
            try {
                checkRtdbAndIncrement(rtdbUrl, bucket, uid, windowSeconds, limit)
            } catch (t: Throwable) {
                log.warn("RTDB rate limit failed; falling back to in-memory: ${t.message}")
                checkLocalAndIncrement(bucket, uid, windowSeconds, limit)
            }
        } else {
            checkLocalAndIncrement(bucket, uid, windowSeconds, limit)
        }
    }

    private suspend fun checkRtdbAndIncrement(
        rtdbUrl: String,
        bucket: String,
        uid: String,
        windowSeconds: Long,
        limit: Int
    ): Boolean {
        val db = FirebaseDatabase.getInstance(rtdbUrl)
        val ref = db.getReference("rateLimits").child(bucket).child(uid)
        val now = System.currentTimeMillis() / 1000

        return suspendCancellableCoroutine { cont ->
            ref.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val map = currentData.value as? Map<*, *>
                    val s = (map?.get("s") as? Number)?.toLong()
                    val c = (map?.get("c") as? Number)?.toInt()
                    val window = if (s == null || c == null || now - s >= windowSeconds) {
                        Window(now, 1)
                    } else {
                        Window(s, c + 1)
                    }
                    currentData.value = mapOf("s" to window.s, "c" to window.c)
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(
                    error: com.google.firebase.database.DatabaseError?,
                    committed: Boolean,
                    currentData: com.google.firebase.database.DataSnapshot?
                ) {
                    if (error != null) {
                        cont.resume(false)
                        return
                    }
                    val map = currentData?.value as? Map<*, *>
                    val c = (map?.get("c") as? Number)?.toInt() ?: 0
                    cont.resume(c <= limit)
                }
            })
        }
    }

    private fun checkLocalAndIncrement(bucket: String, uid: String, windowSeconds: Long, limit: Int): Boolean {
        val store = localBuckets.computeIfAbsent(bucket) { ConcurrentHashMap() }
        val now = System.currentTimeMillis() / 1000
        val win = store.compute(uid) { _, existing ->
            if (existing == null || now - existing.s >= windowSeconds) {
                Window(now, 1)
            } else {
                existing.apply { c += 1 }
            }
        }!!
        return if (win.c <= limit) {
            true
        } else {
            // strict rollback of over-increment
            store.computeIfPresent(uid) { _, w ->
                if (w.s == win.s && w.c > 0) w.c -= 1
                w
            }
            false
        }
    }
}
