package com.sorenkai.api.model

import com.sorenkai.api.util.newId
import com.sorenkai.api.util.now
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Story(
    val id: String = newId(),
    val title: String,
    val url: String,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Instant = now(),
    val createdBy: String
)
