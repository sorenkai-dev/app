package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.dto.paging.Cursor
import com.sorenkai.api.dto.paging.decodeCursorOrNull
import com.sorenkai.api.model.Discussion
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlin.time.Instant

open class DiscussionRepository {
    private val col: MongoCollection<Discussion> by lazy { Mongo.db.getCollection("discussions") }

    open suspend fun ensureIndexes() {
        col.createIndex(ascending("userId"))
        col.createIndex(ascending("writingSlug"))
        col.createIndex(ascending("parentId"))
        col.createIndex(ascending("kind"))
        col.createIndex(com.mongodb.client.model.Indexes.descending("createdAt", "id"))
    }

    open suspend fun create(item: Discussion): Discussion {
        col.insertOne(item)
        return item
    }

    open suspend fun findById(id: String): Discussion? = col.find(eq("id", id)).firstOrNull()

    open suspend fun deleteOwn(id: String, uid: String): Boolean {
        val res = col.deleteOne(com.mongodb.client.model.Filters.and(eq("id", id), eq("userId", uid)))
        return res.deletedCount > 0
    }

    open suspend fun updateOwn(id: String, uid: String, body: String, hashtags: List<String>, updatedAt: Instant): Boolean {
        val res = col.updateOne(
            com.mongodb.client.model.Filters.and(eq("id", id), eq("userId", uid)),
            com.mongodb.client.model.Updates.combine(
                com.mongodb.client.model.Updates.set("body", body),
                com.mongodb.client.model.Updates.set("hashtags", hashtags),
                com.mongodb.client.model.Updates.set("updatedAt", updatedAt)
            )
        )
        return res.matchedCount > 0 && res.modifiedCount > 0
    }

    open suspend fun listRoot(pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> = listByFilter(eq("kind", Discussion.Kind.POST.name), pageSize, cursor)

    open suspend fun listByWriting(writingSlug: String, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> =
        listByFilter(eq("writingSlug", writingSlug), pageSize, cursor)

    open suspend fun listByParent(parentId: String, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> =
        listByFilter(eq("parentId", parentId), pageSize, cursor)

    private suspend fun listByFilter(filter: org.bson.conversions.Bson, pageSize: Int, cursor: String?): Pair<List<Discussion>, Cursor?> {
        val size = pageSize.coerceIn(1, 100)
        val cur = cursor?.decodeCursorOrNull()
        val cursorFilter = cur?.let {
            val ts = Instant.fromEpochMilliseconds(it.createdAtEpochMs)
            com.mongodb.client.model.Filters.or(
                com.mongodb.client.model.Filters.lt("createdAt", ts),
                com.mongodb.client.model.Filters.and(eq("createdAt", ts), com.mongodb.client.model.Filters.lt("id", it.id))
            )
        }
        val f = if (cursorFilter != null) com.mongodb.client.model.Filters.and(filter, cursorFilter) else filter
        val docs = col.find(f).sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("id"))).limit(size + 1).toList()
        val hasMore = docs.size > size
        val items = if (hasMore) docs.dropLast(1) else docs
        val next = if (hasMore) items.lastOrNull()?.let { Cursor(createdAtEpochMs = it.createdAt.toEpochMilliseconds(), id = it.id) } else null
        return items to next
    }
}
