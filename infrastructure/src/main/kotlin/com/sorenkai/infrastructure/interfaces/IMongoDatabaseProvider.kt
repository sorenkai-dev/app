package com.sorenkai.infrastructure.interfaces

import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

interface IMongoDatabaseProvider {
    val database: MongoDatabase

    fun <T : Any> getCollection(
        name: String,
        clazz: Class<T>,
    ): MongoCollection<T>
}
