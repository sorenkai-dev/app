package com.sorenkai.audit

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

open class AuditLogRepository {
    open fun create(entry: AuditLog) {
        transaction {
            AuditLogsTable.insert {
                it[id] = entry.id
                it[entityType] = entry.entityType.name
                it[entityId] = entry.entityId
                it[action] = entry.action.name
                it[userId] = entry.userId
                it[timestamp] = entry.timestamp
                it[ip] = entry.ip
            }
        }
    }
}




