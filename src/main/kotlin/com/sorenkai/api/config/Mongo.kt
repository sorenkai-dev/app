package com.sorenkai.api.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.pojo.PojoCodecProvider


object Mongo {
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
