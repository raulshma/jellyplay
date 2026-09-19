package com.raulshma.jellyplay.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [MediaCleanupScanCore]'s [MediaCleanupScanCore.runScan] chassis at the
 * core seam — the corners [AdminStatisticsRepositoryImplTest] only reached
 * through the repository's happy-path scans: the completion upsert (status +
 * serialized stubs + progress settled to total), the cancel-stop (a deleted
 * scan row makes the targeted progress write report 0 affected rows and the
 * loop stops WITHOUT a further fetch or a completion resurrection), the
 * catch → FAILED tail, and the [MediaCleanupScanCore.MAX_SCAN_RESULTS] paging
 * stop. Driven with synthetic fetch/map closures over a real in-memory Room
 * `scan_state` table — no API client calls.
 */
class MediaCleanupScanCoreTest {

    private lateinit var database: JellyPlayDatabase
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun buildCore(): MediaCleanupScanCore = MediaCleanupScanCore(
        apiClient = mockk(),
        auditLogDao = database.auditLogDao(),
        scanStateDao = database.scanStateDao(),
        json = json,
        // Unused by [runScan] (only the detect* launches start coroutines).
        scope = CoroutineScope(Dispatchers.Unconfined),
        labels = DesktopAdminStatisticsLabels,
        timeSource = object : TimeSource {
            override fun nowEpochMillis(): Long = 0L
            override fun nowElapsedRealtimeMillis(): Long = 0L
            override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
        },
    )

    private fun scanRow(scanId: String) = ScanStateEntity(
        scanId = scanId,
        type = "STALE",
        configJson = "{}",
        status = ScanPhase.SCANNING.name,
    )

    @Test
    fun `runScan completes the row with serialized stubs and progress settled to total`() = runTest {
        val core = buildCore()
        database.scanStateDao().insert(scanRow("s1"))
        var calls = 0

        core.runScan(
            scanId = "s1",
            fetchNextPage = {
                calls++
                if (calls == 1) Pair(3, listOf("a", "b", "c")) else null
            },
            mapRows = { rows -> rows.map { MediaItemStub(itemId = it) } },
            progressOf = { page, _, found -> Triple(page.second.size, page.first, found) },
        )

        val row = database.scanStateDao().getById("s1")!!
        assertEquals(ScanPhase.COMPLETED.name, row.status)
        assertEquals(3, row.itemsFound)
        assertEquals(row.total, row.progress)
        val stubs = json.decodeFromString(ListSerializer(MediaItemStub.serializer()), row.resultJson!!)
        assertEquals(listOf("a", "b", "c"), stubs.map { it.itemId })
    }

    @Test
    fun `a deleted scan row cancels the loop without a further fetch or a resurrection`() = runTest {
        val core = buildCore()
        database.scanStateDao().insert(scanRow("s2"))
        var calls = 0

        core.runScan(
            scanId = "s2",
            fetchNextPage = {
                calls++
                // Cancel mid-scan: the row disappears before the page's
                // targeted progress write runs.
                database.scanStateDao().deleteById("s2")
                Pair(10, listOf("a"))
            },
            mapRows = { rows -> rows.map { MediaItemStub(itemId = it) } },
            progressOf = { _, _, found -> Triple(found, 10, found) },
        )

        // The 0-affected-rows progress write stopped the loop after the one
        // page, and the completion upsert never re-created the row.
        assertEquals(1, calls)
        assertNull(database.scanStateDao().getById("s2"))
    }

    @Test
    fun `a throwing fetch marks the row FAILED`() = runTest {
        val core = buildCore()
        database.scanStateDao().insert(scanRow("s3"))

        core.runScan<String>(
            scanId = "s3",
            fetchNextPage = { throw IllegalStateException("boom") },
            mapRows = { emptyList() },
            progressOf = { _, _, _ -> Triple(0, 0, 0) },
        )

        assertEquals(ScanPhase.FAILED.name, database.scanStateDao().getById("s3")!!.status)
    }

    @Test
    fun `the scan stops paging once MAX_SCAN_RESULTS stubs are retained`() = runTest {
        val core = buildCore()
        database.scanStateDao().insert(scanRow("s4"))
        var calls = 0
        val halfCap = MediaCleanupScanCore.MAX_SCAN_RESULTS / 2

        core.runScan(
            scanId = "s4",
            fetchNextPage = {
                calls++
                Pair(Int.MAX_VALUE, List(halfCap) { "item-$calls-$it" })
            },
            mapRows = { rows -> rows.map { MediaItemStub(itemId = it) } },
            progressOf = { _, _, found -> Triple(found, Int.MAX_VALUE, found) },
        )

        // The third page would only be fetched to be discarded.
        assertEquals(2, calls)
        val row = database.scanStateDao().getById("s4")!!
        assertEquals(ScanPhase.COMPLETED.name, row.status)
        assertEquals(MediaCleanupScanCore.MAX_SCAN_RESULTS, row.itemsFound)
    }
}
