package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalFeatureGate
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dialog-machine gating gaps in [ArrQueueViewModel] NOT pinned by
 * [ArrQueueViewModelTest] (whose `pending_action_dialog_state_transitions`
 * covers only the idle hold/dismiss path):
 *
 * 1. Clear-BEFORE-action: confirming a delete closes the dialog
 *    ([ArrQueueUiState.pendingAction] null) while the repository call is
 *    still in flight ([ArrQueueUiState.actionInProgress] true) — the dialog
 *    never lingers over the busy flag.
 * 2. A failed action NEVER reopens the dialog: the failure surfaces through
 *    the message seal only (the clear-before-action arm's documented rule).
 * 3. The in-flight dismiss guard: [ArrQueueViewModel.dismissAction] fed the
 *    site's busy flag refuses to clear a pending dialog while an action is
 *    in flight, and dismisses once the flag settles (the
 *    [com.raulshma.jellyplay.core.model.PendingConfirmation.dismiss] gate's
 *    VM wiring — the pure algebra itself is pinned in core:model's
 *    PendingConfirmationTest).
 * 4. The bulk-delete confirm follows the same clear-before-action ladder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrQueueViewModelDialogGapsTest {

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

    private fun newViewModel(): ArrQueueViewModel = ArrQueueViewModel(
        arrRepository = arrRepository,
        experimentalGate = ExperimentalFeatureGate(experimentalStore, CoroutineScope(mainDispatcher)),
    )

    private fun item(queueId: Int) = ArrQueueItem(
        queueId = queueId,
        tmdbId = queueId,
        title = "Item $queueId",
        status = ArrDownloadStatus.DOWNLOADING,
        serverKind = ArrServiceKind.RADARR,
        serverId = "srv",
    )

    /** Parks the repository call on [gate] so the test owns its settle timing. */
    private fun gateDeleteRowOn(gate: CompletableDeferred<Unit>) {
        coEvery { arrRepository.deleteQueueRow(any(), any(), any()) } coAnswers {
            gate.await()
            Result.success(Unit)
        }
    }

    @Test
    fun confirm_closesTheDialogBeforeTheRepositoryCallSettles() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        gateDeleteRowOn(gate)
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = item(1)

        viewModel.showDeleteDialog(target)
        assertNotNull(viewModel.state.value.pendingAction)

        viewModel.deleteItem(target, blocklist = false, searchAgain = false)
        runCurrent()

        // Clear-before-action: dialog closed while the delete is in flight.
        assertNull(viewModel.state.value.pendingAction)
        assertTrue(viewModel.state.value.actionInProgress)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.actionInProgress)
        assertNull(viewModel.state.value.pendingAction)
    }

    @Test
    fun delete_failure_neverReopensTheDialog() = runTest(mainDispatcher) {
        coEvery { arrRepository.deleteQueueRow(any(), any(), any()) } returns
            Result.failure(RuntimeException("radarr offline"))
        val viewModel = newViewModel()
        advanceUntilIdle()
        val target = item(1)

        viewModel.showDeleteDialog(target)
        viewModel.deleteItem(target, blocklist = false, searchAgain = false)
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingAction)
        assertFalse(viewModel.state.value.actionInProgress)
        // The failure surfaces through the message seal, not the dialog.
        assertEquals(ArrQueueMessage.Raw("radarr offline"), viewModel.messages.first())
    }

    @Test
    fun dismiss_whileActionInProgress_isRefused_thenDismissesOnceSettled() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        gateDeleteRowOn(gate)
        val viewModel = newViewModel()
        advanceUntilIdle()

        // First action in flight (confirmed, dialog already cleared).
        viewModel.deleteItem(item(1), blocklist = false, searchAgain = false)
        runCurrent()
        assertTrue(viewModel.state.value.actionInProgress)

        // A second dialog held while the action is in flight — the row buttons
        // are disabled while busy, but the machine must still guard the state.
        val queued = item(2)
        viewModel.showDeleteDialog(queued)
        assertEquals(ArrQueueAction.Delete(queued), viewModel.state.value.pendingAction)

        viewModel.dismissAction()
        // In-flight guard: the dismiss is refused, the dialog stays open.
        assertEquals(ArrQueueAction.Delete(queued), viewModel.state.value.pendingAction)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.actionInProgress)

        viewModel.dismissAction()
        assertNull(viewModel.state.value.pendingAction)
    }

    @Test
    fun bulkConfirm_followsTheSameClearBeforeActionLadder() = runTest(mainDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { arrRepository.deleteQueueItems(any(), any()) } coAnswers {
            gate.await()
            Result.success(Unit)
        }
        queueFlow.value = listOf(item(1))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.showBulkDeleteDialog()
        assertEquals(ArrQueueAction.BulkDelete, viewModel.state.value.pendingAction)

        viewModel.deleteSelected(blocklist = false, searchAgain = false)
        runCurrent()

        assertNull(viewModel.state.value.pendingAction)
        assertTrue(viewModel.state.value.actionInProgress)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.actionInProgress)
    }
}
