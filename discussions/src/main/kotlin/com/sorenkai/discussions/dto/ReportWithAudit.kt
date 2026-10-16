package com.sorenkai.discussions.dto

import kotlinx.serialization.Serializable
import org.kindredhq.discussions.core.domain.model.Report
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class ReportWithAudit(
    val report: Report,
    val moderatedBy: String? = null,
    val moderatedAt: Instant? = null,
    val resolutionReason: String? = null,
)
