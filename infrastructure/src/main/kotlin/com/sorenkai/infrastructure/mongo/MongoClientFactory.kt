package com.sorenkai.infrastructure.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.kotlinx.dateTimeSerializersModule
import org.bson.codecs.pojo.PojoCodecProvider
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object MongoClientFactory {
    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    fun create(config: MongoConfig): MongoClient {
        // 1. Setup the SerializersModule with both legacy and new Instant support
        val customSerializersModule =
            SerializersModule {
                include(dateTimeSerializersModule) // Supports kotlinx.datetime.Instant
                contextual(Instant::class, InstantAsBsonDateTime) // Supports kotlin.time.Instant
            }

        // 2. Create the Kotlinx Serialization Provider
        val kotlinxCodecRegistry =
            CodecRegistries.fromProviders(
                KotlinSerializerCodecProvider(customSerializersModule),
            )

        // 3. Setup POJO provider as a fallback
        val pojoCodecProvider =
            PojoCodecProvider
                .builder()
                .automatic(true)
                .build()

        // 4. Combine registries with CORRECT PRIORITY.
        // We put kotlinxCodecRegistry FIRST so it handles @Serializable classes
        // before the Default or POJO registries try to intervene.
        val combinedCodecRegistry =
            CodecRegistries.fromRegistries(
                kotlinxCodecRegistry,
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(pojoCodecProvider),
            )

        // --- Mongo settings ---
        val settings =
            MongoClientSettings
                .builder()
                .applyConnectionString(ConnectionString(config.uri))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(combinedCodecRegistry)
                .applyToConnectionPoolSettings { b ->
                    b.maxSize(config.maxPool)
                    b.minSize(config.minPool)
                    b.maxWaitTime(config.waitQueueMs.toLong(), TimeUnit.MILLISECONDS)
                }.applyToSocketSettings { b ->
                    b.connectTimeout(config.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    b.readTimeout(config.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                }.applyToClusterSettings { b ->
                    b.serverSelectionTimeout(config.serverSelectionMs.toLong(), TimeUnit.MILLISECONDS)
                }.retryWrites(config.retryWrites)
                .build()

        return MongoClient.create(settings)
    }
}
