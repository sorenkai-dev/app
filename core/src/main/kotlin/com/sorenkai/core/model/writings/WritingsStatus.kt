package com.sorenkai.core.model.writings

import kotlinx.serialization.Serializable

@Serializable
enum class WritingsStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    SUBMITTED,
}
