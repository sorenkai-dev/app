package com.sorenkai.api.dto

import com.sorenkai.api.model.TargetType
import kotlinx.serialization.Serializable

@Serializable
data class ReportCreateRequest(
    val targetType: TargetType,
    val targetId: String,
    val reason: String
)
