package com.sorenkai.infrastructure.dto

import com.sorenkai.infrastructure.dto.writings.MongoWritingStats
import com.sorenkai.infrastructure.dto.writings.MongoWritingStatus
import com.sorenkai.infrastructure.dto.writings.MongoWritingType
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.kotlinx.ObjectIdSerializer
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
@Serializable
data class WritingMongoEntity
    constructor(
        @SerialName("_id") @Serializable(with = ObjectIdSerializer::class) val id: ObjectId = ObjectId(),
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
    )
