package com.sorenkai.api.services

import com.sorenkai.api.dto.WritingDetail
import com.sorenkai.api.dto.WritingListItem
import com.sorenkai.api.dto.LikeResponse
import com.sorenkai.api.dto.ShareResponse
import com.sorenkai.api.dto.ViewResponse
import com.sorenkai.api.dto.SalesClickResponse
import com.sorenkai.api.model.Writing
import com.sorenkai.api.repo.WritingRepository
import io.ktor.server.engine.applicationEnvironment

/**
 * Application service for read-only access to published writings.
 *
 * Provides listing and detail retrieval by slug, mapping domain models to DTOs.
 */
class WritingService(
    private val repo: WritingRepository = WritingRepository()
) {
    class NotFoundException(msg: String): RuntimeException(msg)

    /**
     * Lists published writings with fixed maximum page size and non-negative offset.
     *
     * @param limit requested number of items; coerced into [1, 50]
     * @param offset starting offset; negative values coerced to 0
     */
    suspend fun listPublished(limit: Int, offset: Int): List<WritingListItem> {
        applicationEnvironment().log.info("\n\n -------- \n\n Entering WritingService.listPublished \n\n ------")
        val docs = repo.listPublished(limit.coerceIn(1, 50), offset.coerceAtLeast(0))
        return docs.map {
            WritingListItem(
                title = it.title,
                slug = it.slug,
                language = it.language,
                synopsis = it.synopsis,
                status = enumValueOf<Writing.Status>(it.status.name.uppercase()),
                type = enumValueOf<Writing.Type>(it.type.name),
                salesLink = it.salesLink,
                tags = it.tags,
                featured = it.featured,
                seoDescription = it.seoDescription,
                createdAt = it.createdAt,
                publishedAt = it.publishedAt,
                updatedAt = it.updatedAt,
                stats = WritingListItem.Stats(
                    commentCount = it.stats.commentCount,
                    viewCount = it.stats.viewCount,
                    likeCount = it.stats.likeCount,
                    shareCount = it.stats.shareCount,
                    salesClicks = it.stats.salesClicks
                )
            )
        }
    }

    /**
     * Gets a published writing by its slug.
     *
     * @throws NotFoundException when no published writing exists for the slug
     */
    suspend fun getBySlug(slug: String): WritingDetail {
        val doc = repo.getPublishedBySlug(slug) ?: throw NotFoundException("Writing not found")
        return WritingDetail(
            title = doc.title,
            slug = doc.slug,
            language = doc.language,
            synopsis = doc.synopsis,
            type = enumValueOf<Writing.Type>(doc.type.name),
            salesLink = doc.salesLink,
            content = doc.content!!,
            status = enumValueOf<Writing.Status>(doc.status.name),
            coverUrl = doc.coverUrl,
            authorId = doc.authorId,
            publishedAt = doc.publishedAt,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            stats = WritingDetail.Stats(
                commentCount = doc.stats.commentCount,
                viewCount = doc.stats.viewCount,
                likeCount = doc.stats.likeCount,
                shareCount = doc.stats.shareCount,
                salesClicks = doc.stats.salesClicks
            ),
            tags = doc.tags,
            featured = doc.featured,
            seoDescription = doc.seoDescription
        )
    }

    /**
     * Gets a published writing by language and slug.
     *
     * @throws NotFoundException when no published writing exists for the given language and slug
     */
    suspend fun getByLangAndSlug(lang: String, slug: String): WritingDetail {
        val doc = repo.getPublishedByLangAndSlug(lang, slug) ?: throw NotFoundException("Writing not found")
        return WritingDetail(
            title = doc.title,
            slug = doc.slug,
            language = doc.language,
            synopsis = doc.synopsis,
            type = enumValueOf<Writing.Type>(doc.type.name),
            salesLink = doc.salesLink,
            content = doc.content!!,
            status = enumValueOf<Writing.Status>(doc.status.name),
            coverUrl = doc.coverUrl,
            authorId = doc.authorId,
            publishedAt = doc.publishedAt,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            stats = WritingDetail.Stats(
                commentCount = doc.stats.commentCount,
                viewCount = doc.stats.viewCount,
                likeCount = doc.stats.likeCount,
                shareCount = doc.stats.shareCount,
                salesClicks = doc.stats.salesClicks
            ),
            tags = doc.tags,
            featured = doc.featured,
            seoDescription = doc.seoDescription
        )
    }

    // --- v1.1 additions ---
    suspend fun listByTag(tag: String, limit: Int, offset: Int): List<WritingListItem> {
        val docs = repo.listByTag(tag, limit.coerceIn(1, 50), offset.coerceAtLeast(0))
        return docs.map {
            WritingListItem(
                title = it.title,
                slug = it.slug,
                language = it.language,
                synopsis = it.synopsis,
                status = enumValueOf<Writing.Status>(it.status.name.uppercase()),
                type = enumValueOf<Writing.Type>(it.type.name),
                salesLink = it.salesLink,
                tags = it.tags,
                featured = it.featured,
                seoDescription = it.seoDescription,
                createdAt = it.createdAt,
                publishedAt = it.publishedAt,
                updatedAt = it.updatedAt,
                stats = WritingListItem.Stats(
                    commentCount = it.stats.commentCount,
                    viewCount = it.stats.viewCount,
                    likeCount = it.stats.likeCount,
                    shareCount = it.stats.shareCount,
                    salesClicks = it.stats.salesClicks
                )
            )
        }
    }

    suspend fun getAllTags(): List<String> = repo.distinctTags()

    suspend fun incrementShare(slug: String): ShareResponse {
        val updated = repo.incShare(slug) ?: throw NotFoundException("Writing not found")
        return ShareResponse(slug = updated.slug, shareCount = updated.stats.shareCount)
    }

    suspend fun like(slug: String): LikeResponse {
        val updated = repo.incLike(slug) ?: throw NotFoundException("Writing not found")
        return LikeResponse(slug = updated.slug, likeCount = updated.stats.likeCount)
    }

    suspend fun unlike(slug: String): LikeResponse {
        val updated = repo.decLikeNotBelowZero(slug) ?: throw NotFoundException("Writing not found")
        return LikeResponse(slug = updated.slug, likeCount = updated.stats.likeCount)
    }

    suspend fun view(slug: String): ViewResponse {
        val updated = repo.incView(slug) ?: throw NotFoundException("Writing not found")
        return ViewResponse(slug = updated.slug, viewCount = updated.stats.viewCount)
    }

    suspend fun clickSale(slug: String): SalesClickResponse {
        val updated = repo.incSalesClick(slug) ?: throw NotFoundException("Writing not found")
        return SalesClickResponse(slug = updated.slug, salesClicks = updated.stats.salesClicks)
    }
}
