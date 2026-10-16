package com.sorenkai.audit

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Column
import kotlin.time.Instant

object AuditLogsTable : IdTable<String>("audit_logs") {
    override val id: Column<EntityID<String>> = text("id").entityId()
    val entityType = text("entity_type")
    val entityId = text("entity_id")
    val action = text("action")
    val userId = text("user_id")
    val timestamp = timestamp("timestamp")
    val ip = text("ip").nullable()

    override val primaryKey = PrimaryKey(id)
}
