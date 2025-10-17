package com.sorenkai.api.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sorenkai.api.util.DateAsLongSerializer
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.modules.SerializersModule
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.pojo.PojoCodecProvider
import org.bson.codecs.kotlinx.dateTimeSerializersModule
import java.util.Date
import java.util.concurrent.TimeUnit


/**
 * Singleton for MongoDB access with sane defaults for Kindred/SorenKai backend.
 *
 * Responsibilities:
 * - Provides a lazily initialized [MongoClient] configured with:
 *   - Connection string from `MONGODB_URI` (system property or env var).
 *   - Standard UUID representation ([UuidRepresentation.STANDARD]).
 *   - Codec registry that includes:
 *     - Kotlinx Serialization support (via [KotlinSerializerCodecProvider]).
 *     - POJO codec provider for automatic mapping of common types.
 * - Exposes a lazily initialized [MongoDatabase] reference, defaulting to
 *   the database name parsed from the URI or `"sorenkai"` if unspecified.
 *
 * Environment variables / system properties:
 * - `MONGODB_URI`: full MongoDB connection string.
 *   Examples:
 *     - `mongodb://user:pass@host:27017/sorenkai?retryWrites=false`
 *     - `mongodb+srv://cluster0.mongodb.net/sorenkai`
 *
 * Thread safety:
 * - Uses Kotlin’s `by lazy` delegates for safe, on-demand initialization.
 *
 * Usage example:
 * ```
 * val collection = Mongo.db.getCollection("discussions")
 * ```
 */
object Mongo {

    // Visible for tests: snapshot of applied settings (non-sensitive)
    @Volatile
    internal var settingsSnapshot: Map<String, String> = emptyMap()

    /**
     * Lazily created [MongoClient] instance with configured pool sizes and timeouts.
     *
     * Falls back to `mongodb://localhost:27017/sorenkai?retryWrites=false` if no URI is provided.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val client: MongoClient by lazy {
        // 1. Keep the custom module definition with the Date serializer
        val customSerializersModule = SerializersModule {
            include(dateTimeSerializersModule)
        }

        // 2. Define the CodecRegistry that USES the custom module
        //    We'll call this the 'kotlinxCodecRegistry' for clarity
        val kotlinxCodecRegistry = CodecRegistries.fromProviders(
            KotlinSerializerCodecProvider(customSerializersModule)
        )
        val uri = System.getProperty("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: System.getenv("MONGODB_URI")
            ?: "mongodb://localhost:27017/sorenkai?retryWrites=false"

        val maxPool = (System.getenv("MONGO_MAX_POOL_SIZE") ?: "50").toIntOrNull() ?: 50
        val minPool = (System.getenv("MONGO_MIN_POOL_SIZE") ?: "0").toIntOrNull() ?: 0
        val waitQueueMs = (System.getenv("MONGO_WAIT_QUEUE_MS") ?: "5000").toIntOrNull() ?: 5000
        val connectTimeoutMs = (System.getenv("MONGO_CONNECT_TIMEOUT_MS") ?: "3000").toIntOrNull() ?: 3000
        val socketTimeoutMs = (System.getenv("MONGO_SOCKET_TIMEOUT_MS") ?: "5000").toIntOrNull() ?: 5000
        val serverSelectionMs = (System.getenv("MONGO_SERVER_SELECTION_MS") ?: "3000").toIntOrNull() ?: 3000
        val retryWrites = (System.getenv("MONGO_RETRY_WRITES") ?: "true").equals("true", ignoreCase = true)

        val pojo = PojoCodecProvider.builder().automatic(true).build()
        val codecs = CodecRegistries.fromProviders(
            // Use the registry that includes your Date serializer
            kotlinxCodecRegistry, // uses kotlinx-serialization (bson-kotlinx) with Date serializer
            pojo
        )
        val baseRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(), codecs
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(baseRegistry)
            .applyToConnectionPoolSettings { b ->
                b.maxSize(maxPool)
                b.minSize(minPool)
                b.maxWaitTime(waitQueueMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .applyToSocketSettings { b ->
                b.connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                b.readTimeout(socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .applyToClusterSettings { b ->
                b.serverSelectionTimeout(serverSelectionMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .retryWrites(retryWrites)
            .build()

        // Update test-visible snapshot (no secrets)
        settingsSnapshot = mapOf(
            "maxPool" to maxPool.toString(),
            "minPool" to minPool.toString(),
            "waitQueueMs" to waitQueueMs.toString(),
            "connectTimeoutMs" to connectTimeoutMs.toString(),
            "socketTimeoutMs" to socketTimeoutMs.toString(),
            "serverSelectionMs" to serverSelectionMs.toString(),
            "retryWrites" to retryWrites.toString()
        )

        MongoClient.create(settings)
    }

    /**
     * Lazily created [MongoDatabase] reference.
     *
     * Database name is resolved from the connection string, defaulting to `"sorenkai"`
     * if not explicitly included.
     */
    val db: MongoDatabase by lazy {
        val uri = System.getProperty("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: System.getenv("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: "mongodb://localhost:27017/sorenkai?retryWrites=false"
        val cs = ConnectionString(uri)
        val dbName = cs.database ?: "sorenkai"
        client.getDatabase(dbName)
    }

}
