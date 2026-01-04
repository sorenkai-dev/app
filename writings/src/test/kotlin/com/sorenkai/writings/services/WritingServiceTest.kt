package com.sorenkai.writings.services

import com.sorenkai.core.model.auth.UID
import com.sorenkai.core.model.writings.Writing
import com.sorenkai.core.model.writings.WritingsStatus
import com.sorenkai.core.model.writings.WritingsType
import com.sorenkai.core.repo.ILikeRepository
import com.sorenkai.core.repo.IWritingRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.DefaultAsserter.fail
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private class FakeLikeRepository : ILikeRepository {
    val store = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun like(
        targetId: String,
        userId: UID,
    ) {
        store.computeIfAbsent(targetId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlike(
        targetId: String,
        userId: UID,
    ) {
        store[targetId]?.remove(userId)
    }

    override suspend fun countLikes(targetIds: List<String>): Map<String, Long> =
        targetIds.associateWith { tid -> store[tid]?.size?.toLong() ?: 0L }

    override suspend fun getLikedTargets(
        targetIds: List<String>,
        userId: UID,
    ): Set<String> = targetIds.filter { tid -> store[tid]?.contains(userId) == true }.toSet()
}

@OptIn(ExperimentalTime::class)
private class FakeWritingRepository : IWritingRepository {
    private val bySlug = ConcurrentHashMap<String, Writing>()
    private val byId = ConcurrentHashMap<String, Writing>()

    override suspend fun create(doc: Writing): Writing {
        bySlug[doc.slug] = doc
        byId[doc.id] = doc
        return doc
    }

    override suspend fun findById(id: String): Writing? = byId[id]

    override suspend fun findBySlug(slug: String): Writing? = bySlug[slug]

    override suspend fun getPublishedBySlug(slug: String): Writing? =
        bySlug[slug]?.takeIf { it.writingsStatus == WritingsStatus.PUBLISHED || it.writingsStatus == WritingsStatus.SUBMITTED }

    override suspend fun getPublishedByLangAndSlug(
        lang: String,
        slug: String,
    ): Writing? = getPublishedBySlug(slug)?.takeIf { it.language == lang }

    override suspend fun listPublished(
        limit: Int,
        offset: Int,
    ): List<Writing> =
        bySlug.values
            .filter { it.writingsStatus == WritingsStatus.PUBLISHED || it.writingsStatus == WritingsStatus.SUBMITTED }
            .sortedWith(compareByDescending<Writing> { it.publishedAt }.thenByDescending { it.createdAt }.thenByDescending { it.slug })
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 50))

    override suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
    ): List<Writing> =
        listPublished(50, 0)
            .filter { it.tags.contains(tag) }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 50))

    override suspend fun distinctTags(): List<String> =
        bySlug.values
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    override suspend fun incShare(slug: String): Writing? =
        bySlug[slug]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(shareCount = cur.writingsStats.shareCount + 1)
            val updated = cur.copy(writingsStats = stats)
            bySlug[slug] = updated
            updated
        }

    override suspend fun incView(slug: String): Writing? =
        bySlug[slug]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(viewCount = cur.writingsStats.viewCount + 1)
            val updated = cur.copy(writingsStats = stats)
            bySlug[slug] = updated
            updated
        }

    override suspend fun incSalesClick(slug: String): Writing? =
        bySlug[slug]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(salesClicks = cur.writingsStats.salesClicks + 1)
            val updated = cur.copy(writingsStats = stats)
            bySlug[slug] = updated
            updated
        }

    override suspend fun deleteBySlug(slug: String): Boolean = bySlug.remove(slug) != null

    override suspend fun updateStatus(
        slug: String,
        status: WritingsStatus,
    ): Boolean {
        val cur = bySlug[slug] ?: return false
        bySlug[slug] = cur.copy(writingsStatus = status)
        return true
    }

    override suspend fun setFeatured(
        slug: String,
        featured: Boolean,
    ): Boolean {
        val cur = bySlug[slug] ?: return false
        bySlug[slug] = cur.copy(featured = featured)
        return true
    }
}

@OptIn(ExperimentalTime::class)
class WritingServiceTest {
    private fun sample(
        slug: String = "hello-world",
        status: WritingsStatus = WritingsStatus.PUBLISHED,
        lang: String = "en",
        type: WritingsType = WritingsType.ARTICLE,
        tags: List<String> = listOf("kotlin", "tech"),
    ): Writing =
        Writing(
            title = "Hello",
            language = lang,
            slug = slug,
            synopsis = "syn",
            content = "content",
            writingsStatus = status,
            coverUrl = null,
            authorId = "u1",
            publishedAt = Clock.System.now(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            writingsType = type,
            salesLink = null,
            tags = tags,
            featured = false,
            seoDescription = "seo",
        )

    @Test
    fun list_and_detail_mapping_and_bounds() {
        runBlocking {
            val repo = FakeWritingRepository()
            val likes = FakeLikeRepository()
            val svc = WritingService(repo, likes)
            repo.create(sample(slug = "a"))
            repo.create(sample(slug = "b"))
            repo.create(sample(slug = "c", status = WritingsStatus.DRAFT)) // should be filtered out

            val list = svc.listPublished(limit = 1000, offset = -5)
            assertTrue(list.size >= 2)
            assertEquals("a", list.last().slug)
            assertNotNull(list.first().stats)

            val detail = svc.getBySlug("a")
            assertEquals("a", detail.slug)
            assertEquals("u1", detail.authorId)
            assertEquals("seo", detail.seoDescription)
            assertTrue(detail.content.isNotBlank())
        }
    }

    @Test
    fun get_by_lang_and_slug_and_not_found() {
        runBlocking {
            val repo = FakeWritingRepository()
            val likes = FakeLikeRepository()
            val svc = WritingService(repo, likes)
            repo.create(sample(slug = "x", lang = "en"))

            // ok
            val ok = svc.getByLangAndSlug("en", "x")
            assertEquals("x", ok.slug)

            // wrong lang
            try {
                svc.getByLangAndSlug("fr", "x")
                fail("Expected NotFoundException")
            } catch (e: WritingService.NotFoundException) {
                // expected
            }
        }
    }

    @Test
    fun list_by_tag_and_tags_endpoint() {
        runBlocking {
            val repo = FakeWritingRepository()
            val likes = FakeLikeRepository()
            val svc = WritingService(repo, likes)
            repo.create(sample(slug = "x", tags = listOf("kotlin", "kmp")))
            repo.create(sample(slug = "y", tags = listOf("kmp")))
            repo.create(sample(slug = "z", tags = emptyList()))

            val kmp = svc.listByTag("kmp", limit = 10, offset = 0)
            assertEquals(2, kmp.size)
            val tags = svc.getAllTags()
            assertTrue(tags.contains("kotlin"))
            assertTrue(tags.contains("kmp"))
        }
    }

    @Test
    fun stats_updates_like_unlike_share_view_sales() {
        runBlocking {
            val repo = FakeWritingRepository()
            val likes = FakeLikeRepository()
            val svc = WritingService(repo, likes)
            repo.create(sample(slug = "x"))

            val s1 = svc.incrementShare("x")
            assertEquals(1, s1.shareCount)
            val l1 = svc.like("x", "u1")
            assertEquals(1, l1.likesCount)
            val l0 = svc.unlike("x", "u1")
            assertEquals(0, l0.likesCount)
            val v1 = svc.view("x")
            assertEquals(1, v1.viewCount)
            val c1 = svc.clickSale("x")
            assertEquals(1, c1.salesClicks)
        }
    }

    @Test
    fun admin_mutations() {
        runBlocking {
            val repo = FakeWritingRepository()
            val likes = FakeLikeRepository()
            val svc = WritingService(repo, likes)
            repo.create(sample(slug = "x"))

            assertTrue(svc.updateStatusAdmin("x", WritingsStatus.ARCHIVED))
            assertTrue(svc.setFeaturedAdmin("x", true))
            assertTrue(svc.deleteAdmin("x"))
        }
    }
}
