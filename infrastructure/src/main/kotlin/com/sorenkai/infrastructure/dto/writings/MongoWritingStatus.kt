package com.sorenkai.infrastructure.dto.writings

import kotlinx.serialization.Serializable

@Serializable
enum class MongoWritingStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    SUBMITTED,
}
