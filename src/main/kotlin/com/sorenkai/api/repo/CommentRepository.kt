package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.lt
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.descending
import com.mongodb.client.model.Sorts.orderBy
import com.mongodb.client.model.Sorts.descending as sortDesc
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.dto.paging.decodeCursorOrNull
import com.sorenkai.api.model.Comment
import kotlinx.coroutines.flow.toList
import kotlin.time.Instant

open class CommentRepository {
    private val col: MongoCollection<Comment> by lazy { Mongo.db.getCollection("comments") }

    open suspend fun ensureIndexes() {
        // For story-scoped recent comments pagination
        col.createIndex(ascending("storyId"))
        col.createIndex(descending("createdAt", "id"))
    }

    open suspend fun create(comment: Comment): Comment {
        col.insertOne(comment)
        return comment
    }

    open suspend fun listByUser(uid: String): List<Comment> =
        col.find(eq("createdBy", uid)).sort(orderBy(sortDesc("createdAt"), sortDesc("id"))).toList()

    open suspend fun deleteByUser(uid: String): Long =
        col.deleteMany(eq("createdBy", uid)).deletedCount

    open suspend fun listByStoryPage(storyId: String, pageSize: Int, afterCursor: String?): Pair<List<Comment>, Cursor?> {
        val size = pageSize.coerceIn(1, 100)
        val baseFilter = eq("storyId", storyId)
        val filter = afterCursor?.let { curStr ->
            val cur = curStr.decodeCursorOrNull()
            if (cur != null) {
                val cursorInstant = Instant.fromEpochMilliseconds(cur.createdAtEpochMs)
                and(
                    baseFilter,
                    or(
                        lt("createdAt", cursorInstant),
                        and(eq("createdAt", cursorInstant), lt("id", cur.id))
                    )
                )
            } else baseFilter
        } ?: baseFilter

        val docs = col.find(filter)
            .sort(orderBy(sortDesc("createdAt"), sortDesc("id")))
            .limit(size + 1)
            .toList()

        val hasMore = docs.size > size
        val items = if (hasMore) docs.dropLast(1) else docs
        val next = if (hasMore) items.lastOrNull()?.let {
            Cursor(createdAtEpochMs = it.createdAt.toEpochMilliseconds(), id = it.id)
        } else null
        return items to next
    }
}
