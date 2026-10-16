package com.sorenkai.writings.entity.mapper

import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.model.WritingsStatus
import com.sorenkai.writings.model.WritingsType
import com.sorenkai.writings.entity.WritingMongoEntity
import com.sorenkai.writings.entity.MongoWritingStats
import com.sorenkai.writings.entity.MongoWritingStatus
import com.sorenkai.writings.entity.MongoWritingType
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




