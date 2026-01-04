package com.sorenkai.infrastructure.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.codecs.kotlinx.ObjectIdSerializer
import org.bson.types.ObjectId

/**
 * Stores a single user's like relationship to a generic target.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LikeMongoEntity(
    @SerialName("_id") @Serializable(with = ObjectIdSerializer::class) val id: ObjectId = ObjectId(),
    @Serializable(with = ObjectIdSerializer::class) val targetId: ObjectId,
    val userId: String,
)
