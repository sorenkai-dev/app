package com.sorenkai.community.reports.dto

import com.sorenkai.community.reports.model.ReportStatus
import com.sorenkai.community.reports.model.TargetType
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Response payload representing an abuse/content report stored in the system.
 *
 * This DTO is returned by the reports API (e.g., `POST /v1/reports`, `GET /v1/reports/{id}`).
 * It contains metadata about the report, including who created it, when it was filed,
 * and its current moderation status.
 *
 * @property id The unique identifier of the report (MongoDB ObjectId as a hex string).
 * @property targetType The type of entity that was reported, such as
 * [com.sorenkai.api.model.TargetType.DISCUSSION] or
 * [com.sorenkai.api.model.TargetType.WRITING].
 * @property targetId The unique identifier of the entity being reported.
 * @property reason The explanation provided by the reporting user describing why the content was flagged.
 * @property createdAt The timestamp (UTC) when the report was filed.
 * @property createdBy The UID of the user who created the report.
 * @property status The current moderation status of the report (e.g.,
 * [com.sorenkai.api.model.ReportStatus.OPEN],
 * [com.sorenkai.api.model.ReportStatus.RESOLVED]).
 *
 * ### Example JSON
 * ```json
 * {
 *   "id": "64a1bc23de4567890ef12345",
 *   "targetType": "DISCUSSION",
 *   "targetId": "507f1f77bcf86cd799439011",
 *   "reason": "This comment violates the community guidelines.",
 *   "createdAt": "2025-02-03T14:12:00Z",
 *   "createdBy": "user_12345",
 *   "status": "OPEN"
 * }
 * ```
 */
@Serializable
data class ReportResponse
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val targetType: TargetType,
        val targetId: String,
        val reason: String,
        val createdAt: Instant,
        val createdBy: String,
        val status: ReportStatus,
    )
