package com.sorenkai.api.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.sorenkai.writings.util.InstantAsBsonDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.pojo.PojoCodecProvider
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

/**
 * Factory for creating configured [MongoClient] instances.
 *
 * Constructs a [MongoClient] with a codec registry that prioritizes Kotlinx Serialization
 * for all `@Serializable` classes, falling back to the MongoDB default registry and then
 * a POJO codec provider for any remaining types.
 *
 * ### Codec registry priority (highest to lowest):
 * 1. **Kotlinx Serialization** - handles all `@Serializable` annotated classes.
 *    Includes a contextual serializer for [kotlin.time.Instant] via [InstantAsBsonDateTime],
 *    mapping to BSON DateTime for correct storage and retrieval.
 * 2. **MongoDB default registry** - handles primitive types, BSON types, and built-in codecs.
 * 3. **POJO codec provider** - automatic fallback for any non-serializable POJOs.
 *
 * UUID representation is set to [UuidRepresentation.STANDARD] (RFC 4122) for consistency
 * with the Kotlin UUID API.
 *
 * All connection pool, socket, and cluster settings are sourced from [MongoConfig],
 * which is loaded from environment variables via [MongoConfigLoader].
 *
 * @see MongoConfig
 * @see MongoConfigLoader
 * @see MongoProvider
 */
object MongoClientFactory {

    @OptIn(ExperimentalSerializationApi::class)
    fun create(config: MongoConfig): MongoClient {
        val serializersModule = SerializersModule {
            contextual(Instant::class, InstantAsBsonDateTime)
        }

        val kotlinxCodecRegistry = CodecRegistries.fromProviders(
            KotlinSerializerCodecProvider(serializersModule),
        )

        val pojoCodecProvider = PojoCodecProvider.builder()
            .automatic(true)
            .build()

        val combinedCodecRegistry = CodecRegistries.fromRegistries(
            kotlinxCodecRegistry,
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(pojoCodecProvider),
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(config.uri))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(combinedCodecRegistry)
            .applyToConnectionPoolSettings { b ->
                b.maxSize(config.maxPool)
                b.minSize(config.minPool)
                b.maxWaitTime(config.waitQueueMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .applyToSocketSettings { b ->
                b.connectTimeout(config.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                b.readTimeout(config.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .applyToClusterSettings { b ->
                b.serverSelectionTimeout(config.serverSelectionMs.toLong(), TimeUnit.MILLISECONDS)
            }
            .retryWrites(config.retryWrites)
            .build()

        return MongoClient.create(settings)
    }
}
