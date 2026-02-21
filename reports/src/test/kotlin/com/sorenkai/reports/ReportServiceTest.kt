package com.sorenkai.reports

import com.sorenkai.core.dto.reports.ReportCreateRequest
import com.sorenkai.core.model.reports.Report
import com.sorenkai.core.model.reports.ReportStatus
import com.sorenkai.core.model.reports.TargetType
import com.sorenkai.core.repo.IReportRepository
import com.sorenkai.reports.services.ReportService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private class FakeReportRepository : IReportRepository {
    private val store = ConcurrentHashMap<Uuid, Report>()

    override suspend fun create(doc: Report): Report {
        store[doc.id] = doc
        return doc
    }

    override suspend fun findById(id: Uuid): Report? = store[id]

    override suspend fun existsOpenOrReviewed(
        createdBy: String,
        targetType: TargetType,
        targetId: Uuid,
    ): Boolean =
        store.values.any {
            it.createdBy == createdBy &&
                it.targetType == targetType &&
                it.targetId == targetId &&
                (it.status == ReportStatus.OPEN || it.status == ReportStatus.IN_REVIEW)
        }

    override suspend fun listAdmin(
        status: Set<ReportStatus>?,
        targetType: TargetType?,
        targetId: Uuid?,
        limit: Int,
        offset: Int,
    ): List<Report> =
        store.values
            .filter { status == null || it.status in status }
            .filter { targetType == null || it.targetType == targetType }
            .filter { targetId == null || it.targetId == targetId }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)

    override suspend fun updateStatus(
        id: Uuid,
        newStatus: ReportStatus,
    ): Boolean {
        val cur = store[id] ?: return false
        store[id] = cur.copy(status = newStatus)
        return true
    }
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ReportServiceTest {
    private val repo = FakeReportRepository()
    private val service = ReportService(repo)

    private val targetId1 = Uuid.random()
    private val targetId2 = Uuid.random()

    @Test
    fun `create success`() =
        runBlocking {
            val req = ReportCreateRequest(targetType = "POST", targetId = targetId1.toString(), reason = "spam")
            val resp = service.create("u1", req)
            assertEquals("spam", resp.reason)
            assertEquals(ReportStatus.OPEN, resp.status)

            val found = repo.findById(Uuid.parse(resp.id))
            assertNotNull(found)
            assertEquals("u1", found?.createdBy)
        }

    @Test
    fun `create duplicate conflict`(): Unit =
        runBlocking {
            val req = ReportCreateRequest(targetType = "POST", targetId = targetId1.toString(), reason = "spam")
            service.create("u1", req)

            assertThrows(ReportService.ConflictException::class.java) {
                runBlocking { service.create("u1", req) }
            }
        }

    @Test
    fun `create validation failures`(): Unit =
        runBlocking {
            // Missing uid
            assertThrows(ReportService.ValidationException::class.java) {
                runBlocking { service.create("", ReportCreateRequest("POST", targetId1.toString(), "x")) }
            }
            // Invalid targetType
            assertThrows(ReportService.ValidationException::class.java) {
                runBlocking { service.create("u1", ReportCreateRequest("INVALID", targetId1.toString(), "x")) }
            }
            // Short reason
            assertThrows(ReportService.ValidationException::class.java) {
                runBlocking { service.create("u1", ReportCreateRequest("POST", targetId1.toString(), "")) }
            }
            // Invalid UUID
            assertThrows(ReportService.ValidationException::class.java) {
                runBlocking { service.create("u1", ReportCreateRequest("POST", "not-a-uuid", "x")) }
            }
        }

    @Test
    fun `updateStatus valid transitions`() =
        runBlocking {
            val r = repo.create(Report(targetType = TargetType.POST, targetId = targetId1, reason = "x", createdBy = "u1"))

            // OPEN -> IN_REVIEW
            assertTrue(service.updateStatus("admin", r.id.toString(), ReportStatus.IN_REVIEW))
            assertEquals(ReportStatus.IN_REVIEW, repo.findById(r.id)?.status)

            // IN_REVIEW -> RESOLVED
            assertTrue(service.updateStatus("admin", r.id.toString(), ReportStatus.RESOLVED))
            assertEquals(ReportStatus.RESOLVED, repo.findById(r.id)?.status)
        }

    @Test
    fun `updateStatus invalid transitions`(): Unit =
        runBlocking {
            val r = repo.create(Report(targetType = TargetType.POST, targetId = targetId1, reason = "x", createdBy = "u1"))

            // OPEN -> RESOLVED (invalid, must go via IN_REVIEW)
            assertThrows(ReportService.ConflictException::class.java) {
                runBlocking { service.updateStatus("admin", r.id.toString(), ReportStatus.RESOLVED) }
            }
        }

    @Test
    fun `listAdmin filtering`() =
        runBlocking {
            repo.create(
                Report(targetType = TargetType.POST, targetId = targetId1, reason = "r1", createdBy = "u1", status = ReportStatus.OPEN),
            )
            repo.create(
                Report(
                    targetType = TargetType.WRITING,
                    targetId = targetId2,
                    reason = "r2",
                    createdBy = "u1",
                    status = ReportStatus.RESOLVED,
                ),
            )

            val all = service.listAdmin(null, null, null, 10, 0)
            assertEquals(2, all.size)

            val discussions = service.listAdmin(null, TargetType.POST, null, 10, 0)
            assertEquals(1, discussions.size)
            assertEquals(TargetType.POST, (repo.findById(Uuid.parse(discussions[0].id)) as Report).targetType)

            val resolved = service.listAdmin(setOf(ReportStatus.RESOLVED), null, null, 10, 0)
            assertEquals(1, resolved.size)
            assertEquals(ReportStatus.RESOLVED, resolved[0].status)
        }
}
