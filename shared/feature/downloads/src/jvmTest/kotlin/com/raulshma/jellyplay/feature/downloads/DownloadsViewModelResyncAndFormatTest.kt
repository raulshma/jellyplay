package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [DownloadsResyncActions] surface (the VM's resync/freshness seam)
 * NOT exercised by [DownloadsViewModelTest]:
 *
 * 1. [DownloadsResyncActions.forceResyncCandidates] eligibility boundary — the
 *    COMPLETED ∪ FAILED ∪ PAUSED set. FAILED/PAUSED items still carry their
 *    offline metadata row, so refreshing them is valid; actively transferring
 *    states (PENDING/QUEUED) are excluded exactly like DOWNLOADING.
 * 2. The reactive appbar/sheet state holders ([DownloadsResyncActions.updatesAvailable],
 *    [DownloadsResyncActions.updateRows]) mirror the repository flows and keep
 *    their WhileSubscribed defaults (0 / empty) before an emission.
 * 3. The core-model ByteFormatter semantics the screen consumes directly
 *    (B/KB/MB/GB steps, empty speed ≤ 0, empty ETA for zero-speed or
 *    fully-downloaded items) — the ViewModel's formatter forwards are
 *    deleted, so these pin the shared extensions themselves.
 *
 * Drives the holder DIRECTLY (the DownloadsResyncActions extraction): the
 * queue/repository/resync doubles are enough — no full-VM construction, so
 * the policy/intake/store doubles the pre-extraction suite needed are gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelResyncAndFormatTest {

    // MainDispatcherRule (:core:testing) inlined — jvmTest has no access to
    // that module (DownloadsViewModelTest pattern). The mirror test's holder
    // scope rides this dispatcher, shared with runTest(mainDispatcher)'s
    // scheduler — the same mechanics the pre-extraction VM suites used.
    private val mainDispatcher = StandardTestDispatcher()
    private var holderScope: kotlinx.coroutines.CoroutineScope? = null

    private lateinit var downloadRepository: DownloadQueue
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var syncManager: OfflineResync

    private lateinit var downloadsFlow: MutableStateFlow<List<DownloadItem>>
    private val updatesCount = MutableStateFlow(0)
    private val updateRows = MutableStateFlow<List<OfflineSyncUpdate>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        downloadRepository = mockk<DownloadQueue>(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        syncManager = mockk<OfflineResync>(relaxed = true)
        downloadsFlow = MutableStateFlow(emptyList())
        coEvery { downloadRepository.getAllDownloadsSnapshot() } answers { downloadsFlow.value }
        every { syncManager.batchProgress } returns MutableStateFlow(ResyncBatchProgress())
        every { offlineRepository.getUpdatesCount() } returns updatesCount
        every { offlineRepository.getItemsWithUpdates() } returns updateRows
    }

    @AfterTest
    fun tearDown() {
        holderScope?.cancel()
        Dispatchers.resetMain()
    }

    /**
     * The holder under test, wired to this test's doubles. Candidates tests
     * pass [TestScope.backgroundScope] (stateIn's sharing coroutine is
     * auto-cancelled at test end); the mirror test passes a plain
     * CoroutineScope on [mainDispatcher] (torn down in [tearDown]) so the
     * WhileSubscribed propagation runs on the shared test scheduler.
     */
    private fun CoroutineScope.resyncActions() = DownloadsResyncActions(
        scope = this,
        offlineRepository = offlineRepository,
        resync = syncManager,
        queue = downloadRepository,
    )

    private fun item(
        id: String,
        mediaItemId: String = id,
        status: DownloadStatus,
    ) = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = "Item $id",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = status,
        priority = 0,
    )

    // ── forceResyncCandidates eligibility boundary ────────────────────────

    @Test
    fun failed_and_paused_items_are_force_resync_eligible() = runTest {
        downloadsFlow.value = listOf(
            item("failed", status = DownloadStatus.FAILED),
            item("paused", status = DownloadStatus.PAUSED),
        )

        val candidates = backgroundScope.resyncActions().forceResyncCandidates()
        assertEquals(listOf("failed", "paused"), candidates.map { it.id })
    }

    @Test
    fun pending_and_queued_items_are_force_resync_excluded() = runTest {
        downloadsFlow.value = listOf(
            item("pen", status = DownloadStatus.PENDING),
            item("que", status = DownloadStatus.QUEUED),
            item("cxl", status = DownloadStatus.CANCELLED),
        )

        assertEquals(emptyList(), backgroundScope.resyncActions().forceResyncCandidates().map { it.id })
    }

    @Test
    fun candidates_snapshot_is_independent_of_the_ui_list_window() = runTest {
        // The snapshot read is uncapped; a UI list that hasn't emitted (or an
        // empty one) must not hide downloaded items from the picker.
        downloadsFlow.value = emptyList()
        coEvery { downloadRepository.getAllDownloadsSnapshot() } returns listOf(
            item("snap", status = DownloadStatus.COMPLETED),
        )

        assertEquals(listOf("snap"), backgroundScope.resyncActions().forceResyncCandidates().map { it.id })
    }

    // ── Reactive update state holders ────────────────────────────────────

    @Test
    fun updatesAvailable_and_updateRows_mirror_the_repository_flows() = runTest(mainDispatcher) {
        holderScope = CoroutineScope(mainDispatcher)
        val actions = holderScope!!.resyncActions()
        val countJob = launch { actions.updatesAvailable.collect {} }
        val rowsJob = launch { actions.updateRows.collect {} }
        advanceUntilIdle()
        assertEquals(0, actions.updatesAvailable.value)
        assertEquals(emptyList(), actions.updateRows.value)

        updatesCount.value = 3
        val rows = listOf(OfflineSyncUpdate(id = "u1", name = "U1", mediaFileChanged = true))
        updateRows.value = rows
        advanceUntilIdle()

        assertEquals(3, actions.updatesAvailable.value)
        assertEquals(rows, actions.updateRows.value)
        countJob.cancel()
        rowsJob.cancel()
    }

    // ── ByteFormatter semantics the downloads UI consumes directly ───────

    @Test
    fun formatBytes_steps_through_byte_units() {
        assertEquals("512 B", 512L.formatBytes())
        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("1.0 MB", (1024L * 1024).formatBytes())
        assertEquals("1.0 GB", (1024L * 1024 * 1024).formatBytes())
    }

    @Test
    fun formatSpeed_is_empty_for_non_positive_speeds() {
        assertEquals("", 0L.formatSpeed())
        assertEquals("", (-5L).formatSpeed())
        assertEquals("2.0 KB/s", 2048L.formatSpeed())
    }

    @Test
    fun formatEta_boundaries() {
        // Zero total / zero speed / already-complete → empty.
        assertEquals("", formatEta(0L, 0L, 100L))
        assertEquals("", formatEta(0L, 1000L, 0L))
        assertEquals("", formatEta(1000L, 1000L, 100L))
        // 50s remaining → seconds branch.
        assertEquals("50s left", formatEta(0L, 5000L, 100L))
        // 90s remaining → minutes branch.
        assertEquals("1m 30s left", formatEta(0L, 9000L, 100L))
        // 2h + 1s remaining → hours branch.
        assertEquals("2h 0m left", formatEta(0L, 7201L * 1000, 1000L))
    }
}
