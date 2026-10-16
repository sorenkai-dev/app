package com.sorenkai.writings.services

import com.sorenkai.common.model.auth.UID
import com.sorenkai.writings.model.Writing
import com.sorenkai.writings.model.WritingPublisher
import com.sorenkai.writings.model.WritingsStatus
import com.sorenkai.writings.model.WritingsType
import com.sorenkai.likes.repository.ILikeRepository
import com.sorenkai.writings.repository.IWritingRepository
import com.sorenkai.writings.repository.WritingWithLikes
import com.sorenkai.writings.service.WritingService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.DefaultAsserter.fail
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.jupiter.api.Assertions.assertNotNull as jUnitAssertNotNull

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private class FakeLikeRepository : ILikeRepository {
    val store = ConcurrentHashMap<Uuid, MutableSet<String>>()
    val writingStore = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun likeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) {
        store.computeIfAbsent(discussionId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlikeDiscussion(
        discussionId: Uuid,
        userId: UID,
    ) {
        store[discussionId]?.remove(userId)
    }

    override suspend fun likeWriting(
        writingId: String,
        userId: UID,
    ) {
        writingStore.computeIfAbsent(writingId) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    override suspend fun unlikeWriting(
        writingId: String,
        userId: UID,
    ) {
        writingStore[writingId]?.remove(userId)
    }

    override suspend fun countDiscussionLikes(discussionIds: Set<Uuid>): Map<Uuid, Long> =
        discussionIds.associateWith { tid -> store[tid]?.size?.toLong() ?: 0L }

    override suspend fun countWritingLikes(writingIds: Set<String>): Map<String, Long> =
        writingIds.associateWith { tid -> writingStore[tid]?.size?.toLong() ?: 0L }

    override suspend fun getLikedDiscussions(
        discussionIds: Set<Uuid>,
        userId: UID,
    ): Set<Uuid> = discussionIds.filter { tid -> store[tid]?.contains(userId) == true }.toSet()

    override suspend fun getLikedWritings(
        writingIds: Set<String>,
        userId: UID,
    ): Set<String> = writingIds.filter { tid -> writingStore[tid]?.contains(userId) == true }.toSet()
}

@OptIn(ExperimentalTime::class)
private class FakeWritingRepository(
    private val likes: ILikeRepository,
) : IWritingRepository {
    private val byId = ConcurrentHashMap<String, Writing>()

    override suspend fun create(doc: Writing): Writing {
        byId[doc.id] = doc
        return doc
    }

    override suspend fun findById(id: String): Writing? = byId[id]

    override suspend fun getPublishedById(
        id: String,
        currentUserId: UID?,
    ): WritingWithLikes? {
        val doc =
            byId[id]?.takeIf { it.writingsStatus == WritingsStatus.PUBLISHED || it.writingsStatus == WritingsStatus.SUBMITTED }
                ?: return null
        return enrich(doc, currentUserId)
    }

    override suspend fun getPublishedBySlug(
        slug: String,
        currentUserId: UID?,
    ): WritingWithLikes? {
        val doc =
            byId.values.find { it.slug == slug }?.takeIf {
                it.writingsStatus == WritingsStatus.PUBLISHED ||
                    it.writingsStatus == WritingsStatus.SUBMITTED
            } ?: return null
        return enrich(doc, currentUserId)
    }

    override suspend fun getPublishedByLangAndId(
        lang: String,
        id: String,
        currentUserId: UID?,
    ): WritingWithLikes? = getPublishedById(id, currentUserId)?.takeIf { it.writing.language == lang }

    override suspend fun listPublished(
        limit: Int,
        offset: Int,
        lang: String?,
        currentUserId: UID?,
    ): List<WritingWithLikes> {
        val docs =
            byId.values
                .filter { it.writingsStatus == WritingsStatus.PUBLISHED || it.writingsStatus == WritingsStatus.SUBMITTED }
                .filter { if (lang == null) true else it.language == lang }
                .sortedWith(compareByDescending<Writing> { it.publishedAt }.thenByDescending { it.createdAt }.thenByDescending { it.slug })
                .drop(offset.coerceAtLeast(0))
                .take(limit.coerceIn(1, 50))
                .toList()
        return enrich(docs, currentUserId)
    }

    override suspend fun listByTag(
        tag: String,
        limit: Int,
        offset: Int,
        lang: String?,
        currentUserId: UID?,
    ): List<WritingWithLikes> =
        listPublished(50, 0, lang, currentUserId)
            .filter { it.writing.tags.contains(tag) }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceIn(1, 50))

    private suspend fun enrich(
        doc: Writing,
        currentUserId: UID?,
    ): WritingWithLikes {
        val count = likes.countWritingLikes(setOf(doc.id))[doc.id] ?: 0L
        val isLiked = currentUserId?.let { likes.getLikedWritings(setOf(doc.id), it).contains(doc.id) } ?: false
        return WritingWithLikes(doc, count, isLiked)
    }

    private suspend fun enrich(
        docs: List<Writing>,
        currentUserId: UID?,
    ): List<WritingWithLikes> {
        if (docs.isEmpty()) return emptyList()
        val ids = docs.map { it.id }.toSet()
        val counts = likes.countWritingLikes(ids)
        val likedByMe = currentUserId?.let { likes.getLikedWritings(ids, it) } ?: emptySet()
        return docs.map {
            WritingWithLikes(it, counts[it.id] ?: 0L, likedByMe.contains(it.id))
        }
    }

    override suspend fun distinctTags(): List<String> =
        byId.values
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    override suspend fun incShare(id: String): Writing? =
        byId[id]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(shareCount = cur.writingsStats.shareCount + 1)
            val updated = cur.copy(writingsStats = stats)
            byId[id] = updated
            updated
        }

    override suspend fun incView(id: String): Writing? =
        byId[id]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(viewCount = cur.writingsStats.viewCount + 1)
            val updated = cur.copy(writingsStats = stats)
            byId[id] = updated
            updated
        }

    override suspend fun incSalesClick(id: String): Writing? =
        byId[id]?.let {
            val cur = it
            val stats = cur.writingsStats.copy(salesClicks = cur.writingsStats.salesClicks + 1)
            val updated = cur.copy(writingsStats = stats)
            byId[id] = updated
            updated
        }

    override suspend fun deleteById(id: String): Boolean = byId.remove(id) != null

    override suspend fun updateStatus(
        id: String,
        status: WritingsStatus,
    ): Boolean {
        val cur = byId[id] ?: return false
        byId[id] = cur.copy(writingsStatus = status)
        return true
    }

    override suspend fun setFeatured(
        id: String,
        featured: Boolean,
    ): Boolean {
        val cur = byId[id] ?: return false
        byId[id] = cur.copy(featured = featured)
        return true
    }

    override suspend fun updateSalesLink(
        id: String,
        salesLink: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        val cur = byId[id] ?: return false
        byId[id] = cur.copy(salesLink = salesLink, jsonLd = jsonLd)
        return true
    }

    override suspend fun updateCoverUrl(
        id: String,
        coverUrl: String?,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        val cur = byId[id] ?: return false
        byId[id] = cur.copy(coverUrl = coverUrl, jsonLd = jsonLd)
        return true
    }

    override suspend fun updatePublisher(
        id: String,
        jsonLd: kotlinx.serialization.json.JsonObject?,
    ): Boolean {
        val cur = byId[id] ?: return false
        byId[id] = cur.copy(jsonLd = jsonLd)
        return true
    }
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
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
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            val docA = repo.create(sample(slug = "a"))
            repo.create(sample(slug = "b"))
            repo.create(sample(slug = "c", status = WritingsStatus.DRAFT)) // should be filtered out

            val list = svc.listPublished(limit = 1000, offset = -5)
            assertTrue(list.size >= 2)
            assertEquals("a", list.last().slug)
            jUnitAssertNotNull(list.first().stats)

            val detail = svc.getById(docA.id)
            assertEquals("a", detail.slug)
            assertEquals("u1", detail.authorId)
            assertEquals("seo", detail.seoDescription)
            assertTrue(detail.content.isNotBlank())
        }
    }

    @Test
    fun list_published_filters_by_language() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            repo.create(sample(slug = "en-1", lang = "en"))
            repo.create(sample(slug = "es-1", lang = "es"))

            val enList = svc.listPublished(limit = 10, offset = 0, lang = "en")
            assertEquals(1, enList.size)
            assertEquals("en", enList[0].language)

            val esList = svc.listPublished(limit = 10, offset = 0, lang = "es")
            assertEquals(1, esList.size)
            assertEquals("es", esList[0].language)

            val allList = svc.listPublished(limit = 10, offset = 0, lang = null)
            assertEquals(2, allList.size)
        }
    }

    @Test
    fun get_by_lang_and_id_and_not_found() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            val doc = repo.create(sample(slug = "x", lang = "en"))
            val id = doc.id

            // ok
            val ok = svc.getByLangAndId("en", id)
            assertEquals("x", ok.slug)

            // wrong lang
            try {
                svc.getByLangAndId("fr", id)
                fail("Expected NotFoundException")
            } catch (e: WritingService.NotFoundException) {
                // expected
            }
        }
    }

    @Test
    fun list_by_tag_and_tags_endpoint() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
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
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            val doc = repo.create(sample(slug = "x"))
            val id = doc.id

            val s1 = svc.incrementShare(id)
            assertEquals(1, s1.shareCount)
            val l1 = svc.like(id, "u1")
            assertEquals(1, l1.likesCount)
            val l0 = svc.unlike(id, "u1")
            assertEquals(0, l0.likesCount)
            val v1 = svc.view(id)
            assertEquals(1, v1.viewCount)
            val c1 = svc.clickSale(id)
            assertEquals(1, c1.salesClicks)
        }
    }

    @Test
    fun liked_by_me_logic() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            val doc = repo.create(sample(slug = "liked"))
            val id = doc.id
            val uid = "user-123"

            // 1. Unauthenticated: likedByMe = false
            val anonDetail = svc.getById(id, currentUid = null)
            assertEquals(false, anonDetail.stats.isLikedByMe)
            assertEquals(0, anonDetail.stats.likesCount)

            // 2. Authenticated, not yet liked: likedByMe = false
            val userDetailBefore = svc.getById(id, currentUid = uid)
            assertEquals(false, userDetailBefore.stats.isLikedByMe)

            // 3. Like it
            svc.like(id, uid)

            // 4. Authenticated, liked: likedByMe = true, count = 1
            val userDetailAfter = svc.getById(id, currentUid = uid)
            assertEquals(true, userDetailAfter.stats.isLikedByMe)
            assertEquals(1, userDetailAfter.stats.likesCount)

            // 5. Another user: likedByMe = false, count = 1
            val otherDetail = svc.getById(id, currentUid = "other-user")
            assertEquals(false, otherDetail.stats.isLikedByMe)
            assertEquals(1, otherDetail.stats.likesCount)

            // 6. List check
            val list = svc.listPublished(limit = 10, offset = 0, currentUid = uid)
            assertTrue(list.any { it.id == id && it.stats.isLikedByMe })
        }
    }

    @Test
    fun admin_mutations() {
        runBlocking {
            val likes = FakeLikeRepository()
            val repo = FakeWritingRepository(likes)
            val svc = WritingService(repo, likes, users = null)
            val doc = sample(slug = "x", status = WritingsStatus.PUBLISHED)
            svc.createAdmin(doc)
            val id = doc.id

            val created = repo.findById(id)
            jUnitAssertNotNull(created)
            jUnitAssertNotNull(created?.jsonLd)
            assertEquals(
                "Article",
                created
                    ?.jsonLd
                    ?.get("@type")
                    ?.jsonPrimitive
                    ?.content,
            )

            assertTrue(svc.updateStatusAdmin(id, WritingsStatus.ARCHIVED))
            assertTrue(svc.setFeaturedAdmin(id, true))

            // New JSON-LD partial updates
            assertTrue(svc.updateSalesLinkAdmin(id, "https://amazon.com", "1234567890"))
            val withSales = repo.findById(id)
            assertEquals("https://amazon.com", withSales?.salesLink)
            val offers = withSales?.jsonLd?.get("offers")?.jsonObject
            assertEquals(
                "https://amazon.com",
                offers?.get("url")?.jsonPrimitive?.content,
            )
            assertEquals(
                "1234567890",
                offers?.get("isbn")?.jsonPrimitive?.content,
            )

            assertTrue(svc.updateCoverUrlAdmin(id, "https://example.com/cover.jpg"))
            val withCover = repo.findById(id)
            assertEquals("https://example.com/cover.jpg", withCover?.coverUrl)
            assertEquals(
                "https://example.com/cover.jpg",
                withCover
                    ?.jsonLd
                    ?.get("image")
                    ?.jsonPrimitive
                    ?.content,
            )

            assertTrue(svc.updatePublisherAdmin(id, WritingPublisher.Person("John Doe", "https://johndoe.com")))
            val withPublisher = repo.findById(id)
            val pub = withPublisher?.jsonLd?.get("publisher")?.jsonObject
            assertEquals("Person", pub?.get("@type")?.jsonPrimitive?.content)
            assertEquals("John Doe", pub?.get("name")?.jsonPrimitive?.content)
            assertEquals("https://johndoe.com", pub?.get("url")?.jsonPrimitive?.content)

            assertTrue(svc.deleteAdmin(id))
        }
    }
}




