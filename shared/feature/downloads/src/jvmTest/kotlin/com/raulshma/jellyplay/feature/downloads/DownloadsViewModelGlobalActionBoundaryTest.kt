package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Empty-target and empty-list boundary gaps in [DownloadsViewModel] NOT pinned
 * by [DownloadsViewModelTest] / [DownloadsViewModelResyncAndFormatTest] (both
 * only exercise the positive paths of the global actions):
 *
 * 1. [DownloadsViewModel.applyBulkAction] with PAUSE/All when NO downloading
 *    items exist and RETRY_FAILED/All when NO failed items exist are guarded
 *    no-ops — no repository call fires at all.
 * 2. [DownloadsViewModel.moveToFront] / [DownloadsViewModel.lowerPriority]
 *    against an EMPTY list fall back to the 0 baseline (`maxOfOrNull`/`minOfOrNull`
 *    → null → 0), i.e. +1 / −1 respectively.
 * 3. [DownloadsViewModel.checkAllForUpdates] forwards an EMPTY downloaded-id
 *    batch (the sync manager, not the VM, decides what an empty batch means).
 * 4. DELETE/Selected on a selection whose ids no longer match the current
 *    list (list changed under the selection) is a no-op — the target fold
 *    intersects selection with the LIVE list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelGlobalActionBoundaryTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (DownloadsViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var syncManager: OfflineSyncManager
    private lateinit var viewModel: DownloadsViewModel

    private lateinit var downloadsFlow: MutableStateFlow<List<DownloadItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        downloadsFlow = MutableStateFlow(emptyList())
        every { downloadRepository.getAllDownloads() } returns downloadsFlow
        coEvery { downloadRepository.getAllDownloadsSnapshot() } answers { downloadsFlow.value }
        every { syncManager.batchProgress } returns MutableStateFlow(ResyncBatchProgress())
        every { offlineRepository.getUpdatesCount() } returns MutableStateFlow(0)
        every { offlineRepository.getItemsWithUpdates() } returns MutableStateFlow(emptyList())
        viewModel = DownloadsViewModel(downloadRepository, offlineRepository, syncManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(
        id: String,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
        priority: Int = 0,
    ) = DownloadItem(
        id = id,
        mediaItemId = id,
        name = "Item $id",
        mediaType = MediaType.MOVIE,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = 0L,
        downloadedBytes = 0L,
        status = status,
        priority = priority,
    )

    @Test
    fun pauseAll_withNoDownloadingItems_isAGuardedNoop() = runTest(mainDispatcher) {
        downloadsFlow.value = listOf(
            item("pa", status = DownloadStatus.PAUSED),
            item("ok", status = DownloadStatus.COMPLETED),
        )
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.PAUSE, DownloadActionScope.All)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadRepository.pauseDownload(any()) }
    }

    @Test
    fun retryAllFailed_withNoFailedItems_isAGuardedNoop() = runTest(mainDispatcher) {
        downloadsFlow.value = listOf(item("ok", status = DownloadStatus.COMPLETED))
        advanceUntilIdle()

        viewModel.applyBulkAction(DownloadBulkAction.RETRY_FAILED, DownloadActionScope.All)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadRepository.retryDownload(any()) }
        verify(exactly = 0) { downloadRepository.enqueueDownload(any()) }
    }

    @Test
    fun moveToFront_onAnEmptyList_usesTheZeroBaselinePlusOne() = runTest(mainDispatcher) {
        downloadsFlow.value = emptyList()
        advanceUntilIdle()

        viewModel.moveToFront(item("solo"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("solo", 1) }
    }

    @Test
    fun lowerPriority_onAnEmptyList_usesTheZeroBaselineMinusOne() = runTest(mainDispatcher) {
        downloadsFlow.value = emptyList()
        advanceUntilIdle()

        viewModel.lowerPriority(item("solo"))
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.setDownloadPriority("solo", -1) }
    }

    @Test
    fun checkAllForUpdates_forwardsAnEmptyDownloadedIdBatch() = runTest(mainDispatcher) {
        coEvery { offlineRepository.getDownloadedItemIds() } returns emptyList()

        viewModel.checkAllForUpdates()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncManager.checkForUpdatesBatch(emptyList()) }
        assertTrue(viewModel.checking.value.not())
    }

    @Test
    fun deleteSelected_withSelectionMissingFromTheLiveList_isANoop() = runTest(mainDispatcher) {
        // Selection captured an id that has since left the (500-row-windowed)
        // list: the bulk delete intersects with the LIVE list, so nothing is
        // deleted and no message is emitted.
        downloadsFlow.value = listOf(item("still-here", status = DownloadStatus.COMPLETED))
        advanceUntilIdle()
        viewModel.toggleSelection(item("vanished"))

        viewModel.applyBulkAction(DownloadBulkAction.DELETE, DownloadActionScope.Selected)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
        // The stale selection is kept (only a successful bulk delete clears it).
        assertEquals(setOf("vanished"), viewModel.uiState.value.selectedIds)
    }
}
