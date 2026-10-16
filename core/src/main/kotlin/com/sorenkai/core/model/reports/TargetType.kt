package com.sorenkai.core.model.reports

import kotlinx.serialization.Serializable

@Serializable
enum class TargetType {
    WRITING,
    POST,
    COMMENT,
}
