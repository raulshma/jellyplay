package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanStateDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var scanStateDao: ScanStateDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        scanStateDao = database.scanStateDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createScan(
        scanId: String,
        status: String = "RUNNING",
        progress: Int = 0,
        total: Int = 0,
        itemsFound: Int = 0,
        resultJson: String? = null,
        createdAt: Long = 1_000L,
    ) = ScanStateEntity(
        scanId = scanId,
        type = "DUPLICATE",
        configJson = """{"libraryId":"lib-1"}""",
        status = status,
        progress = progress,
        total = total,
        itemsFound = itemsFound,
        resultJson = resultJson,
        createdAt = createdAt,
    )

    @Test
    fun `insert then getById round-trips`() = runTest {
        val scan = createScan("scan-1", resultJson = """[{"path":"/a.mkv"}]""")
        scanStateDao.insert(scan)

        assertEquals(scan, scanStateDao.getById("scan-1"))
    }

    @Test
    fun `getById returns null for missing scan`() = runTest {
        assertNull(scanStateDao.getById("missing"))
    }

    @Test
    fun `insert with REPLACE overwrites an existing scanId`() = runTest {
        scanStateDao.insert(createScan("scan-1", status = "RUNNING"))
        scanStateDao.insert(createScan("scan-1", status = "COMPLETED"))

        assertEquals("COMPLETED", scanStateDao.getById("scan-1")?.status)
    }

    @Test
    fun `update rewrites the full row`() = runTest {
        scanStateDao.insert(createScan("scan-1", status = "RUNNING"))

        scanStateDao.update(createScan("scan-1", status = "COMPLETED", progress = 10, total = 10, itemsFound = 3))

        val loaded = scanStateDao.getById("scan-1")!!
        assertEquals("COMPLETED", loaded.status)
        assertEquals(10, loaded.progress)
        assertEquals(3, loaded.itemsFound)
    }

    @Test
    fun `observeById emits the row`() = runTest {
        scanStateDao.insert(createScan("scan-1", status = "RUNNING"))

        assertEquals("RUNNING", scanStateDao.observeById("scan-1").first()?.status)
    }

    @Test
    fun `observeById emits null for missing scan`() = runTest {
        assertNull(scanStateDao.observeById("missing").first())
    }

    @Test
    fun `observeProgress projects only the progress columns`() = runTest {
        scanStateDao.insert(
            createScan("scan-1", status = "RUNNING", progress = 4, total = 10, itemsFound = 2)
        )

        val row = scanStateDao.observeProgress("scan-1").first()
        assertNotNull(row)
        assertEquals("RUNNING", row.status)
        assertEquals(4, row.progress)
        assertEquals(10, row.total)
        assertEquals(2, row.itemsFound)
    }

    @Test
    fun `observeProgress emits null for missing scan`() = runTest {
        assertNull(scanStateDao.observeProgress("missing").first())
    }

    @Test
    fun `updateProgress updates progress columns without touching status or result`() = runTest {
        scanStateDao.insert(
            createScan("scan-1", status = "RUNNING", resultJson = """[{"path":"/a.mkv"}]""")
        )

        val affected = scanStateDao.updateProgress("scan-1", progress = 7, total = 10, itemsFound = 4)

        assertEquals(1, affected)
        val loaded = scanStateDao.getById("scan-1")!!
        assertEquals(7, loaded.progress)
        assertEquals(10, loaded.total)
        assertEquals(4, loaded.itemsFound)
        // Progress-only write: status and the result blob survive unchanged.
        assertEquals("RUNNING", loaded.status)
        assertEquals("""[{"path":"/a.mkv"}]""", loaded.resultJson)
    }

    @Test
    fun `updateProgress returns zero when the scan row is gone`() = runTest {
        assertEquals(0, scanStateDao.updateProgress("missing", progress = 1, total = 1, itemsFound = 0))
    }

    @Test
    fun `deleteById removes the scan`() = runTest {
        scanStateDao.insert(createScan("scan-1"))

        scanStateDao.deleteById("scan-1")

        assertNull(scanStateDao.getById("scan-1"))
    }

    @Test
    fun `deleteOlderThan prunes only terminal scans strictly older than the cutoff`() = runTest {
        scanStateDao.insert(createScan("old-completed", status = "COMPLETED", createdAt = 1_000L))
        scanStateDao.insert(createScan("old-failed", status = "FAILED", createdAt = 2_000L))
        scanStateDao.insert(createScan("old-deleted", status = "DELETED", createdAt = 3_000L))
        // Terminal but exactly at the cutoff — "<" means it survives.
        scanStateDao.insert(createScan("boundary-completed", status = "COMPLETED", createdAt = 4_000L))
        // Recent terminal rows survive.
        scanStateDao.insert(createScan("new-completed", status = "COMPLETED", createdAt = 5_000L))
        // Old but still RUNNING — never pruned.
        scanStateDao.insert(createScan("old-running", status = "RUNNING", createdAt = 1_500L))

        val deleted = scanStateDao.deleteOlderThan(4_000L)

        assertEquals(3, deleted)
        val remaining = listOf(
            scanStateDao.getById("boundary-completed"),
            scanStateDao.getById("new-completed"),
            scanStateDao.getById("old-running"),
        )
        assertTrue(remaining.all { it != null })
        assertNull(scanStateDao.getById("old-completed"))
        assertNull(scanStateDao.getById("old-failed"))
        assertNull(scanStateDao.getById("old-deleted"))
    }
}
