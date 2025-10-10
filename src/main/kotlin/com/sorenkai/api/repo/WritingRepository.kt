package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.model.Writing
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

open class WritingRepository {
    private val col: MongoCollection<Writing> by lazy { Mongo.db.getCollection("writings") }

    open suspend fun ensureIndexes() {
        // Unique slug
        col.createIndex(ascending("slug"), IndexOptions().unique(true))
        // For queries
        col.createIndex(ascending("status"))
        col.createIndex(com.mongodb.client.model.Indexes.descending("publishedAt", "id"))
        col.createIndex(ascending("authorId"))
    }

    open suspend fun create(doc: Writing): Writing {
        col.insertOne(doc)
        return doc
    }

    open suspend fun findBySlug(slug: String): Writing? = col.find(eq("slug", slug)).firstOrNull()

    open suspend fun getPublishedBySlug(slug: String): Writing? =
        col.find(and(eq("slug", slug), eq("status", Writing.Status.PUBLISHED.name))).firstOrNull()

    open suspend fun listPublished(limit: Int, offset: Int): List<Writing> {
        val size = limit.coerceIn(1, 50)
        val skip = offset.coerceAtLeast(0)
        return col.find(eq("status", Writing.Status.PUBLISHED.name))
            .sort(Sorts.orderBy(Sorts.descending("publishedAt"), Sorts.descending("id")))
            .skip(skip)
            .limit(size)
            .toList()
    }
}
