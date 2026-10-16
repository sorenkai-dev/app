package com.sorenkai.writings.entity

import kotlinx.serialization.Serializable

@Serializable
enum class MongoWritingStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    SUBMITTED,
}




