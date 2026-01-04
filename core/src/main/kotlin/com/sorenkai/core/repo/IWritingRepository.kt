package com.sorenkai.core.repo

import com.sorenkai.core.model.writings.Writing

interface IWritingRepository {
    suspend fun create(doc: Writing): Writing

    suspend fun findById(id: String): Writing?

    suspend fun findBySlug(slug: String): Writing?

    suspend fun getPublishedBySlug(slug: String): Writing?

    suspend fun getPublishedByLangAndSlug(
        lang: String,
        slug: String,
    ): Writing?

    suspend fun listPublished(
        limit: Int,
        offset: Int,
    ): List<Writing>

    suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
    ): List<Writing>

    suspend fun distinctTags(): List<String>

    suspend fun incShare(slug: String): Writing?

    suspend fun incView(slug: String): Writing?

    suspend fun incSalesClick(slug: String): Writing?

    // Admin mutations
    suspend fun deleteBySlug(slug: String): Boolean

    suspend fun updateStatus(
        slug: String,
        status: com.sorenkai.core.model.writings.WritingsStatus,
    ): Boolean

    suspend fun setFeatured(
        slug: String,
        featured: Boolean,
    ): Boolean
}
