package com.sorenkai.discussions.dto

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class ReportCreateRequest(
    val targetId: Uuid,
    val reason: String
)




