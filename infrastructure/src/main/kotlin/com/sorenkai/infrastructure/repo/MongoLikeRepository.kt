package com.sorenkai.infrastructure.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.infrastructure.dto.LikeMongoEntity
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

class MongoLikeRepository(
    databaseProvider: IMongoDatabaseProvider,
) : ILikeRepository {
    private val db: MongoCollection<LikeMongoEntity> =
        databaseProvider.getCollection("likes", LikeMongoEntity::class.java)

    override suspend fun like(
        targetId: String,
        userId: UID,
    ) {
        val filter =
            and(
                eq("targetId", ObjectId(targetId)),
                eq("userId", userId),
            )
        // Idempotent like: update with upsert if it doesn't exist
        db.replaceOne(
            filter,
            LikeMongoEntity(targetId = ObjectId(targetId), userId = userId),
            ReplaceOptions().upsert(true),
        )
    }

    override suspend fun unlike(
        targetId: String,
        userId: UID,
    ) {
        val filter =
            and(
                eq("targetId", ObjectId(targetId)),
                eq("userId", userId),
            )
        db.deleteOne(filter)
    }

    override suspend fun countLikes(targetIds: List<String>): Map<String, Long> {
        if (targetIds.isEmpty()) return emptyMap()

        val objectIds = targetIds.map { ObjectId(it) }
        val pipeline =
            listOf(
                com.mongodb.client.model.Aggregates
                    .match(`in`("targetId", objectIds)),
                com.mongodb.client.model.Aggregates
                    .group(
                        "\$targetId",
                        com.mongodb.client.model.Accumulators
                            .sum("count", 1),
                    ),
            )

        // Using a more manual approach since we want a map back
        val counts = mutableMapOf<String, Long>()
        // Initialize with 0 for all requested IDs
        targetIds.forEach { counts[it] = 0L }

        db.aggregate(pipeline, org.bson.Document::class.java).toList().forEach { doc ->
            val id = doc.getObjectId("_id").toHexString()
            val count = doc.getInteger("count")?.toLong() ?: 0L
            counts[id] = count
        }

        return counts
    }

    override suspend fun getLikedTargets(
        targetIds: List<String>,
        userId: UID,
    ): Set<String> {
        if (targetIds.isEmpty()) return emptySet()

        val objectIds = targetIds.map { ObjectId(it) }
        val filter =
            and(
                eq("userId", userId),
                `in`("targetId", objectIds),
            )

        return db
            .find(filter)
            .toList()
            .map { it.targetId.toHexString() }
            .toSet()
    }
}
