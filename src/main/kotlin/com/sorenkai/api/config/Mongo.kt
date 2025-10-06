package com.sorenkai.api.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.pojo.PojoCodecProvider


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

    /**
     * Lazily created [MongoClient] instance with sane defaults for codecs and UUID handling.
     *
     * Falls back to `mongodb://localhost:27017/sorenkai?retryWrites=false` if no URI is provided.
     */
    private val client: MongoClient by lazy {
        val uri = System.getProperty("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: System.getenv("MONGODB_URI")
            ?: "mongodb://localhost:27017/sorenkai?retryWrites=false"
        val pojo = PojoCodecProvider.builder().automatic(true).build()
        val codecs = CodecRegistries.fromProviders(
            KotlinSerializerCodecProvider(), // uses kotlinx-serialization (bson-kotlinx)
            pojo
        )
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uri))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(), codecs
            ))
            .build()
        MongoClient.create(settings)
    }

    /**
     * Lazily created [MongoDatabase] reference.
     *
     * Database name is resolved from the connection string, defaulting to `"sorenkai"`
     * if not explicitly included.
     */
    val db: MongoDatabase by lazy {
        // database name comes from connection string; if absent, use "sorenkai"
        val uri = System.getProperty("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: System.getenv("MONGODB_URI")?.takeIf { it.isNotBlank() }
            ?: "mongodb://localhost:27017/sorenkai?retryWrites=false"
        val cs = ConnectionString(uri)
        val dbName = cs.database ?: "sorenkai"
        client.getDatabase(dbName)
    }
}
