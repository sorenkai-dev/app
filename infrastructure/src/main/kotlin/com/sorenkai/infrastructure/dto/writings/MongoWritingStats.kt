package com.sorenkai.infrastructure.dto.writings

import kotlinx.serialization.Serializable

@Serializable
data class MongoWritingStats(
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val shareCount: Int = 0,
    val salesClicks: Int = 0,
)
