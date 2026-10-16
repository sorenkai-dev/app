package com.sorenkai.discussions.dto

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class DiscussionCreateRequest(
    val targetId: Uuid?,
    val parentId: Uuid?,
    val body: String,
    val language: String?
)




