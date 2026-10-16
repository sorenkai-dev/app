package com.sorenkai.api.mongo

/**
 * Configuration parameters for establishing a MongoDB connection.
 *
 * Holds all settings required by [MongoClientFactory] to create and configure a
 * [com.mongodb.kotlin.client.coroutine.MongoClient]. Instances are created by
 * [MongoConfigLoader] from environment variables or system properties.
 *
 * @property uri full MongoDB connection string, e.g.
 *   `mongodb+srv://user:pass@cluster.mongodb.net/sorenkai`
 * @property maxPool maximum number of connections in the connection pool
 * @property minPool minimum number of idle connections maintained in the pool
 * @property waitQueueMs maximum time in milliseconds a thread will wait for a connection
 *   from the pool before timing out
 * @property connectTimeoutMs maximum time in milliseconds to wait when establishing a
 *   new connection to a MongoDB server
 * @property socketTimeoutMs maximum time in milliseconds to wait for a response on an
 *   existing connection before timing out
 * @property serverSelectionMs maximum time in milliseconds the driver will wait to find
 *   an available server before throwing an exception
 * @property retryWrites whether the driver should automatically retry supported write
 *   operations on transient network errors
 *
 * @see MongoConfigLoader
 * @see MongoClientFactory
 */
data class MongoConfig(
    val uri: String,
    val maxPool: Int,
    val minPool: Int,
    val waitQueueMs: Int,
    val connectTimeoutMs: Int,
    val socketTimeoutMs: Int,
    val serverSelectionMs: Int,
    val retryWrites: Boolean,
)
