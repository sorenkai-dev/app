package com.sorenkai.infrastructure.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Projections
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import com.sorenkai.core.repo.WritingWithLikes
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
    private val likes: ILikeRepository,
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

    override suspend fun getPublishedById(
        id: String,
        currentUserId: UID?,
    ): WritingWithLikes? {
        val doc =
            db
                .find(
                    and(
                        eq("_id", ObjectId(id)),
                        eq("status", WritingsStatus.PUBLISHED.name),
                    ),
                ).firstOrNull()
                ?.toCoreModel() ?: return null
        return enrich(doc, currentUserId)
    }

    override suspend fun getPublishedBySlug(
        slug: String,
        currentUserId: UID?,
    ): WritingWithLikes? {
        val doc =
            db
                .find(
                    and(
                        eq("slug", slug),
                        eq("status", WritingsStatus.PUBLISHED.name),
                    ),
                ).firstOrNull()
                ?.toCoreModel() ?: return null
        return enrich(doc, currentUserId)
    }

    override suspend fun getPublishedByLangAndId(
        lang: String,
        id: String,
        currentUserId: UID?,
    ): WritingWithLikes? {
        val doc =
            db
                .find(
                    and(
                        eq("_id", ObjectId(id)),
                        eq("language", lang),
                        eq("status", WritingsStatus.PUBLISHED.name),
                    ),
                ).firstOrNull()
                ?.toCoreModel() ?: return null
        return enrich(doc, currentUserId)
    }

    override suspend fun listPublished(
        limit: Int,
        offset: Int,
        lang: String?,
        currentUserId: UID?,
    ): List<WritingWithLikes> {
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val skip = offset.coerceAtLeast(0)

        val filter =
            if (lang != null) {
                and(eq("status", WritingsStatus.PUBLISHED.name), eq("language", lang))
            } else {
                eq("status", WritingsStatus.PUBLISHED.name)
            }

        val docs =
            db
                .find(filter)
                .projection(Projections.exclude("content"))
                .sort(
                    Sorts.orderBy(
                        Sorts.descending("publishedAt"),
                        Sorts.descending("createdAt"),
                        Sorts.descending("_id"),
                    ),
                ).skip(skip)
                .limit(size)
                .map { it.toCoreModel() }
                .toList()

        return enrich(docs, currentUserId)
    }

    override suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
        lang: String?,
        currentUserId: UID?,
    ): List<WritingWithLikes> {
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val skip = offset.coerceAtLeast(0)

        val baseFilters =
            mutableListOf(
                eq("status", WritingsStatus.PUBLISHED.name),
                eq("tags", tag),
            )

        if (lang != null) {
            baseFilters.add(eq("language", lang))
        }

        val filter = and(baseFilters)

        val docs =
            db
                .find(filter)
                .projection(Projections.exclude("content"))
                .sort(
                    Sorts.orderBy(
                        Sorts.descending("publishedAt"),
                        Sorts.descending("createdAt"),
                        Sorts.descending("_id"),
                    ),
                ).skip(skip)
                .limit(size)
                .map { it.toCoreModel() }
                .toList()

        return enrich(docs, currentUserId)
    }

    private suspend fun enrich(
        doc: Writing,
        currentUserId: UID?,
    ): WritingWithLikes {
        val id = doc.id
        val count = likes.countWritingLikes(setOf(id))[id] ?: 0L
        val isLiked = currentUserId?.let { likes.getLikedWritings(setOf(id), it).contains(id) } ?: false
        return WritingWithLikes(doc, count, isLiked)
    }

    private suspend fun enrich(
        docs: List<Writing>,
        currentUserId: UID?,
    ): List<WritingWithLikes> {
        if (docs.isEmpty()) return emptyList()
        val ids = docs.map { it.id }.toSet()
        val counts = likes.countWritingLikes(ids)
        val likedByMe = currentUserId?.let { likes.getLikedWritings(ids, it) } ?: emptySet()

        return docs.map {
            WritingWithLikes(
                writing = it,
                likesCount = counts[it.id] ?: 0L,
                isLikedByMe = likedByMe.contains(it.id),
            )
        }
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

    // --- Stats updates by id returning updated document ---
    private val returnAfter = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

    override suspend fun incShare(id: String): Writing? =
        db
            .findOneAndUpdate(eq("_id", ObjectId(id)), inc("stats.shareCount", 1), returnAfter)
            ?.toCoreModel()

    override suspend fun incView(id: String): Writing? =
        db
            .findOneAndUpdate(eq("_id", ObjectId(id)), inc("stats.viewCount", 1), returnAfter)
            ?.toCoreModel()

    override suspend fun incSalesClick(id: String): Writing? =
        db
            .findOneAndUpdate(eq("_id", ObjectId(id)), inc("stats.salesClicks", 1), returnAfter)
            ?.toCoreModel()

    // --- Admin mutations ---
    override suspend fun deleteById(id: String): Boolean = db.deleteOne(eq("_id", ObjectId(id))).deletedCount > 0

    override suspend fun updateStatus(
        id: String,
        status: WritingsStatus,
    ): Boolean = db.updateOne(eq("_id", ObjectId(id)), set("status", status.name)).matchedCount > 0

    override suspend fun setFeatured(
        id: String,
        featured: Boolean,
    ): Boolean = db.updateOne(eq("_id", ObjectId(id)), set("featured", featured)).matchedCount > 0

    override suspend fun updateSalesLink(
        id: String,
        salesLink: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        val updates =
            mutableListOf(
                set("salesLink", salesLink),
            )
        if (jsonLd != null) {
            updates.add(set("jsonLd", jsonLd))
        }
        return db.updateOne(eq("_id", ObjectId(id)), Updates.combine(updates)).matchedCount > 0
    }

    override suspend fun updateCoverUrl(
        id: String,
        coverUrl: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        val updates =
            mutableListOf(
                set("coverUrl", coverUrl),
            )
        if (jsonLd != null) {
            updates.add(set("jsonLd", jsonLd))
        }
        return db.updateOne(eq("_id", ObjectId(id)), Updates.combine(updates)).matchedCount > 0
    }

    override suspend fun updatePublisher(
        id: String,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        if (jsonLd == null) return false
        return db.updateOne(eq("_id", ObjectId(id)), set("jsonLd", jsonLd)).matchedCount > 0
    }
}
