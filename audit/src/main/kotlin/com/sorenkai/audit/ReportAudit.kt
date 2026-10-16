package com.sorenkai.audit

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class ReportAudit(
    val reportId: Uuid,
    val moderatedBy: String,
    val moderatedAt: Instant,
    val resolutionReason: String?
)
