package com.sorenkai.infrastructure.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Projections
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.infrastructure.dto.WritingMongoEntity
import com.sorenkai.infrastructure.dto.mappers.toCoreModel
import com.sorenkai.infrastructure.dto.mappers.toMongoEntity
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

class MongoWritingRepository(
    databaseProvider: IMongoDatabaseProvider,
) : IWritingRepository {
    private val db: MongoCollection<WritingMongoEntity> =
        databaseProvider.getCollection(
            "writings",
            WritingMongoEntity::class.java,
        )

    private suspend fun ensureIndexes() {
        // Unique slug
        db.createIndex(ascending("slug"), IndexOptions().unique(true))
        // For queries
        db.createIndex(ascending("status"))
        db.createIndex(
            com.mongodb.client.model.Indexes
                .descending("publishedAt", "id"),
        )
        db.createIndex(ascending("authorId"))
        // For tags search
        db.createIndex(ascending("tags"))
    }

    override suspend fun create(doc: Writing): Writing {
        val entity = doc.toMongoEntity()
        db.insertOne(entity)
        return doc
    }

    override suspend fun findById(id: String): Writing? = db.find(eq("_id", ObjectId(id))).firstOrNull()?.toCoreModel()

    override suspend fun findBySlug(slug: String): Writing? = db.find(eq("slug", slug)).firstOrNull()?.toCoreModel()

    override suspend fun getPublishedBySlug(slug: String): Writing? =
        db
            .find(
                and(
                    eq("slug", slug),
                    eq("status", WritingsStatus.PUBLISHED.name),
                ),
            ).firstOrNull()
            ?.toCoreModel()

    override suspend fun getPublishedByLangAndSlug(
        lang: String,
        slug: String,
    ): Writing? =
        db
            .find(
                and(
                    eq("slug", slug),
                    eq("language", lang),
                    eq("status", WritingsStatus.PUBLISHED.name),
                ),
            ).firstOrNull()
            ?.toCoreModel()

    override suspend fun listPublished(
        limit: Int,
        offset: Int,
    ): List<Writing> {
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val skip = offset.coerceAtLeast(0)

        val filter =
            or(
                eq("status", WritingsStatus.PUBLISHED.name),
                eq("status", WritingsStatus.SUBMITTED.name),
            )

        return db
            .find(filter)
            .projection(Projections.exclude("content"))
            .sort(
                Sorts.orderBy(
                    Sorts.descending("publishedAt"),
                    Sorts.descending("createdAt"),
                    Sorts.descending("slug"),
                ),
            ).skip(skip)
            .limit(size)
            .map { it.toCoreModel() }
            .toList()
    }

    override suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
    ): List<Writing> {
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val skip = offset.coerceAtLeast(0)

        val filter =
            and(
                or(
                    eq("status", WritingsStatus.PUBLISHED.name),
                    eq("status", WritingsStatus.SUBMITTED.name),
                ),
                eq("tags", tag),
            )

        return db
            .find(filter)
            .projection(Projections.exclude("content"))
            .sort(
                Sorts.orderBy(
                    Sorts.descending("publishedAt"),
                    Sorts.descending("createdAt"),
                    Sorts.descending("slug"),
                ),
            ).skip(skip)
            .limit(size)
            .map { it.toCoreModel() }
            .toList()
    }

    override suspend fun distinctTags(): List<String> =
        db
            .distinct<String>("tags")
            .toList()
            .filterNotNull()
            .map {
                it.trim()
            }.filter { it.isNotEmpty() }
            .sorted()

    // --- Stats updates by slug returning updated document ---
    private val returnAfter = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

    override suspend fun incShare(slug: String): Writing? =
        db
            .findOneAndUpdate(eq("slug", slug), inc("stats.shareCount", 1), returnAfter)
            ?.toCoreModel()

    override suspend fun incView(slug: String): Writing? =
        db
            .findOneAndUpdate(eq("slug", slug), inc("stats.viewCount", 1), returnAfter)
            ?.toCoreModel()

    override suspend fun incSalesClick(slug: String): Writing? =
        db
            .findOneAndUpdate(eq("slug", slug), inc("stats.salesClicks", 1), returnAfter)
            ?.toCoreModel()

    // --- Admin mutations ---
    override suspend fun deleteBySlug(slug: String): Boolean = db.deleteOne(eq("slug", slug)).deletedCount > 0

    override suspend fun updateStatus(
        slug: String,
        status: WritingsStatus,
    ): Boolean = db.updateOne(eq("slug", slug), set("status", status.name)).matchedCount > 0

    override suspend fun setFeatured(
        slug: String,
        featured: Boolean,
    ): Boolean = db.updateOne(eq("slug", slug), set("featured", featured)).matchedCount > 0
}
