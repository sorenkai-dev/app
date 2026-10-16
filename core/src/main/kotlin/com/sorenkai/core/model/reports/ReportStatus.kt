package com.sorenkai.core.model.reports

import kotlinx.serialization.Serializable

@Serializable
enum class ReportStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
}
