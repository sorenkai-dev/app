package com.sorenkai.core.model.reports

import kotlinx.serialization.Serializable
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Report
    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    constructor(
        val id: Uuid = Uuid.random(),
        val targetType: TargetType,
        val targetId: Uuid,
        val reason: String,
        val createdAt: Instant = now(),
        val createdBy: String,
        val status: ReportStatus = ReportStatus.OPEN,
    )
