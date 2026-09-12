package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
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
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
 * Pins the [WatchedMediaScanWorker] shell — the watched-removal twin of
 * [StaleMediaScanWorker] around the shared [ScanWorkerHelper] paging loop:
 *
 *  - Missing `scanId` input or a missing `scan_state` row → `failure`, API
 *    untouched.
 *  - A missing signed-in user (`currentUser` emitted nothing) → `failure`
 *    *after* the row exists but *before* any fetch: watched-item queries are
 *    per-user, so there is nothing sensible to run without one.
 *  - The happy path forwards the admin user id + config into `getWatchedItems`
 *    (`minDaysSincePlayed`, `keepFavorites`, `parentId = libraryIds.first()`)
 *    and maps items to stubs with a blank sizeText and a "Played Nx" detail.
 *  - The catch ladder: thrown [IOException] → FAILED + `retry`; any other
 *    throwable → FAILED + `failure`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchedMediaScanWorkerTest {

    private lateinit var context: Context
    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val scanStateDao: ScanStateDao = mockk(relaxed = true)

    private val scanId = "scan-watched-1"

    private val config = MediaCleanupConfig(
        minDaysSinceWatched = 30,
        keepFavorites = true,
        includeItemTypes = setOf("Episode", "Movie"),
        libraryIds = setOf("lib-1", "lib-2"),
    )

    private val entity = ScanStateEntity(
        scanId = scanId,
        type = "WATCHED",
        configJson = ScanWorkerHelper.json.encodeToString(MediaCleanupConfig.serializer(), config),
        status = ScanPhase.SCANNING.name,
    )

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
        every { apiClient.currentUser } returns flowOf(userInfo(id = "admin-1"))
    }

    private fun userInfo(id: String) = com.raulshma.jellyplay.core.model.UserInfo(
        id = id,
        name = "admin",
        serverAddress = "http://jellyfin.local",
        accessToken = "token",
    )

    private fun buildWorker(inputData: Data = workDataOf("scanId" to scanId)): WatchedMediaScanWorker =
        TestListenableWorkerBuilder<WatchedMediaScanWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): WatchedMediaScanWorker = WatchedMediaScanWorker(appContext, workerParameters, apiClient, scanStateDao)
            })
            .setInputData(inputData)
            .build()

    private fun watchedItem(id: Int, playCount: Int) = WatchedMediaItem(
        itemId = "item-$id",
        name = "Item $id",
        type = "Episode",
        playCount = playCount,
    )

    // ── Guard clauses ─────────────────────────────────────────────────

    @Test
    fun `missing scanId input fails without touching dao or api`() = runTest {
        val result = buildWorker(inputData = Data.EMPTY).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { scanStateDao.getById(any()) }
        coVerify(exactly = 0) { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing dao row fails without calling the api`() = runTest {
        coEvery { scanStateDao.getById(scanId) } returns null

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing current user fails before any fetch`() = runTest {
        every { apiClient.currentUser } returns flowOf(null)

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) }
        // Not marked failed either — the run never started.
        coVerify(exactly = 0) { scanStateDao.update(any()) }
    }

    // ── Happy path: user + config → fetch args, items → stubs ─────────

    @Test
    fun `happy path forwards admin user and config into fetch args and persists mapped stubs`() = runTest {
        coEvery { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(2 to listOf(watchedItem(1, 3), watchedItem(2, 1)))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            apiClient.getWatchedItems(
                userId = "admin-1",
                includeItemTypes = listOf("Episode", "Movie"),
                minDaysSincePlayed = 30,
                keepFavorites = true,
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
        assertEquals("Episode", stubs[0].type)
        // Watched-removal stubs carry no size text — the detail line is the
        // play count instead.
        assertEquals("", stubs[0].sizeText)
        assertEquals("Played 3x", stubs[0].detail)
        assertEquals("Played 1x", stubs[1].detail)
    }

    // ── Catch ladder: transient IO → retry, anything else → failure ──

    @Test
    fun `transient IOException marks the row FAILED and returns retry`() = runTest {
        coEvery { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) } throws
            IOException("connection reset")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        val failed = updates.single { it.status == ScanPhase.FAILED.name }
        assertEquals(scanId, failed.scanId)
    }

    @Test
    fun `unexpected exception marks the row FAILED and returns failure`() = runTest {
        coEvery { apiClient.getWatchedItems(any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("malformed payload")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(ScanPhase.FAILED.name, updates.single().status)
    }
}
