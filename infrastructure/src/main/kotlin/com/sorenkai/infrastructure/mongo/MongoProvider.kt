package com.sorenkai.infrastructure.mongo

import com.mongodb.ConnectionString
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import com.sorenkai.infrastructure.mongo.MongoConfigLoader.loadConfig

object MongoProvider : IMongoDatabaseProvider {
    // This runs the moment Koin touches MongoProvider
    init {
        println("DEBUG: MongoProvider singleton initialized")
    }

    private val config by lazy {
        println("DEBUG: MongoProvider.config lazy block triggered")
        loadConfig()
    }
    val client: MongoClient by lazy {
        MongoClientFactory.create(config)
    }

    override val database: MongoDatabase by lazy {
        val cs = ConnectionString(config.uri)
        client.getDatabase(cs.database ?: "sorenkai")
    }

    override fun <T : Any> getCollection(
        name: String,
        clazz: Class<T>,
    ): MongoCollection<T> = database.getCollection(name, clazz)
}
