package com.sorenkai.infrastructure.repo

import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.model.reports.ReportStatus
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object DiscussionsTable : UUIDTable("discussions") {
    // Database handles gen_random_uuid()

    val kind =
        customEnumeration(
            name = "kind",
            sql = "kind", // Postgres enum type name
            fromDb = { Kind.valueOf(it as String) },
            toDb = { it.name },
        )
    val writingId = text("writing_id").nullable()
    val parentId = uuid("parent_id").references(id).nullable()
    val userId = text("user_id").nullable()
    val lang = text("lang")
    val body = text("body")
    val status =
        customEnumeration(
            name = "status",
            sql = "discussions_status", // use the *actual* enum name in Postgres
            fromDb = { DiscussionsStatus.valueOf(it as String) },
            toDb = { it.name },
        )
    val reason = text("reason").nullable()
    val moderatedBy = text("moderated_by").nullable()

    // Exposed 'timestamp' maps to TIMESTAMPTZ.
    // DB handles now() for created_at
    val moderatedAt = timestampWithTimeZone("moderated_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val purgeAt = timestampWithTimeZone("purge_at").nullable()

    // Fixed to match Supabase snake_case names
    val isPinned = bool("is_pinned").default(false)
    val isLocked = bool("is_locked").default(false)

    // Array support
    val reportedBy = array<String>("reported_by").default(emptyList())
}

object LikesTable : UUIDTable("likes") {
    // Nullable FK → discussions.id
    val discussionId =
        uuid("discussion_id")
            .references(DiscussionsTable.id)
            .nullable()

    // Mongo-backed writing ID
    val writingId = text("writing_id").nullable()

    // Auth provider user ID (Zitadel)
    val userId = text("user_id")
}

object ReportsTable : UUIDTable("reports") {
    val targetType = text("target_type")
    val targetId = uuid("target_id").references(DiscussionsTable.id)
    val reason = text("reason")
    val createdAt = timestampWithTimeZone("created_at")
    val createdBy = text("created_by")
    val status =
        customEnumeration(
            name = "status",
            sql = "report_status",
            fromDb = { ReportStatus.valueOf(it as String) },
            toDb = { it.name },
        )

    init {
        uniqueIndex("idx_reports_user_target", createdBy, targetId)
    }
}
