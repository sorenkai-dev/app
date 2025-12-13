package com.sorenkai.community.reports.dto

import com.sorenkai.community.reports.model.TargetType
import kotlinx.serialization.Serializable

/**
 * Request payload for submitting a new abuse/content report.
 *
 * This DTO is consumed by the reports API (`POST /v1/reports`) and is validated
 * by [configureValidation] before being processed by the service layer.
 *
 * @property targetType The type of entity being reported, e.g.
 * [com.sorenkai.api.model.TargetType.DISCUSSION] or
 * [com.sorenkai.api.model.TargetType.WRITING].
 *   - Determines which repository/service is queried to validate the existence of [targetId].
 * @property targetId The unique identifier of the entity being reported.
 *   - Must be a non-blank string.
 * @property reason The explanation provided by the user for why the entity is being reported.
 *   - Must be non-blank.
 *   - Maximum length: 2000 characters (enforced by request validation).
 *
 * ### Example JSON
 * ```json
 * {
 *   "targetType": "DISCUSSION",
 *   "targetId": "64a1bc23de4567890ef12345",
 *   "reason": "This post contains spam and offensive content."
 * }
 * ```
 */
@Serializable
data class ReportCreateRequest(
    val targetType: TargetType,
    val targetId: String,
    val reason: String,
)
