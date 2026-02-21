package com.sorenkai.infrastructure.repo

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.common.UpdateResult
import com.sorenkai.core.model.community.discussions.Discussion
import com.sorenkai.core.model.community.discussions.DiscussionsStatus
import com.sorenkai.core.model.community.discussions.Kind
import com.sorenkai.core.repo.DiscussionOrder
import com.sorenkai.core.repo.DiscussionWithLikes
import com.sorenkai.core.repo.IDiscussionRepository
import com.sorenkai.infrastructure.dto.DiscussionPgEntity
import com.sorenkai.infrastructure.dto.mappers.toDomain
import com.sorenkai.infrastructure.dto.mappers.toPgEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.jvm.java
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class PostgresDiscussionRepository : IDiscussionRepository {
    val log = LoggerFactory.getLogger(PostgresDiscussionRepository::class.java)

    private fun ResultRow.toPgEntity() =
        DiscussionPgEntity(
            id = this[DiscussionsTable.id].value.toKotlinUuid(),
            kind = this[DiscussionsTable.kind],
            writingId = this[DiscussionsTable.writingId],
            parentId = this[DiscussionsTable.parentId]?.toKotlinUuid(),
            userId = this[DiscussionsTable.userId] ?: "",
            body = this[DiscussionsTable.body],
            lang = this[DiscussionsTable.lang],
            status = this[DiscussionsTable.status],
            reason = this[DiscussionsTable.reason],
            moderatedBy = this[DiscussionsTable.moderatedBy],
            moderatedAt = this[DiscussionsTable.moderatedAt]?.toKotlinInstant(),
            reportedBy = this[DiscussionsTable.reportedBy].toSet(),
            isPinned = this[DiscussionsTable.isPinned],
            isLocked = this[DiscussionsTable.isLocked],
            createdAt = this[DiscussionsTable.createdAt].toKotlinInstant(),
            updatedAt = this[DiscussionsTable.updatedAt]?.toKotlinInstant(),
            deletedAt = this[DiscussionsTable.deletedAt]?.toKotlinInstant(),
            purgeAt = this[DiscussionsTable.purgeAt]?.toKotlinInstant(),
        )

    private fun discussionsWithLikesJoin(): Join =
        DiscussionsTable
            .join(
                LikesTable,
                JoinType.LEFT,
                additionalConstraint = {
                    DiscussionsTable.id eq LikesTable.discussionId
                },
            ).join(
                ReportsTable,
                JoinType.LEFT,
                additionalConstraint = {
                    DiscussionsTable.id eq ReportsTable.targetId
                },
            )

    private data class LikesExpressions(
        val likesCount: ExpressionWithColumnTypeAlias<out Long?>,
        val isLikedByMe: ExpressionWithColumnTypeAlias<out Int?>,
        val isReportedByMe: ExpressionWithColumnTypeAlias<out Int?>,
    )

    private fun likesExpressions(currentUserId: UID?): LikesExpressions {
        val likesCount =
            LikesTable.id.count().alias("likes_count")

        val isLikedByMe =
            if (currentUserId != null) {
                case()
                    .When(LikesTable.userId eq currentUserId, intLiteral(1))
                    .Else(intLiteral(0))
                    .max()
                    .alias("is_liked_by_me")
            } else {
                intLiteral(0).alias("is_liked_by_me")
            }

        val isReportedByMe =
            if (currentUserId != null) {
                case()
                    .When(ReportsTable.createdBy eq currentUserId, intLiteral(1))
                    .Else(intLiteral(0))
                    .max()
                    .alias("is_reported_by_me")
            } else {
                intLiteral(0).alias("is_reported_by_me")
            }

        return LikesExpressions(likesCount, isLikedByMe, isReportedByMe)
    }

    private fun OffsetDateTime.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds(toInstant().toEpochMilli())

    private fun Instant.toJavaOffsetDateTime(): OffsetDateTime =
        OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), ZoneOffset.UTC)

    private fun ResultRow.toDiscussionWithLikes(
        likesCountExpr: ExpressionWithColumnTypeAlias<out Long?>,
        isLikedExpr: ExpressionWithColumnTypeAlias<out Int?>,
        isReportedExpr: ExpressionWithColumnTypeAlias<out Int?>,
    ): DiscussionWithLikes =
        DiscussionWithLikes(
            discussion = toPgEntity().toDomain(),
            likesCount = this[likesCountExpr] ?: 0L,
            isLikedByMe = (this[isLikedExpr] ?: 0) > 0,
            isReportedByMe = (this[isReportedExpr] ?: 0) > 0,
        )

    override suspend fun insert(discussion: Discussion): Discussion =
        dbQuery {
            val entity = discussion.toPgEntity()
            val insertedId =
                DiscussionsTable.insert {
                    it[kind] = entity.kind
                    it[writingId] = entity.writingId
                    it[parentId] = entity.parentId?.toJavaUuid()
                    it[userId] = entity.userId
                    it[lang] = entity.lang
                    it[body] = entity.body
                    it[status] = entity.status
                    it[reason] = entity.reason
                    it[moderatedBy] = entity.moderatedBy
                    it[moderatedAt] = entity.moderatedAt?.toJavaOffsetDateTime()
                    it[reportedBy] = entity.reportedBy.toList()
                    it[isPinned] = entity.isPinned
                    it[isLocked] = entity.isLocked
                    it[createdAt] = entity.createdAt.toJavaOffsetDateTime()
                    it[updatedAt] = entity.updatedAt?.toJavaOffsetDateTime()
                    it[deletedAt] = entity.deletedAt?.toJavaOffsetDateTime()
                    it[purgeAt] = entity.purgeAt?.toJavaOffsetDateTime()
                } get DiscussionsTable.id
            discussion.copy(id = insertedId.value.toKotlinUuid())
        }

    override suspend fun findById(id: Uuid): Discussion? =
        dbQuery {
            DiscussionsTable
                .selectAll()
                .where { DiscussionsTable.id eq id.toJavaUuid() }
                .map { it.toPgEntity().toDomain() }
                .singleOrNull()
        }

    private fun visibilityAndPurgeClause(
        currentUserId: UID?,
        isModerator: Boolean,
    ): Op<Boolean> {
        val now = OffsetDateTime.now()

        val dtAlias = DiscussionsTable.alias("dt_purge")
        val purgeClause =
            (DiscussionsTable.purgeAt.isNull()) or
                (DiscussionsTable.purgeAt.greater(now)) or
                exists(dtAlias.select(dtAlias[DiscussionsTable.id]).where { dtAlias[DiscussionsTable.parentId] eq DiscussionsTable.id })

        val visibilityClause =
            if (isModerator) {
                Op.TRUE
            } else if (currentUserId != null) {
                (DiscussionsTable.deletedAt.isNull()) or (DiscussionsTable.userId eq currentUserId)
            } else {
                DiscussionsTable.deletedAt.isNull()
            }

        return purgeClause.and(visibilityClause)
    }

    override suspend fun findByIdWithLikes(
        id: Uuid,
        currentUserId: UID?,
        isModerator: Boolean,
    ): DiscussionWithLikes? =
        dbQuery {
            val join = discussionsWithLikesJoin()

            val likes = likesExpressions(currentUserId)

            join
                .select(DiscussionsTable.columns + likes.likesCount + likes.isLikedByMe + likes.isReportedByMe)
                .where {
                    (DiscussionsTable.id eq id.toJavaUuid()) and
                        visibilityAndPurgeClause(currentUserId, isModerator)
                }.groupBy(DiscussionsTable.id)
                .map { row ->
                    row.toDiscussionWithLikes(
                        likesCountExpr = likes.likesCount,
                        isLikedExpr = likes.isLikedByMe,
                        isReportedExpr = likes.isReportedByMe,
                    )
                }.singleOrNull()
        }

    override suspend fun listPosts(
        writingId: String?,
        lang: String?,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
        order: DiscussionOrder,
    ): List<DiscussionWithLikes> {
        val start = System.currentTimeMillis()

        val result =
            dbQuery {
                val join = discussionsWithLikesJoin()
                val likes = likesExpressions(currentUserId)

                // 🚨 CRITICAL: make root explicit so partial indexes are usable
                val rootCond = DiscussionsTable.parentId.isNull()

                join
                    .select(DiscussionsTable.columns + likes.likesCount + likes.isLikedByMe + likes.isReportedByMe)
                    .where {
                        val baseCond =
                            (DiscussionsTable.kind eq Kind.POST) and
                                rootCond and
                                (DiscussionsTable.status inList statuses) and
                                visibilityAndPurgeClause(currentUserId, isModerator)

                        val langCond =
                            if (lang != null) DiscussionsTable.lang eq lang else Op.TRUE

                        val writingCond =
                            if (writingId != null) {
                                DiscussionsTable.writingId eq writingId
                            } else {
                                Op.TRUE
                            }

                        baseCond and langCond and writingCond
                    }.groupBy(DiscussionsTable.id)
                    .orderBy(
                        DiscussionsTable.isPinned to SortOrder.DESC,
                        DiscussionsTable.createdAt to
                            when (order) {
                                DiscussionOrder.NEWEST -> SortOrder.DESC
                                DiscussionOrder.OLDEST -> SortOrder.ASC
                            },
                    ).limit(limit)
                    .offset(offset.toLong())
                    .map { row ->
                        row.toDiscussionWithLikes(
                            likesCountExpr = likes.likesCount,
                            isLikedExpr = likes.isLikedByMe,
                            isReportedExpr = likes.isReportedByMe,
                        )
                    }
            }

        log.info(
            "[TIMING] listPosts ${System.currentTimeMillis() - start}ms",
        ) // timer end and report

        return result
    }

    override suspend fun countChildren(parentIds: Set<Uuid>): Map<Uuid, Int> {
        val start = System.currentTimeMillis()
        if (parentIds.isEmpty()) return emptyMap()

        val result =
            dbQuery {
                DiscussionsTable
                    .select(DiscussionsTable.parentId, DiscussionsTable.id.count())
                    .where {
                        (DiscussionsTable.parentId inList parentIds.map { it.toJavaUuid() }) and
                            visibilityAndPurgeClause(null, false)
                    }.groupBy(DiscussionsTable.parentId)
                    .associate {
                        val parentId = it[DiscussionsTable.parentId] ?: error("Parent ID should not be null here")
                        parentId.toKotlinUuid() to it[DiscussionsTable.id.count()].toInt()
                    }
            }

        log.info(
            "[TIMING] countChildren ${System.currentTimeMillis() - start}ms",
        ) // timer end and report

        return result
    }

    override suspend fun listComments(
        parentId: Uuid,
        statuses: Set<DiscussionsStatus>,
        limit: Int,
        offset: Int,
        currentUserId: UID?,
        isModerator: Boolean,
    ): List<DiscussionWithLikes> =
        dbQuery {
            val join = discussionsWithLikesJoin()

            val likes = likesExpressions(currentUserId)

            join
                .select(DiscussionsTable.columns + likes.likesCount + likes.isLikedByMe + likes.isReportedByMe)
                .where {
                    (DiscussionsTable.kind eq Kind.COMMENT) and
                        (DiscussionsTable.parentId eq parentId.toJavaUuid()) and
                        (DiscussionsTable.status inList statuses) and
                        visibilityAndPurgeClause(currentUserId, isModerator)
                }.groupBy(DiscussionsTable.id)
                .orderBy(DiscussionsTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .offset(offset.toLong())
                .map { row ->
                    row.toDiscussionWithLikes(
                        likesCountExpr = likes.likesCount,
                        isLikedExpr = likes.isLikedByMe,
                        isReportedExpr = likes.isReportedByMe,
                    )
                }
        }

    override suspend fun updateBody(
        id: Uuid,
        body: String,
        updatedAt: Instant,
        currentUserId: UID?,
    ): DiscussionWithLikes? =
        dbQuery {
            val updated =
                DiscussionsTable.update({ DiscussionsTable.id eq id.toJavaUuid() }) {
                    it[DiscussionsTable.body] = body
                    it[DiscussionsTable.updatedAt] = updatedAt.toJavaOffsetDateTime()
                }
            if (updated > 0) findByIdWithLikes(id, currentUserId) else null
        }

    override suspend fun setStatus(
        id: Uuid,
        status: DiscussionsStatus,
        moderatedBy: UID?,
        moderatedAt: Instant?,
        reason: String?,
        deletedAt: Instant?,
        purgeAt: Instant?,
        currentUserId: UID?,
    ): DiscussionWithLikes? =
        dbQuery {
            val updated =
                DiscussionsTable.update({ DiscussionsTable.id eq id.toJavaUuid() }) {
                    it[DiscussionsTable.status] = status
                    it[DiscussionsTable.moderatedBy] = moderatedBy
                    it[DiscussionsTable.moderatedAt] = moderatedAt?.toJavaOffsetDateTime()
                    it[DiscussionsTable.reason] = reason
                    it[DiscussionsTable.deletedAt] = deletedAt?.toJavaOffsetDateTime()
                    it[DiscussionsTable.purgeAt] = purgeAt?.toJavaOffsetDateTime()
                }
            if (updated > 0) findByIdWithLikes(id, currentUserId) else null
        }

    override suspend fun setPinned(
        id: Uuid,
        pinned: Boolean,
    ): UpdateResult =
        dbQuery {
            val updated =
                DiscussionsTable.update({ DiscussionsTable.id eq id.toJavaUuid() }) {
                    it[isPinned] = pinned
                }
            if (updated > 0) UpdateResult.Success else UpdateResult.Failure("Discussion not found")
        }

    override suspend fun setLocked(
        id: Uuid,
        locked: Boolean,
    ): UpdateResult =
        dbQuery {
            val updated =
                DiscussionsTable.update({ DiscussionsTable.id eq id.toJavaUuid() }) {
                    it[isLocked] = locked
                }
            if (updated > 0) UpdateResult.Success else UpdateResult.Failure("Discussion not found")
        }

    override suspend fun restore(
        id: Uuid,
        currentUserId: UID?,
    ): DiscussionWithLikes? =
        dbQuery {
            val updated =
                DiscussionsTable.update({ DiscussionsTable.id eq id.toJavaUuid() }) {
                    it[status] = DiscussionsStatus.VISIBLE
                    it[deletedAt] = null
                    it[purgeAt] = null
                }
            if (updated > 0) findByIdWithLikes(id, currentUserId) else null
        }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            val outerStart = System.currentTimeMillis()
            suspendTransaction {
                val innerStart = System.currentTimeMillis()
                val result = block()
                log.info("[TIMING] SQL + mapping took ${System.currentTimeMillis() - innerStart}ms")
                result
            }.also {
                log.info("[TIMING] total suspendTransaction took ${System.currentTimeMillis() - outerStart}ms")
            }
        }
}
