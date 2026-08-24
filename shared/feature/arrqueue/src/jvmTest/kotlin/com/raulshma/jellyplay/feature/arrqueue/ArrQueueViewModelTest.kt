package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_grab_sent
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_import_sent
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_unknown_error
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ArrQueue ViewModel coverage (requests/downloads conveyor test style, no
 * legacy suite existed): the DIRECT_ARR_INTEGRATION flag gate on refresh, the
 * queue-flow mirror, selection state (toggle/selectAll/clear incl. the rowKey
 * format with the empty-serverId fallback), pending-action dialog state, the
 * delete-options mapping (removeFromClient=true, skipRedownload=!searchAgain),
 * the searchAgain→searchForTmdb fan-out (tmdbId-null rows skipped), bulk
 * filtering, and the ArrQueueMessage seal emissions per action outcome
 * (identity asserts on the generated StringResource, admin conveyor
 * precedent; cold first() works because the channel is BUFFERED — downloads
 * conveyor pattern).
 *
 * Init-timing note pinned by the flag tests: `directArrEnabled` is
 * stateIn(Eagerly, initial = false), and init calls refresh() synchronously
 * BEFORE the sharing coroutine runs — so the very first refresh always sees
 * the initial false and no-ops, exactly like HEAD (same rationale as
 * RequestsViewModel.directArrEnabled, quoted in the VM's KDoc).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrQueueViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (requests/downloads conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var arrRepository: ArrRepository
    private lateinit var experimentalStore: ExperimentalStore

    /** Backing flow behind ExperimentalStore.experimental (the flag gate). */
    private lateinit var experimentalSlice: MutableStateFlow<ExperimentalSlice>

    /** Backing flow behind ArrRepository.queue() (the queue mirror source). */
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

    private fun item(
        queueId: Int,
        title: String = "Item $queueId",
        tmdbId: Int? = queueId,
        kind: ArrServiceKind = ArrServiceKind.RADARR,
        serverId: String = "server-$kind",
    ) = ArrQueueItem(
        queueId = queueId,
        tmdbId = tmdbId,
        title = title,
        status = ArrDownloadStatus.DOWNLOADING,
        serverKind = kind,
        serverId = serverId,
    )

    // ── flag gate + refresh ───────────────────────────────────────────────

    @Test
    fun flag_disabled_refresh_is_noop_and_clears_transient_state() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.refreshQueue() }
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun flag_on_init_refresh_noops_then_warmed_refresh_drives_load_and_error() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.refreshQueue() } returns Result.failure(RuntimeException("offline"))

        val viewModel = newViewModel()
        // Init's synchronous refresh() read the pre-share initial false (see
        // class KDoc) — nothing fired yet.
        coVerify(exactly = 0) { arrRepository.refreshQueue() }
        advanceUntilIdle()

        // Once the Eagerly-shared flag has collected, refresh drives the load.
        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { arrRepository.refreshQueue() }
        val state = viewModel.state.value
        assertEquals("offline", state.error)
        assertFalse(state.isLoading)
    }

    // ── queue-flow mirror ─────────────────────────────────────────────────

    @Test
    fun queue_flow_mirrors_into_state() = runTest(mainDispatcher) {
        val first = item(1)
        queueFlow.value = listOf(first)

        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(listOf(first), viewModel.state.value.queue)

        val second = item(2, kind = ArrServiceKind.SONARR)
        queueFlow.value = listOf(first, second)
        advanceUntilIdle()
        assertEquals(listOf(first, second), viewModel.state.value.queue)
    }

    // ── selection + rowKey format ─────────────────────────────────────────

    @Test
    fun selection_toggle_select_all_and_clear_drive_selection_state() = runTest(mainDispatcher) {
        queueFlow.value = listOf(item(1), item(2))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleSelection(item(1))
        assertEquals(setOf("RADARR|1|server-RADARR"), viewModel.state.value.selectedIds)
        assertTrue(viewModel.state.value.selectionMode)

        viewModel.toggleSelection(item(1))
        assertEquals(emptySet(), viewModel.state.value.selectedIds)
        assertFalse(viewModel.state.value.selectionMode)

        viewModel.selectAll()
        assertEquals(2, viewModel.state.value.selectedIds.size)
        assertTrue(viewModel.state.value.selectionMode)

        viewModel.clearSelection()
        assertEquals(emptySet(), viewModel.state.value.selectedIds)
        assertFalse(viewModel.state.value.selectionMode)
    }

    @Test
    fun rowKey_formats_kind_queueid_serverid_with_underscore_fallback() = runTest(mainDispatcher) {
        queueFlow.value = listOf(
            item(1, kind = ArrServiceKind.RADARR, serverId = "srv-a"),
            item(2, kind = ArrServiceKind.SONARR, serverId = ""),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.selectAll()

        assertEquals(
            setOf("RADARR|1|srv-a", "SONARR|2|_"),
            viewModel.state.value.selectedIds,
        )
    }

    // ── pending-action dialog state ───────────────────────────────────────

    @Test
    fun pending_action_dialog_state_transitions() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.pendingAction)

        val delete = item(1)
        viewModel.showDeleteDialog(delete)
        assertEquals(ArrQueueAction.Delete(delete), viewModel.state.value.pendingAction)

        viewModel.showBulkDeleteDialog()
        assertEquals(ArrQueueAction.BulkDelete, viewModel.state.value.pendingAction)

        val grab = item(2)
        viewModel.showGrabDialog(grab)
        assertEquals(ArrQueueAction.Grab(grab), viewModel.state.value.pendingAction)

        val import = item(3)
        viewModel.showImportDialog(import)
        assertEquals(ArrQueueAction.Import(import), viewModel.state.value.pendingAction)

        viewModel.dismissAction()
        assertNull(viewModel.state.value.pendingAction)
    }

    // ── delete options mapping + searchAgain fan-out ──────────────────────

    @Test
    fun deleteItem_maps_dialog_flags_to_arr_options() = runTest(mainDispatcher) {
        val optionsSlot = slot<ArrQueueDeleteOptions>()
        coEvery { arrRepository.deleteQueueItem(any(), capture(optionsSlot)) } returns Result.success(Unit)
        coEvery { arrRepository.searchForTmdb(any(), any()) } returns Result.success(emptyList())
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = item(1)

        viewModel.deleteItem(target, blocklist = false, searchAgain = true)
        advanceUntilIdle()
        assertEquals(
            ArrQueueDeleteOptions(removeFromClient = true, blocklist = false, skipRedownload = false),
            optionsSlot.captured,
        )

        viewModel.deleteItem(target, blocklist = true, searchAgain = false)
        advanceUntilIdle()
        assertEquals(
            ArrQueueDeleteOptions(removeFromClient = true, blocklist = true, skipRedownload = true),
            optionsSlot.captured,
        )
        assertFalse(viewModel.state.value.actionInProgress)
    }

    @Test
    fun deleteItem_search_again_skips_items_without_tmdb() = runTest(mainDispatcher) {
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.success(Unit)
        coEvery { arrRepository.searchForTmdb(any(), any()) } returns Result.success(emptyList())
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.deleteItem(item(1, tmdbId = null), blocklist = false, searchAgain = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { arrRepository.searchForTmdb(any(), any()) }

        viewModel.deleteItem(item(2, tmdbId = 42, kind = ArrServiceKind.SONARR), blocklist = false, searchAgain = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { arrRepository.searchForTmdb(42, ArrServiceKind.SONARR) }
    }

    @Test
    fun deleteSelected_filters_to_selected_rows_fans_out_searches_and_clears() = runTest(mainDispatcher) {
        queueFlow.value = listOf(item(1, tmdbId = 11), item(2, tmdbId = 22), item(3, tmdbId = 33))
        val itemsSlot = slot<List<ArrQueueItem>>()
        coEvery { arrRepository.deleteQueueItems(capture(itemsSlot), any()) } returns Result.success(Unit)
        coEvery { arrRepository.searchForTmdb(any(), any()) } returns Result.success(emptyList())
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.toggleSelection(item(1))
        viewModel.toggleSelection(item(2))

        viewModel.deleteSelected(blocklist = true, searchAgain = true)
        advanceUntilIdle()

        // Only the selected rows are sent, with the mapped options.
        assertEquals(listOf(1, 2), itemsSlot.captured.map { it.queueId })
        coVerify(exactly = 1) {
            arrRepository.deleteQueueItems(any(), ArrQueueDeleteOptions(removeFromClient = true, blocklist = true, skipRedownload = false))
        }
        // Fire-and-forget per-item searches: selected tmdbIds only.
        coVerify(exactly = 1) { arrRepository.searchForTmdb(11, ArrServiceKind.RADARR) }
        coVerify(exactly = 1) { arrRepository.searchForTmdb(22, ArrServiceKind.RADARR) }
        coVerify(exactly = 0) { arrRepository.searchForTmdb(33, any()) }
        // Selection cleared + progress flag reset after the bulk action.
        assertEquals(emptySet(), viewModel.state.value.selectedIds)
        assertFalse(viewModel.state.value.selectionMode)
        assertFalse(viewModel.state.value.actionInProgress)
    }

    @Test
    fun deleteSelected_without_selection_is_a_noop() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.deleteSelected(blocklist = false, searchAgain = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.deleteQueueItems(any(), any()) }
    }

    // ── message seal emissions ────────────────────────────────────────────

    @Test
    fun deleteItem_failure_with_message_emits_raw() = runTest(mainDispatcher) {
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.failure(RuntimeException("boom"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.deleteItem(item(1), blocklist = false, searchAgain = false)
        advanceUntilIdle()

        assertEquals(ArrQueueMessage.Raw("boom"), viewModel.messages.first())
    }

    @Test
    fun deleteItem_failure_without_message_emits_unknown_error_resource() = runTest(mainDispatcher) {
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.failure(RuntimeException(null as String?))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.deleteItem(item(1), blocklist = false, searchAgain = false)
        advanceUntilIdle()

        assertEquals(ArrQueueMessage.Error(Res.string.arrqueue_unknown_error), viewModel.messages.first())
    }

    @Test
    fun grabItem_success_emits_info_with_title_arg_and_refreshes() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.grabQueueItem(any()) } returns Result.success(Unit)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.grabItem(item(1, title = "Movie 4K"))
        advanceUntilIdle()

        assertEquals(
            ArrQueueMessage.Info(Res.string.arrqueue_grab_sent, listOf("Movie 4K")),
            viewModel.messages.first(),
        )
        // Only the post-grab refresh fired — init's synchronous refresh()
        // no-ops on the pre-share initial false (class KDoc).
        coVerify(exactly = 1) { arrRepository.refreshQueue() }
    }

    @Test
    fun grabItem_failure_emits_error_fallback() = runTest(mainDispatcher) {
        coEvery { arrRepository.grabQueueItem(any()) } returns Result.failure(RuntimeException(null as String?))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.grabItem(item(1))
        advanceUntilIdle()

        assertEquals(ArrQueueMessage.Error(Res.string.arrqueue_unknown_error), viewModel.messages.first())
    }

    @Test
    fun importItem_success_emits_info_with_title_arg_and_refreshes() = runTest(mainDispatcher) {
        enableDirectArr()
        coEvery { arrRepository.importQueueItem(any()) } returns Result.success(Unit)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.importItem(item(1, title = "Episode 1"))
        advanceUntilIdle()

        assertEquals(
            ArrQueueMessage.Info(Res.string.arrqueue_import_sent, listOf("Episode 1")),
            viewModel.messages.first(),
        )
        coVerify(exactly = 1) { arrRepository.refreshQueue() }
    }

    @Test
    fun importItem_failure_emits_raw_message() = runTest(mainDispatcher) {
        coEvery { arrRepository.importQueueItem(any()) } returns Result.failure(RuntimeException("stuck"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.importItem(item(1))
        advanceUntilIdle()

        assertEquals(ArrQueueMessage.Raw("stuck"), viewModel.messages.first())
        assertFalse(viewModel.state.value.actionInProgress)
    }

    @Test
    fun successful_delete_emits_no_message() = runTest(mainDispatcher) {
        coEvery { arrRepository.deleteQueueItem(any(), any()) } returns Result.success(Unit)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.deleteItem(item(1), blocklist = false, searchAgain = false)
        advanceUntilIdle()

        val message = withTimeoutOrNull(50) { viewModel.messages.first() }
        assertNull(message)
    }
}
