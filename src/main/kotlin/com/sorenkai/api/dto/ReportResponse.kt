package com.sorenkai.api.dto

import com.sorenkai.api.model.ReportStatus
import com.sorenkai.api.model.TargetType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Response payload representing an abuse/content report stored in the system.
 *
 * This DTO is returned by the reports API (e.g., `POST /v1/reports`, `GET /v1/reports/{id}`).
 * It contains metadata about the report, including who created it, when it was filed,
 * and its current moderation status.
 *
 * @property id The unique identifier of the report (MongoDB ObjectId as a hex string).
 * @property targetType The type of entity that was reported, such as [TargetType.DISCUSSION] or [TargetType.WRITING].
 * @property targetId The unique identifier of the entity being reported.
 * @property reason The explanation provided by the reporting user describing why the content was flagged.
 * @property createdAt The timestamp (UTC) when the report was filed.
 * @property createdBy The UID of the user who created the report.
 * @property status The current moderation status of the report (e.g., [ReportStatus.OPEN], [ReportStatus.RESOLVED]).
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
data class ReportResponse(
    val id: String,
    val targetType: TargetType,
    val targetId: String,
    val reason: String,
    val createdAt: Instant,
    val createdBy: String,
    val status: ReportStatus
)
