package com.sorenkai.api.model

import com.sorenkai.api.util.newId
import com.sorenkai.api.util.now
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
enum class TargetType { STORY, COMMENT }

@Serializable
enum class ReportStatus { OPEN, CLOSED }

@Serializable
data class Report(
    val id: String = newId(),
    val targetType: TargetType,
    val targetId: String,
    val reason: String,
    val createdAt: Instant = now(),
    val createdBy: String,
    val status: ReportStatus = ReportStatus.OPEN
)
