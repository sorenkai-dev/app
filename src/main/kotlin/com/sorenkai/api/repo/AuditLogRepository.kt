package com.sorenkai.api.repo

import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.model.AuditLog

open class AuditLogRepository {
    private val col: MongoCollection<AuditLog> by lazy { Mongo.db.getCollection("auditLog") }

    open suspend fun create(entry: AuditLog): AuditLog {
        col.insertOne(entry)
        return entry
    }
}
