package com.sorenkai.api.dto.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminOk(
    val ok: Boolean = true,
)

@Serializable
data class DashboardSummary(
    val reportsOpen: Int,
    val reportsInReview: Int,
    val reportsResolved: Int,
    val discussionsHiddenOrRemoved: Int,
    val writingsSubmitted: Int,
    val writingsPublished: Int,
)

@Serializable
data class HealthOverview(
    val ok: Boolean,
)

@Serializable
data class AnalyticsStub(
    val ok: Boolean = true,
    val note: String = "Placeholder analytics endpoint",
)




