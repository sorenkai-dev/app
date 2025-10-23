package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.descending
import com.mongodb.client.model.Sorts.orderBy
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.dto.paging.decodeCursorOrNull
import com.sorenkai.api.model.Report
import com.sorenkai.api.model.ReportStatus
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import com.mongodb.client.model.Sorts.descending as sortDesc

open class ReportRepository {
    private val col: MongoCollection<Report> by lazy { Mongo.db.getCollection("reports") }

    open suspend fun ensureIndexes() {
        // For moderator feed pagination by status
        col.createIndex(ascending("status"))
        col.createIndex(descending("createdAt", "id"))
        // Helpful for grouping reports by target
        col.createIndex(ascending("targetType", "targetId"))
    }

    open suspend fun create(report: Report): Report {
        col.insertOne(report)
        return report
    }

    open suspend fun findById(id: String): Report? = col.find(eq("id", id)).firstOrNull()

    open suspend fun updateStatus(id: String, status: ReportStatus): Boolean {
        val result = col.updateOne(eq("id", id), org.bson.Document("\$set", org.bson.Document("status", status)))
        return result.matchedCount > 0
    }

    open suspend fun listByUser(uid: String): List<Report> =
        col.find(eq("createdBy", uid)).sort(orderBy(sortDesc("createdAt"), sortDesc("id"))).toList()

    open suspend fun deleteByUser(uid: String): Long =
        col.deleteMany(eq("createdBy", uid)).deletedCount

    open suspend fun pageByStatus(status: ReportStatus, pageSize: Int, afterCursor: String?): Pair<List<Report>, Cursor?> {
        val size = pageSize.coerceIn(1, 100)
        val baseFilter = eq("status", status)
        val filter = afterCursor?.let { curStr ->
            val cur = curStr.decodeCursorOrNull()
            if (cur != null) {
                val instant = Instant.fromEpochMilliseconds(cur.createdAtEpochMs)
                and(
                    baseFilter,
                    orLtAfter(instant, cur.id)
                )
            } else baseFilter
        } ?: baseFilter

        val find = col.find(filter)
        val sorted = find.sort(orderBy(sortDesc("createdAt"), sortDesc("id")))
        val docs = sorted.limit(size + 1).toList()
        val hasMore = docs.size > size
        val items = if (hasMore) docs.dropLast(1) else docs
        val next = if (hasMore) items.lastOrNull()?.let {
            Cursor(createdAtEpochMs = it.createdAt.toEpochMilliseconds(), id = it.id)
        } else null
        return items to next
    }

    private fun orLtAfter(instant: Instant, id: String): org.bson.conversions.Bson =
        com.mongodb.client.model.Filters.or(
            lt("createdAt", instant),
            and(eq("createdAt", instant), lt("id", id))
        )
}
