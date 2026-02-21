package com.sorenkai.infrastructure.repo

import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import com.sorenkai.core.repo.IReportRepository
import com.sorenkai.infrastructure.dto.ReportPgEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.jvm.java
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class PostgresReportRepository : IReportRepository {
    private val log = LoggerFactory.getLogger(PostgresReportRepository::class.java)

    private fun ResultRow.toPgEntity() =
        ReportPgEntity(
            id = this[ReportsTable.id].value.toKotlinUuid(),
            targetType = TargetType.valueOf(this[ReportsTable.targetType]),
            targetId = this[ReportsTable.targetId].toKotlinUuid(),
            reason = this[ReportsTable.reason],
            createdAt = this[ReportsTable.createdAt].toKotlinInstant(),
            createdBy = this[ReportsTable.createdBy],
            status = this[ReportsTable.status],
        )

    private fun ReportPgEntity.toDomain() =
        Report(
            id = id,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            createdAt = createdAt,
            createdBy = createdBy,
            status = status,
        )

    private fun Report.toPgEntity() =
        ReportPgEntity(
            id = id,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            createdAt = createdAt,
            createdBy = createdBy,
            status = status,
        )

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds(toInstant().toEpochMilli())

    private fun Instant.toJavaOffsetDateTime(): OffsetDateTime =
        OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), ZoneOffset.UTC)

    override suspend fun create(doc: Report): Report =
        dbQuery {
            val entity = doc.toPgEntity()
            ReportsTable.insert {
                it[id] = entity.id.toJavaUuid()
                it[targetType] = entity.targetType.name
                it[targetId] = entity.targetId.toJavaUuid()
                it[reason] = entity.reason
                it[createdAt] = entity.createdAt.toJavaOffsetDateTime()
                it[createdBy] = entity.createdBy
                it[status] = entity.status
            }
            doc
        }

    override suspend fun findById(id: Uuid): Report? =
        dbQuery {
            ReportsTable
                .selectAll()
                .where { ReportsTable.id eq id.toJavaUuid() }
                .map { it.toPgEntity().toDomain() }
                .singleOrNull()
        }

    override suspend fun existsOpenOrReviewed(
        createdBy: String,
        targetType: TargetType,
        targetId: Uuid,
    ): Boolean =
        dbQuery {
            ReportsTable
                .selectAll()
                .where {
                    (ReportsTable.createdBy eq createdBy) and
                        (ReportsTable.targetId eq targetId.toJavaUuid())
                }.any()
        }

    override suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: Uuid?,
        limit: Int,
        offset: Int,
    ): List<Report> =
        dbQuery {
            var query = ReportsTable.selectAll()
            if (status != null) {
                query = query.where { ReportsTable.status inList status }
            }
            if (targetType != null) {
                query = query.where { ReportsTable.targetType eq targetType.name }
            }
            if (targetId != null) {
                query = query.where { ReportsTable.targetId eq targetId.toJavaUuid() }
            }
            query
                .limit(limit)
                .offset(offset.toLong())
                .map { it.toPgEntity().toDomain() }
        }

    override suspend fun updateStatus(
        id: Uuid,
        newStatus: ReportStatus,
    ): Boolean =
        dbQuery {
            ReportsTable.update({ ReportsTable.id eq id.toJavaUuid() }) {
                it[status] = newStatus
            } > 0
        }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction {
                block()
            }
        }
}
