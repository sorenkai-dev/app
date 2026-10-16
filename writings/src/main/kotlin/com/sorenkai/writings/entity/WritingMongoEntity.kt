package com.sorenkai.writings.entity

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
@Serializable
data class WritingMongoEntity(
    @SerialName("_id") @Contextual val id: ObjectId = ObjectId(),
    val title: String,
    val language: String,
    val slug: String,
    val synopsis: String? = null,
    val content: String? = null,
    val status: MongoWritingStatus,
    val coverUrl: String? = null,
    val authorId: String,
    @Contextual val publishedAt: Instant? = null,
    @Contextual val createdAt: Instant,
    @Contextual val updatedAt: Instant? = null,
    val stats: MongoWritingStats,
    val type: MongoWritingType,
    val salesLink: String? = null,
    val tags: List<String> = emptyList(),
    val featured: Boolean? = null,
    val seoDescription: String? = null,
    val jsonLd: JsonObject? = null,
)




