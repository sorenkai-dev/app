package com.sorenkai.infrastructure.dto.mappers

import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.model.writings.WritingsType
import com.sorenkai.infrastructure.dto.WritingMongoEntity
import com.sorenkai.infrastructure.dto.writings.MongoWritingStats
import com.sorenkai.infrastructure.dto.writings.MongoWritingStatus
import com.sorenkai.infrastructure.dto.writings.MongoWritingType
import org.bson.types.ObjectId
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Writing.toMongoEntity(): WritingMongoEntity =
    WritingMongoEntity(
        id = ObjectId(this.id),
        title = this.title,
        language = this.language,
        slug = this.slug,
        synopsis = this.synopsis,
        content = this.content,
        status = MongoWritingStatus.valueOf(this.writingsStatus.name),
        coverUrl = this.coverUrl,
        authorId = this.authorId,
        publishedAt = this.publishedAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        stats =
            MongoWritingStats(
                commentCount = this.writingsStats.commentCount,
                viewCount = this.writingsStats.viewCount,
                shareCount = this.writingsStats.shareCount,
                salesClicks = this.writingsStats.salesClicks,
            ),
        type = MongoWritingType.valueOf(this.writingsType.name),
        salesLink = this.salesLink,
        tags = this.tags,
        featured = this.featured,
        seoDescription = this.seoDescription,
        jsonLd = this.jsonLd,
    )

@OptIn(ExperimentalTime::class)
fun WritingMongoEntity.toCoreModel(): Writing =
    Writing(
        id = this.id.toHexString(),
        title = this.title,
        language = this.language,
        slug = this.slug,
        synopsis = this.synopsis,
        content = this.content,
        writingsStatus = WritingsStatus.valueOf(this.status.name),
        coverUrl = this.coverUrl,
        authorId = this.authorId,
        publishedAt = this.publishedAt,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        writingsStats =
            Writing.WritingsStats(
                commentCount = this.stats.commentCount,
                viewCount = this.stats.viewCount,
                shareCount = this.stats.shareCount,
                salesClicks = this.stats.salesClicks,
            ),
        writingsType = WritingsType.valueOf(this.type.name),
        salesLink = this.salesLink,
        tags = this.tags,
        featured = this.featured,
        seoDescription = this.seoDescription,
        jsonLd = this.jsonLd,
    )
