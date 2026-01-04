package com.sorenkai.writings.services

import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.dto.writings.LikeResponse
import com.sorenkai.core.dto.writings.SalesClickResponse
import com.sorenkai.core.dto.writings.ShareResponse
import com.sorenkai.core.dto.writings.ViewResponse
import com.sorenkai.core.dto.writings.WritingDetail
import com.sorenkai.core.dto.writings.WritingListItem
import com.sorenkai.core.dto.writings.toDetail
import com.sorenkai.core.dto.writings.toListItem
import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import kotlin.time.ExperimentalTime

/**
 * Application service for read-only access to published writings.
 *
 * Provides listing and detail retrieval by slug, mapping domain models to DTOs.
 */
class WritingService(
    private val repo: IWritingRepository,
    private val likes: ILikeRepository,
) {
    class NotFoundException(
        msg: String,
    ) : RuntimeException(msg)

    /**
     * Lists published writings with fixed maximum page size and non-negative offset.
     *
     * @param limit requested number of items; coerced into [1, 50]
     * @param offset starting offset; negative values coerced to 0
     */
    @OptIn(ExperimentalTime::class)
    suspend fun listPublished(
        limit: Int,
        offset: Int,
        currentUid: UID? = null,
    ): List<WritingListItem> {
        val docs = repo.listPublished(limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0))
        if (docs.isEmpty()) return emptyList()

        val ids = docs.map { it.id }
        val counts = likes.countLikes(ids)
        val likedByMe = currentUid?.let { likes.getLikedTargets(ids, it) } ?: emptySet()

        return docs.map {
            it.toListItem(
                isLikedByMe = likedByMe.contains(it.id),
                likesOverride = counts[it.id]?.toInt(),
            )
        }
    }

    /**
     * Gets a published writing by its slug.
     *
     * @throws NotFoundException when no published writing exists for the slug
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getBySlug(
        slug: String,
        currentUid: UID? = null,
    ): WritingDetail {
        val doc = repo.getPublishedBySlug(slug) ?: throw NotFoundException("Writing not found")
        val isLikedByMe = currentUid?.let { likes.getLikedTargets(listOf(doc.id), it).contains(doc.id) } ?: false
        val count = likes.countLikes(listOf(doc.id))[doc.id]?.toInt() ?: 0
        return doc.toDetail(isLikedByMe = isLikedByMe, likesOverride = count)
    }

    /**
     * Gets a published writing by language and slug.
     *
     * @throws NotFoundException when no published writing exists for the given language and slug
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getByLangAndSlug(
        lang: String,
        slug: String,
        currentUid: UID? = null,
    ): WritingDetail {
        val doc = repo.getPublishedByLangAndSlug(lang, slug) ?: throw NotFoundException("Writing not found")
        val isLikedByMe = currentUid?.let { likes.getLikedTargets(listOf(doc.id), it).contains(doc.id) } ?: false
        val count = likes.countLikes(listOf(doc.id))[doc.id]?.toInt() ?: 0
        return doc.toDetail(isLikedByMe = isLikedByMe, likesOverride = count)
    }

    // --- v1.1 additions ---
    @OptIn(ExperimentalTime::class)
    suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
        currentUid: UID? = null,
    ): List<WritingListItem> {
        val docs = repo.listByTag(tag, limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0))
        if (docs.isEmpty()) return emptyList()

        val ids = docs.map { it.id }
        val counts = likes.countLikes(ids)
        val likedByMe = currentUid?.let { likes.getLikedTargets(ids, it) } ?: emptySet()

        return docs.map {
            it.toListItem(
                isLikedByMe = likedByMe.contains(it.id),
                likesOverride = counts[it.id]?.toInt(),
            )
        }
    }

    suspend fun getAllTags(): List<String> = repo.distinctTags()

    suspend fun incrementShare(slug: String): ShareResponse {
        val updated = repo.incShare(slug) ?: throw NotFoundException("Writing not found")
        return ShareResponse(slug = updated.slug, shareCount = updated.writingsStats.shareCount)
    }

    suspend fun like(
        slug: String,
        uid: UID,
    ): LikeResponse {
        val doc = repo.getPublishedBySlug(slug) ?: throw NotFoundException("Writing not found")
        likes.like(doc.id, uid)
        val count = likes.countLikes(listOf(doc.id))[doc.id]?.toInt() ?: 0
        return LikeResponse(slug = slug, likesCount = count)
    }

    suspend fun unlike(
        slug: String,
        uid: UID,
    ): LikeResponse {
        val doc = repo.getPublishedBySlug(slug) ?: throw NotFoundException("Writing not found")
        likes.unlike(doc.id, uid)
        val count = likes.countLikes(listOf(doc.id))[doc.id]?.toInt() ?: 0
        return LikeResponse(
            slug = slug,
            likesCount = count,
        )
    }

    suspend fun view(slug: String): ViewResponse {
        val updated = repo.incView(slug) ?: throw NotFoundException("Writing not found")
        return ViewResponse(slug = updated.slug, viewCount = updated.writingsStats.viewCount)
    }

    suspend fun clickSale(slug: String): SalesClickResponse {
        val updated = repo.incSalesClick(slug) ?: throw NotFoundException("Writing not found")
        return SalesClickResponse(slug = updated.slug, salesClicks = updated.writingsStats.salesClicks)
    }

    // -----------------------------
    // Admin APIs (no auth here)
    // -----------------------------
    suspend fun createAdmin(doc: Writing): Boolean {
        repo.create(doc)
        return true
    }

    suspend fun deleteAdmin(slug: String): Boolean = repo.deleteBySlug(slug)

    suspend fun updateStatusAdmin(
        slug: String,
        status: WritingsStatus,
    ): Boolean = repo.updateStatus(slug, status)

    suspend fun setFeaturedAdmin(
        slug: String,
        featured: Boolean,
    ): Boolean = repo.setFeatured(slug, featured)
}
