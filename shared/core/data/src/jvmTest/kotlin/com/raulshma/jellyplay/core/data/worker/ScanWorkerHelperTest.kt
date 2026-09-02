package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

class ScanWorkerHelperTest {

    private val scanId = "scan-1"

    private val staleEntity = ScanStateEntity(
        scanId = scanId,
        type = "WATCHED",
        configJson = "{}",
        status = ScanPhase.SCANNING.name,
    )

    @Test
    fun `markFailed preserves in-flight progress from the database`() = runTest {
        val dao = mockk<ScanStateDao>(relaxed = true)
        // The DB row reflects real progress persisted by executePaginatedScan
        // during the scan — the stale entity passed to markFailed predates it.
        val currentFromDb = staleEntity.copy(
            progress = 8_000,
            total = 10_000,
            itemsFound = 7_500,
            resultJson = """["a","b"]""",
        )
        coEvery { dao.getById(scanId) } returns currentFromDb

        ScanWorkerHelper.markFailed(dao, staleEntity)

        val updated = slot<ScanStateEntity>()
        coVerify { dao.update(capture(updated)) }
        assertEquals(ScanPhase.FAILED.name, updated.captured.status)
        assertEquals(8_000, updated.captured.progress)
        assertEquals(10_000, updated.captured.total)
        assertEquals(7_500, updated.captured.itemsFound)
        assertEquals("""["a","b"]""", updated.captured.resultJson)
    }

    @Test
    fun `markFailed only changes status, not progress fields`() = runTest {
        val dao = mockk<ScanStateDao>(relaxed = true)
        val currentFromDb = staleEntity.copy(progress = 500, total = 1_000, itemsFound = 500)
        coEvery { dao.getById(scanId) } returns currentFromDb

        ScanWorkerHelper.markFailed(dao, staleEntity)

        val updated = slot<ScanStateEntity>()
        coVerify { dao.update(capture(updated)) }
        assertEquals(ScanPhase.FAILED.name, updated.captured.status)
        assertEquals(500, updated.captured.progress)
        assertEquals(1_000, updated.captured.total)
    }

    @Test
    fun `markFailed falls back to passed entity when row was deleted`() = runTest {
        val dao = mockk<ScanStateDao>(relaxed = true)
        coEvery { dao.getById(scanId) } returns null

        ScanWorkerHelper.markFailed(dao, staleEntity)

        val updated = slot<ScanStateEntity>()
        coVerify { dao.update(capture(updated)) }
        assertEquals(ScanPhase.FAILED.name, updated.captured.status)
        assertEquals(scanId, updated.captured.scanId)
    }

    @Test
    fun `markFailed re-reads current row before writing`() = runTest {
        val dao = mockk<ScanStateDao>(relaxed = true)
        coEvery { dao.getById(scanId) } returns staleEntity.copy(progress = 100)

        ScanWorkerHelper.markFailed(dao, staleEntity)

        // The fix must consult the DB; a regression to the stale-entity copy
        // would skip this read entirely.
        coVerify(exactly = 1) { dao.getById(scanId) }
        coVerify(exactly = 1) { dao.update(any()) }
    }

    // ── executePaginatedScan progress clamping ────────────────────────

    /**
     * Factory for a [ScanStateDao] mock that records every update by status,
     * so a test can assert on the SCANNING vs COMPLETED row independently.
     */
    private fun recordingDao(): Pair<ScanStateDao, MutableList<ScanStateEntity>> {
        val updates = mutableListOf<ScanStateEntity>()
        val dao = mockk<ScanStateDao>(relaxed = true)
        coEvery { dao.update(capture(updates)) } returns Unit
        return dao to updates
    }

    @Test
    fun `executePaginatedScan clamps scanned to total on the final partial batch`() = runTest {
        // Server reports 50 items total; BATCH_SIZE is 200, so the single
        // returned page is the final partial batch. Pre-fix the SCANNING row
        // showed progress = 200 (> total); post-fix it must clamp to 50.
        val (dao, updates) = recordingDao()
        val stubs = (1..50).map { MediaItemStub(itemId = it.toString()) }
        val fetchPage: suspend (Int, Int) -> Pair<Int, List<MediaItemStub>> = { _, _ -> 50 to stubs }

        ScanWorkerHelper.executePaginatedScan(
            scanId = scanId,
            scanStateDao = dao,
            entity = staleEntity,
            isStopped = { false },
            fetchPage = fetchPage,
            mapToStub = { it },
        )

        val scanningRow = updates.single { it.status == ScanPhase.SCANNING.name }
        assertEquals(50, scanningRow.total)
        assertEquals(50, scanningRow.progress) // would have been 200 pre-fix
        assertEquals(50, scanningRow.itemsFound)

        val completedRow = updates.single { it.status == ScanPhase.COMPLETED.name }
        assertEquals(50, completedRow.total)
        assertEquals(50, completedRow.progress)
        assertEquals(50, completedRow.itemsFound)
    }

    @Test
    fun `executePaginatedScan completion snaps progress and total to final found count when no total`() = runTest {
        // Defensive: if the server returns total = 0 but pages yield items,
        // completion falls back to stubs.size so the UI still shows a
        // self-consistent N-of-N.
        val (dao, updates) = recordingDao()
        val stubs = (1..3).map { MediaItemStub(itemId = it.toString()) }
        val fetchPage: suspend (Int, Int) -> Pair<Int, List<MediaItemStub>> = { _, _ -> 0 to stubs }

        ScanWorkerHelper.executePaginatedScan(
            scanId = scanId,
            scanStateDao = dao,
            entity = staleEntity,
            isStopped = { false },
            fetchPage = fetchPage,
            mapToStub = { it },
        )

        val completedRow = updates.single { it.status == ScanPhase.COMPLETED.name }
        assertEquals(3, completedRow.total)
        assertEquals(3, completedRow.progress)
        assertEquals(3, completedRow.itemsFound)
    }
}
