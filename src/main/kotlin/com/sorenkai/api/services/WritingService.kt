package com.sorenkai.api.services

import com.sorenkai.api.dto.WritingDetail
import com.sorenkai.api.dto.WritingListItem
import com.sorenkai.api.repo.WritingRepository

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
        val docs = repo.listPublished(limit.coerceIn(1, 50), offset.coerceAtLeast(0))
        return docs.map {
            WritingListItem(
                title = it.title,
                slug = it.slug,
                synopsis = it.synopsis,
                coverUrl = it.coverUrl,
                publishedAt = it.publishedAt,
                updatedAt = it.updatedAt
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
            synopsis = doc.synopsis,
            content = doc.content,
            status = doc.status,
            coverUrl = doc.coverUrl,
            authorId = doc.authorId,
            publishedAt = doc.publishedAt,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            stats = WritingDetail.Stats(
                commentCount = doc.stats.commentCount,
                viewCount = doc.stats.viewCount,
                likeCount = doc.stats.likeCount
            )
        )
    }
}
