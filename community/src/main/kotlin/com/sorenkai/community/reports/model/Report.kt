package com.sorenkai.community.reports.model

import com.sorenkai.common.util.newId
import kotlinx.serialization.Serializable
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
enum class TargetType { STORY, COMMENT }

@Serializable
enum class ReportStatus { OPEN, CLOSED }

@Serializable
data class Report
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String = newId(),
        val targetType: TargetType,
        val targetId: String,
        val reason: String,
        val createdAt: Instant = now(),
        val createdBy: String,
        val status: ReportStatus = ReportStatus.OPEN,
    )
