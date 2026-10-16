package com.sorenkai.writings.model

import kotlinx.serialization.Serializable

@Serializable
enum class WritingsStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    SUBMITTED,
}





