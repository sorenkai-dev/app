package com.sorenkai.audit

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
interface ReportAuditRepository {
    fun insert(audit: ReportAudit)
    fun findByReportId(reportId: Uuid): ReportAudit?
}

@OptIn(ExperimentalUuidApi::class)
class PostgresReportAuditRepository : ReportAuditRepository {
    override fun insert(audit: ReportAudit) {
        transaction {
            ReportAuditTable.insert {
                it[id] = audit.reportId
                it[moderatedBy] = audit.moderatedBy
                it[moderatedAt] = audit.moderatedAt
                it[resolutionReason] = audit.resolutionReason
            }
        }
    }

    override fun findByReportId(reportId: Uuid): ReportAudit? {
        return transaction {
            ReportAuditTable.select { ReportAuditTable.id eq reportId }
                .map {
                    ReportAudit(
                        reportId = it[ReportAuditTable.id].value,
                        moderatedBy = it[ReportAuditTable.moderatedBy],
                        moderatedAt = it[ReportAuditTable.moderatedAt],
                        resolutionReason = it[ReportAuditTable.resolutionReason]
                    )
                }.singleOrNull()
        }
    }
}
