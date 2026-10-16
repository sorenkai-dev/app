package com.sorenkai.infrastructure.mongo

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
