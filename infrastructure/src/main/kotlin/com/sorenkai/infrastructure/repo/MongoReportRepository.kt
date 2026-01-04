package com.sorenkai.infrastructure.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import com.sorenkai.core.repo.IReportRepository
import com.sorenkai.infrastructure.dto.ReportMongoEntity
import com.sorenkai.infrastructure.dto.mappers.toCoreModel
import com.sorenkai.infrastructure.dto.mappers.toMongoEntity
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

class MongoReportRepository(
    databaseProvider: IMongoDatabaseProvider,
) : IReportRepository {
    private val db: MongoCollection<ReportMongoEntity> =
        databaseProvider.getCollection("reports", ReportMongoEntity::class.java)

    override suspend fun create(doc: Report): Report {
        db.insertOne(doc.toMongoEntity())
        return doc
    }

    override suspend fun findById(id: String): Report? = db.find(eq("id", id)).firstOrNull()?.toCoreModel()

    override suspend fun existsOpenOrReviewed(
        createdBy: String,
        targetType: TargetType,
        targetId: String,
    ): Boolean {
        val statuses = setOf(ReportStatus.OPEN, ReportStatus.IN_REVIEW).map { it.name }
        val filter =
            and(
                eq("createdBy", createdBy),
                eq("targetType", targetType.name),
                eq("targetId", targetId),
                `in`("status", statuses),
            )
        return db.find(filter).firstOrNull() != null
    }

    override suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: String?,
        limit: Int,
        offset: Int,
    ): List<Report> {
        val filters = mutableListOf<org.bson.conversions.Bson>()
        status?.takeIf { it.isNotEmpty() }?.let { filters.add(`in`("status", it.map { s -> s.name })) }
        targetType?.let { filters.add(eq("targetType", it.name)) }
        targetId?.let { filters.add(eq("targetId", it)) }
        val combined = if (filters.isEmpty()) org.bson.Document() else and(filters)
        return db
            .find(combined)
            .sort(Sorts.descending("createdAt"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { it.toCoreModel() }
    }

    override suspend fun updateStatus(
        id: String,
        newStatus: ReportStatus,
    ): Boolean {
        val result = db.updateOne(eq("id", id), set("status", newStatus.name))
        return result.matchedCount == 1L
    }
}
