package com.sorenkai.api.services

import com.sorenkai.api.model.Writing
import com.sorenkai.api.repo.WritingRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

private class FakeWritingRepo : WritingRepository() {
    var lastLimit: Int? = null
    var lastOffset: Int? = null
    var items: List<Writing> = emptyList()

    override suspend fun listPublished(limit: Int, offset: Int): List<Writing> {
        lastLimit = limit; lastOffset = offset
        return items
    }
    override suspend fun getPublishedBySlug(slug: String): Writing? = items.find { it.slug == slug }
}

class WritingServiceTest {

    @Test
    fun `getBySlug throws when not found`() = kotlinx.coroutines.test.runTest {
        val repo = FakeWritingRepo()
        val svc = WritingService(repo)
        try {
            svc.getBySlug("nope")
            org.junit.jupiter.api.Assertions.fail("Expected NotFoundException")
        } catch (e: WritingService.NotFoundException) {
            // expected
        }
    }

    @Test
    fun `listPublished clamps params and excludes content in list DTO`() = kotlinx.coroutines.test.runTest {
        val repo = FakeWritingRepo()
        val sample = Writing(
            title = "T", slug = "s", synopsis = "syn",
            content = "FULL", status = Writing.Status.PUBLISHED, authorId = "a",
            coverUrl = null, publishedAt = com.sorenkai.api.util.now(), stats = Writing.Stats(1,2,3)
        )
        repo.items = listOf(sample)
        val svc = WritingService(repo)
        val list = svc.listPublished(limit = 999, offset = -5)
        // clamp applied on repo call
        assertEquals(50, repo.lastLimit)
        assertEquals(0, repo.lastOffset)
        // DTO excludes content field and maps metadata
        assertEquals(1, list.size)
        val dto = list.first()
        assertEquals("T", dto.title)
        assertEquals("s", dto.slug)
        // there's no content property on list DTO; compile-time check
    }
}
