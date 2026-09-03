package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Pins the [StaleMediaScanWorker] shell — everything around the shared
 * [ScanWorkerHelper] paging loop (whose internals are covered by
 * `ScanWorkerHelperTest` in :shared:core:data):
 *
 *  - A missing `scanId` input **or** a missing `scan_state` row fails the
 *    worker without touching the API (the scan is unreachable without its
 *    persisted identity + config).
 *  - The happy path forwards the decoded [MediaCleanupConfig] into
 *    `getStaleItems` (`daysThreshold`, `includeNeverPlayed`, item types,
 *    `parentId = libraryIds.firstOrNull()`) with the helper's page shape
 *    (limit 200, ascending startIndex), maps each [StaleMediaItem] to a
 *    [MediaItemStub] ("N days ago" / "Never played" detail), and persists the
 *    COMPLETED row with the server-reported total.
 *  - A failed API `Result` is *swallowed* by `getOrDefault` → the scan
 *    completes empty (pinning the current tolerance of per-page API errors).
 *  - A thrown [IOException] (transport-level) marks the row FAILED and returns
 *    `retry`; any other throwable marks FAILED and returns `failure`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaleMediaScanWorkerTest {

    private lateinit var context: Context
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val scanStateDao: ScanStateDao = mockk(relaxed = true)

    private val scanId = "scan-stale-1"

    private val config = MediaCleanupConfig(
        daysThreshold = 45,
        includeNeverPlayed = false,
        includeItemTypes = setOf("Movie", "Episode"),
        libraryIds = setOf("lib-1", "lib-2"),
    )

    private val entity = ScanStateEntity(
        scanId = scanId,
        type = "STALE",
        configJson = ScanWorkerHelper.json.encodeToString(MediaCleanupConfig.serializer(), config),
        status = ScanPhase.SCANNING.name,
    )

    /** DAO mock that records every `update` payload, by status. */
    private val updates = mutableListOf<ScanStateEntity>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        coEvery { scanStateDao.update(capture(updates)) } returns Unit
        coEvery { scanStateDao.getById(scanId) } returns entity
    }

    private fun buildWorker(inputData: androidx.work.Data = workDataOf("scanId" to scanId)): StaleMediaScanWorker =
        TestListenableWorkerBuilder<StaleMediaScanWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): StaleMediaScanWorker = StaleMediaScanWorker(appContext, workerParameters, apiClient, scanStateDao)
            })
            .setInputData(inputData)
            .build()

    private fun staleItem(id: Int, daysSincePlay: Int) = StaleMediaItem(
        itemId = "item-$id",
        name = "Item $id",
        type = "Movie",
        daysSincePlay = daysSincePlay,
        sizeText = "$id GB",
    )

    // ── Guard clauses ─────────────────────────────────────────────────

    @Test
    fun `missing scanId input fails without touching dao or api`() = runTest {
        val result = buildWorker(inputData = androidx.work.Data.EMPTY).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { scanStateDao.getById(any()) }
        coVerify(exactly = 0) { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing dao row fails without calling the api`() = runTest {
        coEvery { scanStateDao.getById(scanId) } returns null

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ── Happy path: config → fetch args, items → stubs ────────────────

    @Test
    fun `happy path forwards config into fetch args and persists mapped stubs`() = runTest {
        coEvery { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(2 to listOf(staleItem(1, 12), staleItem(2, 0)))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            apiClient.getStaleItems(
                daysThreshold = 45,
                includeNeverPlayed = false,
                includeItemTypes = listOf("Movie", "Episode"),
                parentId = "lib-1",
                startIndex = 0,
                limit = 200,
            )
        }

        val completed = updates.single { it.status == ScanPhase.COMPLETED.name }
        assertEquals(scanId, completed.scanId)
        assertEquals(2, completed.total)
        assertEquals(2, completed.progress)
        assertEquals(2, completed.itemsFound)

        val stubs = ScanWorkerHelper.json
            .decodeFromString(serializer<List<MediaItemStub>>(), completed.resultJson!!)
        assertEquals(2, stubs.size)
        assertEquals("item-1", stubs[0].itemId)
        assertEquals("Item 1", stubs[0].name)
        assertEquals("Movie", stubs[0].type)
        assertEquals("1 GB", stubs[0].sizeText)
        assertEquals("12 days ago", stubs[0].detail)
        // daysSincePlay == 0 → the never-played wording, regardless of the
        // includeNeverPlayed flag (the flag only filters server-side).
        assertEquals("Never played", stubs[1].detail)
    }

    @Test
    fun `multi page scan walks startIndex in batch steps and aggregates stubs`() = runTest {
        coEvery { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) } answers {
            val startIndex = args[4] as Int
            val limit = args[5] as Int
            when (startIndex) {
                0 -> Result.success(205 to (1..limit).map { staleItem(it, it) })
                else -> Result.success(205 to (201..205).map { staleItem(it, it) })
            }
        }

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            apiClient.getStaleItems(any(), any(), any(), any(), startIndex = 0, limit = 200, useDateAdded = false)
        }
        coVerify(exactly = 1) {
            apiClient.getStaleItems(any(), any(), any(), any(), startIndex = 200, limit = 200, useDateAdded = false)
        }

        val scanning = updates.filter { it.status == ScanPhase.SCANNING.name }
        assertEquals(2, scanning.size)
        // After page 1: 200 of 205 checked; after the partial page: clamped to 205.
        assertEquals(200, scanning[0].progress)
        assertEquals(205, scanning[0].total)
        assertEquals(205, scanning[1].progress)

        val completed = updates.single { it.status == ScanPhase.COMPLETED.name }
        assertEquals(205, completed.itemsFound)
        assertEquals(205, completed.total)
        val stubs = ScanWorkerHelper.json
            .decodeFromString(serializer<List<MediaItemStub>>(), completed.resultJson!!)
        assertEquals(205, stubs.size)
    }

    // ── Per-page API failure tolerance ────────────────────────────────

    @Test
    fun `a failed api Result is swallowed into an empty page and the scan completes`() = runTest {
        // fetchPage does .getOrDefault(0 to emptyList()) — a failed Result is
        // tolerated per page (pinning the current behaviour, not endorsing it).
        coEvery { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("server 500"))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val completed = updates.single { it.status == ScanPhase.COMPLETED.name }
        assertEquals(0, completed.itemsFound)
    }

    // ── Catch ladder: transient IO → retry, anything else → failure ──

    @Test
    fun `transient IOException marks the row FAILED and returns retry`() = runTest {
        coEvery { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) } throws
            IOException("socket reset")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        val failed = updates.single { it.status == ScanPhase.FAILED.name }
        assertEquals(scanId, failed.scanId)
    }

    @Test
    fun `unexpected exception marks the row FAILED and returns failure`() = runTest {
        coEvery { apiClient.getStaleItems(any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("bad payload")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ScanPhase.FAILED.name, updates.single().status)
    }
}
