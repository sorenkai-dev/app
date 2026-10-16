package com.sorenkai.writings.service

import com.sorenkai.common.util.Constants.MAX_PAGE_SIZE
import com.sorenkai.common.model.user.IUserService
import com.sorenkai.writings.dto.ShareResponse
import com.sorenkai.writings.dto.ViewResponse
import com.sorenkai.writings.dto.WritingDetail
import com.sorenkai.writings.dto.WritingListItem
import com.sorenkai.writings.dto.toDetail
import com.sorenkai.writings.dto.toListItem
import com.sorenkai.common.model.auth.UID
import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.model.WritingPublisher
import com.sorenkai.writings.model.WritingsStatus
import com.sorenkai.writings.repository.IWritingRepository
import kotlin.time.ExperimentalTime

/**
 * Application service for read-only access to published writings.
 *
 * Provides listing and detail retrieval by ID, mapping domain models to DTOs.
 */
class WritingService(
    private val repo: IWritingRepository,
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
        val authorIds = items.map { it.authorId }.distinct()
        val userMap =
            authorIds.associateWith { uid ->
                users?.getUser(uid)
            }

        return items.map {
            it.toListItem(
                author = userMap[it.authorId],
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
        val author = users?.getUser(item.authorId)
        return item.toDetail(
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
        val author = users?.getUser(item.authorId)
        return item.toDetail(
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
        val author = users?.getUser(item.authorId)
        return item.toDetail(
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
        val authorIds = items.map { it.authorId }.distinct()
        val userMap =
            authorIds.associateWith { uid ->
                users?.getUser(uid)
            }

        return items.map {
            it.toListItem(
                author = userMap[it.authorId],
            )
        }
    }

    suspend fun getAllTags(): List<String> = repo.distinctTags()

    suspend fun incrementShare(id: String): ShareResponse {
        val updated = repo.incShare(id) ?: throw NotFoundException("Writing not found")
        return ShareResponse(id = updated.id, shareCount = updated.writingsStats.shareCount)
    }

    suspend fun view(id: String): ViewResponse {
        val updated = repo.incView(id) ?: throw NotFoundException("Writing not found")
        return ViewResponse(id = updated.id, viewCount = updated.writingsStats.viewCount)
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




