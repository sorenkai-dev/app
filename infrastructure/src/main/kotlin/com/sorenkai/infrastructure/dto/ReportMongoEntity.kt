package com.sorenkai.infrastructure.dto

import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
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
data class ReportMongoEntity
    constructor(
        @SerialName("_id") @Serializable(with = ObjectIdSerializer::class) val id: ObjectId = ObjectId(),
        val targetType: TargetType,
        val targetId: String,
        val reason: String,
        @Contextual val createdAt: Instant,
        val createdBy: String,
        val status: ReportStatus,
    )
