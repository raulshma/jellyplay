package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.model.ResyncOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var syncManager: OfflineSyncManager
    private lateinit var viewModel: DownloadsViewModel

    /** Backing flow behind getAllDownloads so tests can push list changes. */
    private lateinit var downloadsFlow: MutableStateFlow<List<DownloadItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        downloadsFlow = MutableStateFlow(emptyList())
        every { downloadRepository.getAllDownloads() } returns downloadsFlow
        // forceResyncCandidates() reads the suspend snapshot; answer with the
        // flow's current value so pushItems drives both paths.
        coEvery { downloadRepository.getAllDownloadsSnapshot() } answers { downloadsFlow.value }
        // batchProgress is a non-suspend val → stub with `every`, not `coEvery`.
        every { syncManager.batchProgress } returns MutableStateFlow(ResyncBatchProgress())
        // Flow-returning repo reads are non-suspend → `every` as well.
        every { offlineRepository.getUpdatesCount() } returns MutableStateFlow(0)
        every { offlineRepository.getItemsWithUpdates() } returns MutableStateFlow(emptyList())
        viewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Pushes [items] into the repository flow and settles the init collector. */
    private fun pushItems(items: List<DownloadItem>) {
        downloadsFlow.value = items
    }

    // ── init collection (queue list projection) ───────────────────────────

    @Test
    fun init_collects_downloads_and_sums_storage_bytes() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("d1", downloadedBytes = 100, totalBytes = 200),
                item("d2", downloadedBytes = 50, totalBytes = 300),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.downloads.size)
        assertEquals(150L, state.totalStorageBytes)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun init_load_failure_sets_error_with_fallback_literal() = runTest(mainDispatcher) {
        every { downloadRepository.getAllDownloads() } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException(null as String?)
        }
        val failingViewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager)
        advanceUntilIdle()

        // Throwable.message == null must hit the fallback literal (kept
        // byte-identical from the legacy call site).
        assertEquals("Failed to load downloads", failingViewModel.uiState.value.error)
        assertFalse(failingViewModel.uiState.value.isLoading)
    }

    // ── per-item orchestration ────────────────────────────────────────────

    @Test
    fun cancelDownload_calls_repository_with_item_id() = runTest(mainDispatcher) {
        viewModel.cancelDownload(item("d9", status = DownloadStatus.DOWNLOADING))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.cancelDownload("d9") }
    }

    @Test
    fun pauseDownload_calls_pause_only() = runTest(mainDispatcher) {
        viewModel.pauseDownload(item("d9", status = DownloadStatus.DOWNLOADING))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("d9") }
        coVerify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun resumeDownload_resets_and_reenqueues() = runTest(mainDispatcher) {
        viewModel.resumeDownload(item("d9", status = DownloadStatus.PAUSED))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.resumeDownload("d9") }
        // enqueueDownload is a non-suspend fun on the writer port.
        verify(exactly = 1) { downloadRepository.enqueueDownload("d9") }
    }

    @Test
    fun retryDownload_resets_and_reenqueues() = runTest(mainDispatcher) {
        viewModel.retryDownload(item("d9", status = DownloadStatus.FAILED))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.retryDownload("d9") }
        verify(exactly = 1) { downloadRepository.enqueueDownload("d9") }
    }

    // ── Bus→flow seam (V3 conveyor) ───────────────────────────────────────
    // The legacy code posted UiText.Resource(R.string.downloads_deleted_message)
    // through the Android-only UserMessageBus; the port pins the replacement
    // one-shot DownloadsUserMessage flow.

    @Test
    fun deleteDownload_calls_repository_and_emits_Deleted_message() = runTest(mainDispatcher) {
        viewModel.deleteDownload(item("d9", status = DownloadStatus.COMPLETED))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("d9") }
        assertEquals(DownloadsUserMessage.Deleted, viewModel.messages.first())
    }

    // ── Selection ─────────────────────────────────────────────────────────

    @Test
    fun toggleSelection_adds_then_removes_and_tracks_selection_mode() = runTest(mainDispatcher) {
        val a = item("a")
        val b = item("b")
        pushItems(listOf(a, b))
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        assertEquals(setOf("a"), viewModel.uiState.value.selectedIds)
        assertTrue(viewModel.uiState.value.selectionMode)

        viewModel.toggleSelection(b)
        assertEquals(setOf("a", "b"), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    @Test
    fun selectAll_selects_every_listed_id_then_clearSelection_resets() = runTest(mainDispatcher) {
        pushItems(listOf(item("a"), item("b"), item("c")))
        advanceUntilIdle()

        viewModel.selectAll()
        assertEquals(setOf("a", "b", "c"), viewModel.uiState.value.selectedIds)
        assertTrue(viewModel.uiState.value.selectionMode)

        viewModel.clearSelection()
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertFalse(viewModel.uiState.value.selectionMode)
    }

    // ── Bulk actions (status-filtered targets) ────────────────────────────

    @Test
    fun deleteSelected_deletes_each_target_clears_selection_and_emits_Deleted() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("a", status = DownloadStatus.COMPLETED),
                item("b", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("a") }
        coVerify(exactly = 1) { downloadRepository.deleteDownload("b") }
        assertEquals(emptySet(), viewModel.uiState.value.selectedIds)
        assertEquals(DownloadsUserMessage.Deleted, viewModel.messages.first())
    }

    @Test
    fun deleteSelected_with_no_targets_skips_repository_and_message() = runTest(mainDispatcher) {
        viewModel.deleteSelected()
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
        val message = kotlinx.coroutines.withTimeoutOrNull(50) { viewModel.messages.first() }
        assertNull(message)
    }

    @Test
    fun pauseSelected_pauses_only_selected_downloading_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
                item("ok", status = DownloadStatus.COMPLETED),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.pauseSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("ok") }
    }

    @Test
    fun resumeSelected_resumes_and_reenqueues_only_selected_paused_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("pa", status = DownloadStatus.PAUSED),
                item("dl", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.resumeSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.resumeDownload("pa") }
        verify(exactly = 1) { downloadRepository.enqueueDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.resumeDownload("dl") }
        verify(exactly = 0) { downloadRepository.enqueueDownload("dl") }
    }

    @Test
    fun cancelSelected_cancels_active_but_not_completed_or_cancelled_items() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("pen", status = DownloadStatus.PENDING),
                item("que", status = DownloadStatus.QUEUED),
                item("dl", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
                item("done", status = DownloadStatus.COMPLETED),
                item("cxl", status = DownloadStatus.CANCELLED),
            ),
        )
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.cancelSelected()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.cancelDownload("pen") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("que") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("dl") }
        coVerify(exactly = 1) { downloadRepository.cancelDownload("pa") }
        coVerify(exactly = 0) { downloadRepository.cancelDownload("done") }
        coVerify(exactly = 0) { downloadRepository.cancelDownload("cxl") }
    }

    // ── Global actions ────────────────────────────────────────────────────

    @Test
    fun pauseAll_pauses_every_downloading_item_without_selection() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl1", status = DownloadStatus.DOWNLOADING),
                item("dl2", status = DownloadStatus.DOWNLOADING),
                item("pa", status = DownloadStatus.PAUSED),
            ),
        )
        advanceUntilIdle()

        viewModel.pauseAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl1") }
        coVerify(exactly = 1) { downloadRepository.pauseDownload("dl2") }
        coVerify(exactly = 0) { downloadRepository.pauseDownload("pa") }
    }

    @Test
    fun retryAllFailed_retries_and_reenqueues_every_failed_item() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("f1", status = DownloadStatus.FAILED),
                item("f2", status = DownloadStatus.FAILED),
                item("ok", status = DownloadStatus.COMPLETED),
            ),
        )
        advanceUntilIdle()

        viewModel.retryAllFailed()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.retryDownload("f1") }
        coVerify(exactly = 1) { downloadRepository.retryDownload("f2") }
        verify(exactly = 1) { downloadRepository.enqueueDownload("f1") }
        verify(exactly = 1) { downloadRepository.enqueueDownload("f2") }
        coVerify(exactly = 0) { downloadRepository.retryDownload("ok") }
    }

    @Test
    fun moveToFront_and_lowerPriority_use_relative_priorities_from_list() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("a", priority = 3),
                item("b", priority = 7),
                item("c", priority = 5),
            ),
        )
        advanceUntilIdle()

        viewModel.moveToFront(item("a", priority = 3))
        advanceUntilIdle()
        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("a", 8) }

        viewModel.lowerPriority(item("c", priority = 5))
        advanceUntilIdle()
        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("c", 2) }
    }

    // ── Freshness check / resync ──────────────────────────────────────────

    @Test
    fun checkAllForUpdates_checks_batch_with_downloaded_ids_and_resets_checking() = runTest(mainDispatcher) {
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("i1", "i2")

        viewModel.checkAllForUpdates()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.checkForUpdatesBatch(listOf("i1", "i2")) }
        assertFalse(viewModel.checking.value)
    }

    @Test
    fun checkAllForUpdates_while_checking_is_a_noop() = runTest(mainDispatcher) {
        // Suspend the first batch check until the test releases it, so the
        // second call observes _checking == true.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { offlineRepository.getDownloadedItemIds() } returns listOf("i1")
        coEvery { syncManager.checkForUpdatesBatch(any()) } coAnswers { gate.await(); emptyList() }

        viewModel.checkAllForUpdates()
        advanceUntilIdle()
        assertTrue(viewModel.checking.value)

        viewModel.checkAllForUpdates()
        advanceUntilIdle()
        coVerify(exactly = 1) { syncManager.checkForUpdatesBatch(any()) }

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.checking.value)
    }

    @Test
    fun resyncOne_delegates_single_id_batch() = runTest(mainDispatcher) {
        viewModel.resyncOne("i1")
        verify(exactly = 1) { syncManager.resyncBatch(listOf("i1")) }
    }

    @Test
    fun resyncAll_resolves_flagged_ids_from_repository() = runTest(mainDispatcher) {
        every { offlineRepository.getItemsWithUpdates() } returns kotlinx.coroutines.flow.flowOf(
            listOf(update("u1"), update("u2")),
        )

        viewModel.resyncAll()
        advanceUntilIdle()

        verify(exactly = 1) { syncManager.resyncBatch(listOf("u1", "u2")) }
    }

    @Test
    fun resyncAll_with_explicit_ids_delegates_and_skips_empty() = runTest(mainDispatcher) {
        viewModel.resyncAll(listOf("u1", "u2"))
        verify(exactly = 1) { syncManager.resyncBatch(listOf("u1", "u2")) }

        viewModel.resyncAll(emptyList())
        verify(exactly = 1) { syncManager.resyncBatch(any()) }
    }

    @Test
    fun forceResync_delegates_with_options_and_skips_empty_selections() = runTest(mainDispatcher) {
        val options = ResyncOptions(categories = setOf(ResyncCategory.METADATA))

        viewModel.forceResync(listOf("i1"), options)
        verify(exactly = 1) { syncManager.resyncBatch(listOf("i1"), options) }

        viewModel.forceResync(emptyList(), options)
        viewModel.forceResync(listOf("i1"), ResyncOptions(categories = emptySet()))
        verify(exactly = 1) { syncManager.resyncBatch(any(), any()) }
    }

    @Test
    fun clearResyncProgress_delegates_to_sync_manager() = runTest(mainDispatcher) {
        viewModel.clearResyncProgress()
        verify(exactly = 1) { syncManager.clearBatchProgress() }
    }

    @Test
    fun forceResyncCandidates_filters_completed_and_dedupes_by_media_item_id() = runTest(mainDispatcher) {
        pushItems(
            listOf(
                item("dl-ep1", mediaItemId = "ep1", status = DownloadStatus.COMPLETED, seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
                item("dl-ep2", mediaItemId = "ep1", status = DownloadStatus.COMPLETED, seriesName = "Show", seasonNumber = 1, episodeNumber = 2),
                item("dl-movie", mediaItemId = "m1", status = DownloadStatus.COMPLETED),
                item("dl-active", mediaItemId = "ep9", status = DownloadStatus.DOWNLOADING),
            ),
        )
        advanceUntilIdle()

        val candidates = viewModel.forceResyncCandidates()
        assertEquals(listOf("ep1", "m1"), candidates.map { it.id })
        // Episode context is carried so the picker renders the SxxExx line.
        assertEquals("Show", candidates.first().seriesName)
        assertEquals(1, candidates.first().seasonNumber)
        assertEquals(2, candidates.first().episodeNumber)
        assertEquals(MediaType.MOVIE, candidates[1].mediaType)
    }

    private fun item(
        id: String,
        mediaItemId: String = id,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
        priority: Int = 0,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = "Item $id",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        priority = priority,
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )

    private fun update(id: String) = OfflineSyncUpdate(id = id, name = "Update $id", mediaFileChanged = false)
}
