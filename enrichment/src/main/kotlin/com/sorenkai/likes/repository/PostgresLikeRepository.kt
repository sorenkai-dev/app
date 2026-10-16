package com.sorenkai.likes.repository

import com.sorenkai.common.model.auth.UID
import com.sorenkai.likes.table.LikesTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class PostgresLikeRepository : ILikeRepository {
    override suspend fun likeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) = dbQuery {
        val alreadyLiked =
            LikesTable
                .select(LikesTable.id)
                .where { (LikesTable.discussionId eq discussionId) and (LikesTable.userId eq userId) }
                .any()

        if (!alreadyLiked) {
            LikesTable.insert {
                it[LikesTable.discussionId] = discussionId
                it[LikesTable.userId] = userId
            }
        }
        Unit
    }

    override suspend fun unlikeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) = dbQuery {
        LikesTable.deleteWhere {
            (LikesTable.discussionId eq discussionId) and (LikesTable.userId eq userId)
        }
        Unit
    }

    override suspend fun likeWriting(
        writingId: String,
        userId: UID,
    ) = dbQuery {
        val alreadyLiked =
            LikesTable
                .select(LikesTable.id)
                .where { (LikesTable.writingId eq writingId) and (LikesTable.userId eq userId) }
                .any()

        if (!alreadyLiked) {
            LikesTable.insert {
                it[LikesTable.writingId] = writingId
                it[LikesTable.userId] = userId
            }
        }
        Unit
    }

    override suspend fun unlikeWriting(
        writingId: String,
        userId: UID,
    ) = dbQuery {
        LikesTable.deleteWhere {
            (LikesTable.writingId eq writingId) and (LikesTable.userId eq userId)
        }
        Unit
    }

    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> =
        dbQuery {
            if (discussionIds.isEmpty()) return@dbQuery emptyMap()
            val uuids = discussionIds.map { it.toJavaUuid() }

            LikesTable
                .select(LikesTable.discussionId, LikesTable.discussionId.count())
                .where { LikesTable.discussionId inList discussionIds.toList() }
                .groupBy(LikesTable.discussionId)
                .associate {
                    it[LikesTable.discussionId]!! to it[LikesTable.discussionId.count()]
                }
        }

    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> =
        dbQuery {
            if (writingIds.isEmpty()) return@dbQuery emptyMap()

            LikesTable
                .select(LikesTable.writingId, LikesTable.writingId.count())
                .where { LikesTable.writingId inList writingIds.toList() }
                .groupBy(LikesTable.writingId)
                .associate {
                    it[LikesTable.writingId]!! to it[LikesTable.writingId.count()]
                }
        }

    override suspend fun getLikedDiscussions(
        discussionIds: Set<Uuid>,
        userId: UID,
    ): Set<Uuid> =
        dbQuery {
            if (discussionIds.isEmpty()) return@dbQuery emptySet()
            val uuids = discussionIds.map { it.toJavaUuid() }

            LikesTable
                .select(LikesTable.discussionId)
                .where {
                    (LikesTable.discussionId inList discussionIds.toList()) and (LikesTable.userId eq userId)
                }.mapNotNull { it[LikesTable.discussionId] }
                .toSet()
        }

    override suspend fun getLikedWritings(
        writingIds: Set<String>,
        userId: UID,
    ): Set<String> =
        dbQuery {
            if (writingIds.isEmpty()) return@dbQuery emptySet()

            LikesTable
                .select(LikesTable.writingId)
                .where {
                    (LikesTable.writingId inList writingIds.toList()) and (LikesTable.userId eq userId)
                }.mapNotNull { it[LikesTable.writingId] }
                .toSet()
        }

    private suspend fun <T> dbQuery(block: suspend () -> T): T = suspendTransaction { block() }
}




