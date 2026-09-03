package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

/**
 * Bulk-delete failure-path gaps in [ArrQueueViewModel] NOT pinned by
 * [ArrQueueViewModelTest] (whose bulk coverage is success-only):
 *
 * 1. [ArrQueueViewModel.deleteSelected] failure emits the message-seal error
 *    (Raw when the exception carries a message), RESETS
 *    [ArrQueueUiState.actionInProgress], but KEEPS the selection so the user
 *    can retry — only success clears it.
 * 2. A successful bulk delete WITHOUT searchAgain fires no
 *    `searchForTmdb` commands at all.
 * 3. [ArrQueueViewModel.refresh] failure surfaces the error through state (the
 *    flag-on path with a later explicit refresh — the main suite only pins the
 *    failure from its first warmed refresh).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrQueueViewModelBulkFailureGapsTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (ArrQueueViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var experimentalSlice: MutableStateFlow<ExperimentalSlice>
    private lateinit var queueFlow: MutableStateFlow<List<ArrQueueItem>>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        arrRepository = mockk()
        experimentalStore = mockk()
        experimentalSlice = MutableStateFlow(ExperimentalSlice())
        queueFlow = MutableStateFlow(emptyList())
        every { experimentalStore.experimental } returns experimentalSlice
        every { arrRepository.queue() } returns queueFlow
        coEvery { arrRepository.refreshQueue() } returns Result.success(Unit)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun enableDirectArr() {
        experimentalSlice.value = ExperimentalSlice(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION),
        )
    }

    private fun newViewModel(): ArrQueueViewModel = ArrQueueViewModel(
        arrRepository = arrRepository,
        experimentalStore = experimentalStore,
    )

    private fun item(queueId: Int, tmdbId: Int? = queueId) = ArrQueueItem(
        queueId = queueId,
        tmdbId = tmdbId,
        title = "Item $queueId",
        status = ArrDownloadStatus.DOWNLOADING,
        serverKind = ArrServiceKind.RADARR,
        serverId = "srv",
    )

    @Test
    fun deleteSelected_failure_emitsRaw_keepsSelection_andResetsTheBusyFlag() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.deleteQueueItems(any(), any()) } returns
            Result.failure(RuntimeException("radarr offline"))
        queueFlow.value = listOf(item(1), item(2))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.toggleSelection(item(1))
        viewModel.toggleSelection(item(2))

        viewModel.deleteSelected(blocklist = true, searchAgain = false)
        advanceUntilIdle()

        assertEquals(ArrQueueMessage.Raw("radarr offline"), viewModel.messages.first())
        assertFalse(viewModel.state.value.actionInProgress)
        // Failure keeps the selection alive for a retry.
        assertEquals(setOf("RADARR|1|srv", "RADARR|2|srv"), viewModel.state.value.selectedIds)
        assertTrue(viewModel.state.value.selectionMode)
        // No per-item searches fired on the failure path.
        coVerify(exactly = 0) { arrRepository.searchForTmdb(any(), any()) }
    }

    @Test
    fun deleteSelected_success_withoutSearchAgain_firesNoSearches() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.deleteQueueItems(any(), any()) } returns Result.success(Unit)
        queueFlow.value = listOf(item(1, tmdbId = 11), item(2, tmdbId = null))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.deleteSelected(blocklist = false, searchAgain = false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            arrRepository.deleteQueueItems(any(), ArrQueueDeleteOptions(removeFromClient = true, blocklist = false, skipRedownload = true))
        }
        coVerify(exactly = 0) { arrRepository.searchForTmdb(any(), any()) }
        // Success clears the selection even when items lack tmdb ids.
        assertEquals(emptySet(), viewModel.state.value.selectedIds)
        assertFalse(viewModel.state.value.selectionMode)
    }

    @Test
    fun refresh_failure_withNullMessage_surfacesNoError() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.refreshQueue() } returns Result.failure(RuntimeException(null as String?))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // HEAD mapping: a null exception message lands `null` in the error
        // slot (no fallback resource at this call site — only the message-seal
        // actions carry the unknown-error fallback).
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }
}
