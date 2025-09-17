package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.lt
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.descending
import com.mongodb.client.model.Sorts.orderBy
import com.mongodb.client.model.Sorts.descending as sortDesc
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.dto.paging.decodeCursorOrNull
import com.sorenkai.api.model.Story
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlin.time.Instant

open class StoryRepository {
    private val col: MongoCollection<Story> by lazy { Mongo.db.getCollection("stories") }

    open suspend fun ensureIndexes() {
        // Unique URL per author (one user can’t post identical link twice)
        col.createIndex(ascending("createdBy", "url"), IndexOptions().unique(true))
        // For recent feed pagination: sort by createdAt desc, id desc
        col.createIndex(descending("createdAt", "id"))
        // Helpful for user-scoped operations
        col.createIndex(ascending("createdBy"))
    }

    open suspend fun create(story: Story): Story {
        col.insertOne(story)
        return story
    }

    open suspend fun findById(id: String): Story? = col.find(eq("id", id)).firstOrNull()

    open suspend fun recent(limit: Int = 20): List<Story> =
        col.find().sort(orderBy(sortDesc("createdAt"), sortDesc("id"))).limit(limit).toList()

    open suspend fun listByUser(uid: String): List<Story> =
        col.find(eq("createdBy", uid)).sort(orderBy(sortDesc("createdAt"), sortDesc("id"))).toList()

    open suspend fun deleteByUser(uid: String): Long =
        col.deleteMany(eq("createdBy", uid)).deletedCount

    open suspend fun recentPage(pageSize: Int, afterCursor: String?): Pair<List<Story>, Cursor?> {
        val size = pageSize.coerceIn(1, 100)
        val filter = afterCursor?.let { curStr ->
            val cur = curStr.decodeCursorOrNull()
            if (cur != null) {
                val cursorInstant = Instant.fromEpochMilliseconds(cur.createdAtEpochMs)
                or(
                    lt("createdAt", cursorInstant),
                    and(eq("createdAt", cursorInstant), lt("id", cur.id))
                )
            } else null
        }

        val find = if (filter != null) col.find(filter) else col.find()
        val sorted = find.sort(orderBy(sortDesc("createdAt"), sortDesc("id")))
        val docs = sorted.limit(size + 1).toList()
        val hasMore = docs.size > size
        val items = if (hasMore) docs.dropLast(1) else docs
        val next = if (hasMore) items.lastOrNull()?.let {
            Cursor(createdAtEpochMs = it.createdAt.toEpochMilliseconds(), id = it.id)
        } else null
        return items to next
    }
}
