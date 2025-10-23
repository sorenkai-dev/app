package com.sorenkai.api.services

import com.sorenkai.api.model.Discussion
import com.sorenkai.api.model.Writing
import com.sorenkai.api.repo.AuditLogRepository
import com.sorenkai.api.repo.DiscussionRepository
import com.sorenkai.api.repo.WritingRepository
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

private class InMemoryDiscussionRepoSvc : DiscussionRepository() {
    val items = mutableListOf<Discussion>()
    override suspend fun create(item: Discussion): Discussion { items.add(item); return item }
    override suspend fun updateOwn(id: String, uid: String, body: String, hashtags: List<String>, updatedAt: Instant): Boolean {
        val idx = items.indexOfFirst { it.id == id && it.userId == uid }
        if (idx < 0) return false
        val cur = items[idx]
        items[idx] = cur.copy(body = body, hashtags = hashtags, updatedAt = updatedAt)
        return true
    }
}

private class InMemoryWritingRepoSvc : WritingRepository() {
    val items = mutableListOf<Writing>()
    override suspend fun getPublishedBySlug(slug: String): Writing? = items.find { it.slug == slug && it.status == Writing.Status.PUBLISHED }
}

private object NoopAuditRepo : AuditLogRepository()

class DiscussionServiceTest {

    @Test
    fun `create extracts lowercase distinct hashtags`() = kotlinx.coroutines.test.runTest {
        val dRepo = InMemoryDiscussionRepoSvc()
        val wRepo = InMemoryWritingRepoSvc()
        val svc = DiscussionService(dRepo, wRepo, NoopAuditRepo)
        val body = "Hello #World and #world and #Kotlin_Dev!"
        val created = svc.create("u1", DiscussionService.CreateRequest(
            kind = Discussion.Kind.POST, body = body
        ), ip = null)
        assertEquals(listOf("world", "kotlin_dev"), created.hashtags)
    }

    @Test
    fun `create with empty body throws ValidationException`() = kotlinx.coroutines.test.runTest {
        val svc = DiscussionService(InMemoryDiscussionRepoSvc(), InMemoryWritingRepoSvc(), NoopAuditRepo)
        try {
            svc.create("u1", DiscussionService.CreateRequest(kind = Discussion.Kind.POST, body = "   "), null)
            org.junit.jupiter.api.Assertions.fail("Expected ValidationException")
        } catch (e: DiscussionService.ValidationException) {
            // expected
        }
    }

    @Test
    fun `create with too long body throws ValidationException`() = kotlinx.coroutines.test.runTest {
        val svc = DiscussionService(InMemoryDiscussionRepoSvc(), InMemoryWritingRepoSvc(), NoopAuditRepo)
        val long = "x".repeat(5001)
        try {
            svc.create("u1", DiscussionService.CreateRequest(kind = Discussion.Kind.POST, body = long), null)
            org.junit.jupiter.api.Assertions.fail("Expected ValidationException")
        } catch (e: DiscussionService.ValidationException) {
            // expected
        }
    }

    @Test
    fun `comment without parentId throws ValidationException`() = kotlinx.coroutines.test.runTest {
        val svc = DiscussionService(InMemoryDiscussionRepoSvc(), InMemoryWritingRepoSvc(), NoopAuditRepo)
        try {
            svc.create("u1", DiscussionService.CreateRequest(kind = Discussion.Kind.COMMENT, body = "reply", parentId = null), null)
            org.junit.jupiter.api.Assertions.fail("Expected ValidationException")
        } catch (e: DiscussionService.ValidationException) {
            // expected
        }
    }
}
