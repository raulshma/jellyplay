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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Pins the [DownloadsViewModel] surface NOT exercised by
 * [DownloadsViewModelTest]:
 *
 * 1. [DownloadsViewModel.forceResyncCandidates] eligibility boundary — the
 *    COMPLETED ∪ FAILED ∪ PAUSED set. FAILED/PAUSED items still carry their
 *    offline metadata row, so refreshing them is valid; actively transferring
 *    states (PENDING/QUEUED) are excluded exactly like DOWNLOADING.
 * 2. The reactive appbar/sheet state holders ([DownloadsViewModel.updatesAvailable],
 *    [DownloadsViewModel.updateRows]) mirror the repository flows and keep
 *    their WhileSubscribed defaults (0 / empty) before an emission.
 * 3. The core-model ByteFormatter semantics the screen consumes directly
 *    (B/KB/MB/GB steps, empty speed ≤ 0, empty ETA for zero-speed or
 *    fully-downloaded items) — the ViewModel's formatter forwards are
 *    deleted, so these pin the shared extensions themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelResyncAndFormatTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (DownloadsViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var downloadRepository: DownloadQueue
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var syncManager: OfflineResync
    private lateinit var viewModel: DownloadsViewModel

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
        every { downloadRepository.getAllDownloads() } returns downloadsFlow
        every { downloadRepository.getActiveDownloadProgress() } returns MutableStateFlow(emptyMap())
        coEvery { downloadRepository.getAllDownloadsSnapshot() } answers { downloadsFlow.value }
        every { syncManager.batchProgress } returns MutableStateFlow(ResyncBatchProgress())
        every { offlineRepository.getUpdatesCount() } returns updatesCount
        every { offlineRepository.getItemsWithUpdates() } returns updateRows
        viewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
    fun failed_and_paused_items_are_force_resync_eligible() = runTest(mainDispatcher) {
        downloadsFlow.value = listOf(
            item("failed", status = DownloadStatus.FAILED),
            item("paused", status = DownloadStatus.PAUSED),
        )
        advanceUntilIdle()

        val candidates = viewModel.forceResyncCandidates()
        assertEquals(listOf("failed", "paused"), candidates.map { it.id })
    }

    @Test
    fun pending_and_queued_items_are_force_resync_excluded() = runTest(mainDispatcher) {
        downloadsFlow.value = listOf(
            item("pen", status = DownloadStatus.PENDING),
            item("que", status = DownloadStatus.QUEUED),
            item("cxl", status = DownloadStatus.CANCELLED),
        )
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.forceResyncCandidates().map { it.id })
    }

    @Test
    fun candidates_snapshot_is_independent_of_the_ui_list_window() = runTest(mainDispatcher) {
        // The snapshot read is uncapped; a UI list that hasn't emitted (or an
        // empty one) must not hide downloaded items from the picker.
        downloadsFlow.value = emptyList()
        coEvery { downloadRepository.getAllDownloadsSnapshot() } returns listOf(
            item("snap", status = DownloadStatus.COMPLETED),
        )
        advanceUntilIdle()

        assertEquals(listOf("snap"), viewModel.forceResyncCandidates().map { it.id })
    }

    // ── Reactive update state holders ────────────────────────────────────

    @Test
    fun updatesAvailable_and_updateRows_mirror_the_repository_flows() = runTest(mainDispatcher) {
        val countJob = launch { viewModel.updatesAvailable.collect {} }
        val rowsJob = launch { viewModel.updateRows.collect {} }
        advanceUntilIdle()
        assertEquals(0, viewModel.updatesAvailable.value)
        assertEquals(emptyList(), viewModel.updateRows.value)

        updatesCount.value = 3
        val rows = listOf(OfflineSyncUpdate(id = "u1", name = "U1", mediaFileChanged = true))
        updateRows.value = rows
        advanceUntilIdle()

        assertEquals(3, viewModel.updatesAvailable.value)
        assertEquals(rows, viewModel.updateRows.value)
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
