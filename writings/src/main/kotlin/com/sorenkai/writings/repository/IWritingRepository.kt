package com.sorenkai.writings.repository

import com.sorenkai.common.model.auth.UID
import com.sorenkai.writings.model.Writing

interface IWritingRepository {
    suspend fun create(doc: Writing): Writing

    suspend fun findById(id: String): Writing?

    suspend fun getPublishedById(
        id: String,
        currentUserId: UID? = null,
    ): Writing?

    suspend fun getPublishedBySlug(
        slug: String,
        currentUserId: UID? = null,
    ): Writing?

    suspend fun getPublishedByLangAndId(
        lang: String,
        id: String,
        currentUserId: UID? = null,
    ): Writing?

    suspend fun listPublished(
        limit: Int,
        offset: Int,
        lang: String? = null,
        currentUserId: UID? = null,
    ): List<Writing>

    suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
        lang: String? = null,
        currentUserId: UID? = null,
    ): List<Writing>

    suspend fun distinctTags(): List<String>

    suspend fun incShare(id: String): Writing?

    suspend fun incView(id: String): Writing?

    suspend fun incSalesClick(id: String): Writing?

    // Admin mutations
    suspend fun deleteById(id: String): Boolean

    suspend fun updateStatus(
        id: String,
        status: com.sorenkai.writings.model.WritingsStatus,
    ): Boolean

    suspend fun setFeatured(
        id: String,
        featured: Boolean,
    ): Boolean

    suspend fun updateSalesLink(
        id: String,
        salesLink: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean

    suspend fun updateCoverUrl(
        id: String,
        coverUrl: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean

    suspend fun updatePublisher(
        id: String,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean
}





