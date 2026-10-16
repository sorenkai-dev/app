package com.sorenkai.audit

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Column
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Instant

@OptIn(ExperimentalUuidApi::class)
object ReportAuditTable : IdTable<Uuid>("report_audits") {
    override val id: Column<EntityID<Uuid>> = uuid("report_id").entityId()
    val moderatedBy = text("moderated_by")
    val moderatedAt = timestamp("moderated_at")
    val resolutionReason = text("resolution_reason").nullable()

    override val primaryKey = PrimaryKey(id)
}
