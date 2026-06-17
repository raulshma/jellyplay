package com.raulshma.jellyplay.core.data.work

import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.ScanPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
