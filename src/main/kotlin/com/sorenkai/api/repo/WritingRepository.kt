package com.sorenkai.api.repo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.api.config.Mongo
import com.sorenkai.api.model.Writing
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.map

open class WritingRepository {
    private val col: MongoCollection<Writing> by lazy { Mongo.db.getCollection("writings") }

    open suspend fun ensureIndexes() {
        // Unique slug
        col.createIndex(ascending("slug"), IndexOptions().unique(true))
        // For queries
        col.createIndex(ascending("status"))
        col.createIndex(com.mongodb.client.model.Indexes.descending("publishedAt", "id"))
        col.createIndex(ascending("authorId"))
        // For tags search
        col.createIndex(ascending("tags"))
    }

    open suspend fun create(doc: Writing): Writing {
        col.insertOne(doc)
        return doc
    }

    open suspend fun findBySlug(slug: String): Writing? = col.find(eq("slug", slug)).firstOrNull()

    open suspend fun getPublishedBySlug(slug: String): Writing? =
        col.find(and(
            eq("slug", slug),
            eq("status", Writing.Status.PUBLISHED.name))).firstOrNull()

    open suspend fun getPublishedByLangAndSlug(lang: String, slug: String): Writing? =
        col.find(and(
            eq("slug", slug),
            eq("language", lang),
            eq("status", Writing.Status.PUBLISHED.name)
        )).firstOrNull()

    open suspend fun listPublished(limit: Int, offset: Int): List<Writing> {
        val size = limit.coerceIn(1, 50)
        val skip = offset.coerceAtLeast(0)

        val filter = or(
            eq("status", Writing.Status.PUBLISHED.name),
            eq("status",Writing.Status.SUBMITTED.name)
        )

        return col.find(filter)
            .projection(Projections.exclude("content"))
            .sort(Sorts.orderBy(
                Sorts.descending("publishedAt"),
                Sorts.descending("createdAt"),
                Sorts.descending("slug")
            ))
            .skip(skip)
            .limit(size)
            .toList()
    }

    open suspend fun listByTag(tag: String, limit: Int, offset: Int): List<Writing> {
        val size = limit.coerceIn(1, 50)
        val skip = offset.coerceAtLeast(0)

        val filter = and(
            or(
                eq("status", Writing.Status.PUBLISHED.name),
                eq("status", Writing.Status.SUBMITTED.name)
            ),
            eq("tags", tag)
        )

        return col.find(filter)
            .projection(Projections.exclude("content"))
            .sort(Sorts.orderBy(
                Sorts.descending("publishedAt"),
                Sorts.descending("createdAt"),
                Sorts.descending("slug")
            ))
            .skip(skip)
            .limit(size)
            .toList()
    }

    open suspend fun distinctTags(): List<String> {
        return col.distinct<String>("tags").toList().filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    }

    // --- Stats updates by slug returning updated document ---
    private val returnAfter = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

    open suspend fun incShare(slug: String): Writing? {
        return col.findOneAndUpdate(eq("slug", slug), inc("stats.shareCount", 1), returnAfter)
    }

    open suspend fun incLike(slug: String): Writing? {
        return col.findOneAndUpdate(eq("slug", slug), inc("stats.likeCount", 1), returnAfter)
    }

    open suspend fun decLikeNotBelowZero(slug: String): Writing? {
        // Decrement only when likeCount >= 1; if already 0, return current doc without changing it
        val updated = col.findOneAndUpdate(and(eq("slug", slug), gte("stats.likeCount", 1)), inc("stats.likeCount", -1), returnAfter)
        return updated ?: col.find(eq("slug", slug)).firstOrNull()
    }

    open suspend fun incView(slug: String): Writing? {
        return col.findOneAndUpdate(eq("slug", slug), inc("stats.viewCount", 1), returnAfter)
    }

    open suspend fun incSalesClick(slug: String): Writing? {
        return col.findOneAndUpdate(eq("slug", slug), inc("stats.salesClicks", 1), returnAfter)
    }
}
