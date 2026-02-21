package com.sorenkai.writings.services

import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.core.auth.IUserService
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
import com.sorenkai.core.model.writings.WritingPublisher
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import kotlin.time.ExperimentalTime

/**
 * Application service for read-only access to published writings.
 *
 * Provides listing and detail retrieval by ID, mapping domain models to DTOs.
 */
class WritingService(
    private val repo: IWritingRepository,
    private val likes: ILikeRepository,
    private val users: IUserService? = null,
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
    suspend fun listPublished(
        limit: Int,
        offset: Int,
        currentUid: UID? = null,
        lang: String? = null,
    ): List<WritingListItem> {
        val items = repo.listPublished(limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0), lang, currentUid)
        if (items.isEmpty()) return emptyList()

        // Resolve users
        val authorIds = items.map { it.writing.authorId }.distinct()
        val userMap =
            authorIds.associateWith { uid ->
                users?.getUser(uid)
            }

        return items.map {
            it.writing.toListItem(
                isLikedByMe = it.isLikedByMe,
                likesOverride = it.likesCount.toInt(),
                author = userMap[it.writing.authorId],
            )
        }
    }

    /**
     * Gets a published writing by its ID.
     *
     * @throws NotFoundException when no published writing exists for the ID
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getById(
        id: String,
        currentUid: UID? = null,
    ): WritingDetail {
        val item = repo.getPublishedById(id, currentUid) ?: throw NotFoundException("Writing not found")
        val author = users?.getUser(item.writing.authorId)
        return item.writing.toDetail(
            isLikedByMe = item.isLikedByMe,
            likesOverride = item.likesCount.toInt(),
            author = author,
        )
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
        val item = repo.getPublishedBySlug(slug, currentUid) ?: throw NotFoundException("Writing not found")
        val author = users?.getUser(item.writing.authorId)
        return item.writing.toDetail(
            isLikedByMe = item.isLikedByMe,
            likesOverride = item.likesCount.toInt(),
            author = author,
        )
    }

    /**
     * Gets a published writing by language and ID.
     *
     * @throws NotFoundException when no published writing exists for the given language and ID
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getByLangAndId(
        lang: String,
        id: String,
        currentUid: UID? = null,
    ): WritingDetail {
        val item = repo.getPublishedByLangAndId(lang, id, currentUid) ?: throw NotFoundException("Writing not found")
        val author = users?.getUser(item.writing.authorId)
        return item.writing.toDetail(
            isLikedByMe = item.isLikedByMe,
            likesOverride = item.likesCount.toInt(),
            author = author,
        )
    }

    // --- v1.1 additions ---
    suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
        currentUid: UID? = null,
        lang: String? = null,
    ): List<WritingListItem> {
        val items = repo.listByTag(tag, limit.coerceIn(1, MAX_PAGE_SIZE), offset.coerceAtLeast(0), lang, currentUid)
        if (items.isEmpty()) return emptyList()

        // Resolve users
        val authorIds = items.map { it.writing.authorId }.distinct()
        val userMap =
            authorIds.associateWith { uid ->
                users?.getUser(uid)
            }

        return items.map {
            it.writing.toListItem(
                isLikedByMe = it.isLikedByMe,
                likesOverride = it.likesCount.toInt(),
                author = userMap[it.writing.authorId],
            )
        }
    }

    suspend fun getAllTags(): List<String> = repo.distinctTags()

    suspend fun incrementShare(id: String): ShareResponse {
        val updated = repo.incShare(id) ?: throw NotFoundException("Writing not found")
        return ShareResponse(id = updated.id, shareCount = updated.writingsStats.shareCount)
    }

    suspend fun like(
        id: String,
        uid: UID,
    ): LikeResponse {
        val item = repo.getPublishedById(id, uid) ?: throw NotFoundException("Writing not found")
        likes.likeWriting(item.writing.id, uid)
        val count = likes.countWritingLikes(setOf(item.writing.id))[item.writing.id]?.toInt() ?: 0
        return LikeResponse(id = id, likesCount = count)
    }

    suspend fun unlike(
        id: String,
        uid: UID,
    ): LikeResponse {
        val item = repo.getPublishedById(id, uid) ?: throw NotFoundException("Writing not found")
        likes.unlikeWriting(item.writing.id, uid)
        val count = likes.countWritingLikes(setOf(item.writing.id))[item.writing.id]?.toInt() ?: 0
        return LikeResponse(
            id = id,
            likesCount = count,
        )
    }

    suspend fun view(id: String): ViewResponse {
        val updated = repo.incView(id) ?: throw NotFoundException("Writing not found")
        return ViewResponse(id = updated.id, viewCount = updated.writingsStats.viewCount)
    }

    suspend fun clickSale(id: String): SalesClickResponse {
        val updated = repo.incSalesClick(id) ?: throw NotFoundException("Writing not found")
        return SalesClickResponse(id = updated.id, salesClicks = updated.writingsStats.salesClicks)
    }

    // -----------------------------
    // Admin APIs (no auth here)
    // -----------------------------
    suspend fun createAdmin(doc: Writing): Boolean {
        val finalDoc =
            if (doc.writingsStatus != WritingsStatus.DRAFT) {
                val jsonLd = WritingStructuredDataFactory.generate(doc)
                doc.copy(jsonLd = jsonLd)
            } else {
                doc
            }
        repo.create(finalDoc)
        return true
    }

    suspend fun updateSalesLinkAdmin(
        id: String,
        salesLink: String?,
        isbn: String?,
    ): Boolean {
        val existing = repo.findById(id) ?: return false
        val updatedJsonLd =
            existing.jsonLd?.let {
                WritingStructuredDataFactory.updateJsonLdOffers(it, salesLink, isbn)
            }
        return repo.updateSalesLink(id, salesLink, updatedJsonLd)
    }

    suspend fun updateCoverUrlAdmin(
        id: String,
        coverUrl: String?,
    ): Boolean {
        val existing = repo.findById(id) ?: return false
        val updatedJsonLd =
            existing.jsonLd?.let {
                WritingStructuredDataFactory.updateJsonLdImage(it, coverUrl)
            }
        return repo.updateCoverUrl(id, coverUrl, updatedJsonLd)
    }

    suspend fun updatePublisherAdmin(
        id: String,
        publisher: WritingPublisher,
    ): Boolean {
        val existing = repo.findById(id) ?: return false
        val updatedJsonLd =
            existing.jsonLd?.let {
                WritingStructuredDataFactory.updateJsonLdPublisher(it, publisher)
            } ?: WritingStructuredDataFactory.generate(existing, publisher)

        return repo.updatePublisher(id, updatedJsonLd)
    }

    suspend fun deleteAdmin(id: String): Boolean = repo.deleteById(id)

    suspend fun updateStatusAdmin(
        id: String,
        status: WritingsStatus,
    ): Boolean = repo.updateStatus(id, status)

    suspend fun setFeaturedAdmin(
        id: String,
        featured: Boolean,
    ): Boolean = repo.setFeatured(id, featured)
}
