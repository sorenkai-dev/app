package com.sorenkai.infrastructure.repo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import com.mongodb.client.model.Updates.unset
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.infrastructure.dto.DiscussionMongoEntity
import com.sorenkai.infrastructure.dto.mappers.toCoreModel
import com.sorenkai.infrastructure.dto.mappers.toMongoEntity
import com.sorenkai.infrastructure.interfaces.IMongoDatabaseProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class MongoDiscussionRepository(
    databaseProvider: IMongoDatabaseProvider,
) : IDiscussionRepository {
    private val db: MongoCollection<DiscussionMongoEntity> =
        databaseProvider.getCollection(
            "discussions",
            DiscussionMongoEntity::class.java,
        )

    override suspend fun insert(discussion: Discussion): Discussion {
        val mongoEntity = discussion.toMongoEntity()
        db.insertOne(mongoEntity)
        return discussion
    }

    override suspend fun findById(id: String): Discussion? = db.find(eq("_id", ObjectId(id))).firstOrNull()?.toCoreModel()

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> {
        val conditions = mutableListOf<Bson>()

        conditions.add(eq("kind", Kind.POST.name))
        conditions.add(`in`("status", statuses.map { it.name }))

        if (lang != null) {
            conditions.add(eq("lang", lang))
        }

        if (writingId != null) {
            conditions.add(eq("writingId", ObjectId(writingId)))
        } else {
            conditions.add(
                or(
                    eq("writingId", null),
                    exists("writingId", false),
                ),
            )
        }

        val filters = and(conditions)

        return db
            .find(filters)
            .sort(Sorts.descending("isPinned", "createdAt"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { it.toCoreModel() }
    }

    override suspend fun listComments(
        parentId: String,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
    ): List<Discussion> {
        val filters =
            and(
                eq("kind", Kind.COMMENT.name),
                eq("parentId", ObjectId(parentId)),
                `in`("status", statuses.map { it.name }),
            )
        return db
            .find(filters)
            .sort(Sorts.ascending("createdAt"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { it.toCoreModel() }
    }

    override suspend fun updateBody(
        id: String,
        body: String,
        updatedAt: Instant,
    ): Boolean {
        val result =
            db.updateOne(
                eq("_id", ObjectId(id)),
                combine(
                    set("body", body),
                    set("updatedAt", updatedAt),
                ),
            )
        return result.matchedCount == 1L
    }

    override suspend fun setStatus(
        id: String,
        status: DiscussionsStatus,
        moderatedBy: UID?,
        moderatedAt: Instant?,
        reason: String?,
    ): Boolean {
        val filter = mutableListOf<Bson>()
        filter.add(set("status", status.name))

        if (moderatedBy != null) {
            filter.add(set("moderatedBy", moderatedBy))
        } else {
            filter.add(unset("moderatedBy"))
        }

        if (moderatedAt != null) {
            filter.add(set("moderatedAt", moderatedAt))
        } else {
            filter.add(unset("moderatedAt"))
        }

        if (reason != null) {
            filter.add(set("reason", reason))
        } else {
            filter.add(unset("reason"))
        }

        val result =
            db.updateOne(
                eq("_id", ObjectId(id)),
                combine(filter),
            )

        return result.matchedCount == 1L
    }

    override suspend fun setPinned(
        id: String,
        pinned: Boolean,
    ): UpdateResult {
        val result = db.updateOne(eq("_id", ObjectId(id)), set("isPinned", pinned))
        return if (result.matchedCount == 0L) {
            UpdateResult.Failure("Discussion not found")
        } else {
            UpdateResult.Success
        }
    }

    override suspend fun setLocked(
        id: String,
        locked: Boolean,
    ): UpdateResult {
        val result =
            db.updateOne(
                eq("_id", ObjectId(id)),
                set("isLocked", locked),
            )
        return if (result.matchedCount == 0L) {
            UpdateResult.Failure("Discussion not found")
        } else {
            UpdateResult.Success
        }
    }
}
