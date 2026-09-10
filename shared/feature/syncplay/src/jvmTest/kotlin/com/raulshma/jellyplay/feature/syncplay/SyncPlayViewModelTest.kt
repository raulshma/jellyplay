package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.feature.syncplay.generated.resources.Res
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_create_group
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_join_group
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_leave_group
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_load_groups
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_join_disabled
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * SyncPlay ViewModel coverage (downloads conveyor test style): group loading
 * with the message-seam fallbacks, the join-behavior preference fan-out, the
 * create→auto-join flow, the WebSocket event projection (incl. the empty
 * GroupUpdate reconnect-grace branch) and transport delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (downloads/music/livetv conveyor port
    // pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: SyncPlayRepository
    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var syncPlayCastStore: SyncPlayCastStore

    /** Backing flow behind SyncPlayManager.events so tests can push events. */
    private lateinit var eventsFlow: MutableSharedFlow<SyncPlayEvent>

    /** Backing flow behind SyncPlayCastStore.syncPlayCast. */
    private lateinit var castPrefs: MutableStateFlow<SyncPlayCastSlice>

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        syncPlayManager = mockk()
        syncPlayCastStore = mockk()
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        castPrefs = MutableStateFlow(SyncPlayCastSlice())
        every { syncPlayManager.events } returns eventsFlow
        // Non-suspend val reads → `every`, not `coEvery`.
        every { syncPlayManager.activeGroupId } returns null
        every { syncPlayManager.lastReconnectMs } returns 0L
        every { syncPlayCastStore.syncPlayCast } returns castPrefs
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = SyncPlayViewModel(
        syncPlayRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
        syncPlayCastStore = syncPlayCastStore,
    )

    /** Joins `groupId` end-to-end (manager success + group info) so the event-listener path is live. */
    private fun joinActive(
        groupId: String = "g1",
        groupName: String = "Party",
        isPlaying: Boolean = false,
    ) {
        every { syncPlayManager.activeGroupId } returns groupId
        coEvery { syncPlayManager.joinGroup(groupId) } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayInfo(groupId) } returns Result.success(
            SyncPlayGroupInfo(groupId = groupId, groupName = groupName, isPlaying = isPlaying),
        )
    }

    private fun group(id: String, name: String = "Group $id") =
        SyncPlayGroup(groupId = id, groupName = name, participantCount = 1)

    private fun queueUpdate(
        playingItemId: String = "item1",
        startPositionTicks: Long = 42L,
        isPlaying: Boolean = true,
    ) = SyncPlayQueueUpdateData(
        playlistItemIds = emptyList(),
        itemIds = emptyList(),
        playingItemIndex = 0,
        playingItemId = playingItemId,
        playingPlaylistItemId = "pl-$playingItemId",
        startPositionTicks = startPositionTicks,
        isPlaying = isPlaying,
        whenMs = 0L,
        lastUpdateMs = 0L,
        repeatMode = SyncPlayRepeatMode.REPEAT_NONE,
        shuffleMode = SyncPlayShuffleMode.SORTED,
        reason = "",
    )

    // ── group loading ─────────────────────────────────────────────────────

    @Test
    fun init_loads_groups_and_clears_loading() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1")))

        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("g1"), state.groups.map { it.groupId })
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isInGroup)
    }

    @Test
    fun init_load_failure_without_message_falls_back_to_resource() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSyncPlayGroups() } returns
            Result.failure(RuntimeException(null as String?))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertSame(
            Res.string.syncplay_error_load_groups,
            (viewModel.uiState.value.error as SyncPlayMessage.Resource).res,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun init_load_failure_with_message_maps_to_raw() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.failure(RuntimeException("boom"))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals("boom", (viewModel.uiState.value.error as SyncPlayMessage.Raw).text)
    }

    // ── join behaviour preference fan-out ────────────────────────────────

    @Test
    fun auto_accept_invites_joins_first_group_on_load() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayAutoAcceptInvites = true)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1"), group("g2")))
        joinActive("g1")

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isInGroup)
        assertEquals("g1", viewModel.uiState.value.currentGroup?.groupId)
        coVerify(exactly = 1) { syncPlayManager.joinGroup("g1") }
    }

    @Test
    fun without_auto_accept_no_group_is_joined_automatically() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1")))

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isInGroup)
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
    }

    @Test
    fun requestJoin_always_join_joins_immediately() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayJoinBehavior = SyncPlayJoinBehavior.ALWAYS_JOIN)
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.requestJoin(group("g1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { syncPlayManager.joinGroup("g1") }
        assertNull(viewModel.uiState.value.pendingJoin)
    }

    @Test
    fun requestJoin_ask_surfaces_pendingJoin_without_joining() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayJoinBehavior = SyncPlayJoinBehavior.ASK)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.requestJoin(group("g1", "Party"))

        assertEquals("g1", viewModel.uiState.value.pendingJoin?.groupId)
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
    }

    @Test
    fun requestJoin_never_join_emits_disabled_notification() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayJoinBehavior = SyncPlayJoinBehavior.NEVER_JOIN)
        val viewModel = newViewModel()
        advanceUntilIdle()
        val received = mutableListOf<SyncPlayMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notifications.collect { received += it }
        }

        viewModel.requestJoin(group("g1"))
        advanceUntilIdle()

        assertEquals(
            listOf<SyncPlayMessage>(SyncPlayMessage.Resource(Res.string.syncplay_join_disabled)),
            received,
        )
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
    }

    @Test
    fun confirmJoin_clears_pending_and_joins() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayJoinBehavior = SyncPlayJoinBehavior.ASK)
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.requestJoin(group("g1", "Party"))

        viewModel.confirmJoin()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingJoin)
        assertTrue(viewModel.uiState.value.isInGroup)
        coVerify(exactly = 1) { syncPlayManager.joinGroup("g1") }
    }

    @Test
    fun cancelJoin_clears_pending_without_joining() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayJoinBehavior = SyncPlayJoinBehavior.ASK)
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.requestJoin(group("g1"))

        viewModel.cancelJoin()

        assertNull(viewModel.uiState.value.pendingJoin)
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
    }

    // ── join / leave flows ────────────────────────────────────────────────

    @Test
    fun joinGroup_success_sets_in_group_and_loads_current_group() = runTest(mainDispatcher) {
        joinActive("g1", groupName = "Party")
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.joinGroup("g1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInGroup)
        assertEquals("Party", state.currentGroup?.groupName)
        assertFalse(state.isLoading)
    }

    @Test
    fun joinGroup_failure_falls_back_to_resource_and_raw_message() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        coEvery { syncPlayManager.joinGroup("g1") } returns Result.failure(RuntimeException(null as String?))
        coEvery { syncPlayManager.joinGroup("g2") } returns Result.failure(RuntimeException("nope"))

        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertSame(
            Res.string.syncplay_error_join_group,
            (viewModel.uiState.value.error as SyncPlayMessage.Resource).res,
        )
        assertFalse(viewModel.uiState.value.isInGroup)

        viewModel.joinGroup("g2")
        advanceUntilIdle()
        assertEquals("nope", (viewModel.uiState.value.error as SyncPlayMessage.Raw).text)
    }

    @Test
    fun leaveGroup_success_resets_state_and_reloads_groups() = runTest(mainDispatcher) {
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInGroup)
        coEvery { syncPlayManager.leaveGroup() } returns Result.success(Unit)

        viewModel.leaveGroup()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInGroup)
        assertNull(state.currentGroup)
        // init load + the post-leave reload.
        coVerify(exactly = 2) { mediaRepository.getSyncPlayGroups() }
    }

    @Test
    fun leaveGroup_failure_maps_error() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        coEvery { syncPlayManager.leaveGroup() } returns Result.failure(RuntimeException(null as String?))

        viewModel.leaveGroup()
        advanceUntilIdle()

        assertSame(
            Res.string.syncplay_error_leave_group,
            (viewModel.uiState.value.error as SyncPlayMessage.Resource).res,
        )
    }

    // ── create flow ───────────────────────────────────────────────────────

    @Test
    fun createGroup_success_joins_newly_named_group() = runTest(mainDispatcher) {
        joinActive("g9", groupName = "Party")
        coEvery { mediaRepository.createSyncPlayGroup("Party") } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g9", "Party")))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.createGroup("Party")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showCreateDialog)
        assertTrue(state.isInGroup)
        assertEquals("g9", state.currentGroup?.groupId)
    }

    @Test
    fun createGroup_success_without_matching_group_skips_join() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSyncPlayGroup("Party") } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.createGroup("Party")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCreateDialog)
        assertFalse(viewModel.uiState.value.isInGroup)
        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
    }

    @Test
    fun createGroup_failure_falls_back_to_resource() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSyncPlayGroup("Party") } returns
            Result.failure(RuntimeException(null as String?))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.createGroup("Party")
        advanceUntilIdle()

        assertSame(
            Res.string.syncplay_error_create_group,
            (viewModel.uiState.value.error as SyncPlayMessage.Resource).res,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── WebSocket event projection ────────────────────────────────────────

    @Test
    fun playQueueUpdate_synthesizes_group_info_when_absent() = runTest(mainDispatcher) {
        joinActive("g1")
        // Group info fetch fails → currentGroup stays null until the event.
        coEvery { mediaRepository.getSyncPlayInfo("g1") } returns Result.failure(RuntimeException("offline"))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.currentGroup)

        eventsFlow.tryEmit(SyncPlayEvent.PlayQueueUpdate(queueUpdate()))
        advanceUntilIdle()

        assertEquals(
            SyncPlayGroupInfo(groupId = "", groupName = "", playingItemId = "item1", isPlaying = true, positionTicks = 42L),
            viewModel.uiState.value.currentGroup,
        )
    }

    @Test
    fun stateUpdate_toggles_isPlaying_on_current_group() = runTest(mainDispatcher) {
        joinActive("g1", isPlaying = false)
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.currentGroup!!.isPlaying)

        eventsFlow.tryEmit(SyncPlayEvent.StateUpdate(isPlaying = true, state = "Playing", reason = ""))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.currentGroup!!.isPlaying)
    }

    @Test
    fun non_empty_groupUpdate_refreshes_current_group() = runTest(mainDispatcher) {
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()

        eventsFlow.tryEmit(SyncPlayEvent.GroupUpdate(groupName = "Party", participantCount = 3))
        advanceUntilIdle()

        assertEquals("g1", viewModel.uiState.value.currentGroup?.groupId)
        assertTrue(viewModel.uiState.value.isInGroup)
    }

    @Test
    fun empty_groupUpdate_after_grace_window_marks_group_left() = runTest(mainDispatcher) {
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInGroup)

        eventsFlow.tryEmit(SyncPlayEvent.GroupUpdate(groupName = "", participantCount = 0))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isInGroup)
        assertNull(viewModel.uiState.value.currentGroup)
    }

    @Test
    fun empty_groupUpdate_within_reconnect_grace_keeps_group() = runTest(mainDispatcher) {
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInGroup)
        // Reconnect "just happened": inside the 5 s grace window.
        every { syncPlayManager.lastReconnectMs } returns System.currentTimeMillis() - 1_000

        eventsFlow.tryEmit(SyncPlayEvent.GroupUpdate(groupName = "", participantCount = 0))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isInGroup)
        assertEquals("g1", viewModel.uiState.value.currentGroup?.groupId)
    }

    @Test
    fun notification_event_emits_raw_message() = runTest(mainDispatcher) {
        joinActive("g1")
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        val received = mutableListOf<SyncPlayMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.notifications.collect { received += it }
        }

        eventsFlow.tryEmit(SyncPlayEvent.Notification(message = "server says hi"))
        advanceUntilIdle()

        assertEquals(listOf<SyncPlayMessage>(SyncPlayMessage.Raw("server says hi")), received)
    }

    // ── transport delegation ──────────────────────────────────────────────

    @Test
    fun togglePlayback_pauses_when_playing_unpauses_when_paused() = runTest(mainDispatcher) {
        coEvery { mediaRepository.syncPlayPause() } returns Result.success(Unit)
        coEvery { mediaRepository.syncPlayUnpause() } returns Result.success(Unit)
        joinActive("g1", isPlaying = true)
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()

        viewModel.togglePlayback()
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.syncPlayPause() }
        coVerify(exactly = 0) { mediaRepository.syncPlayUnpause() }

        eventsFlow.tryEmit(SyncPlayEvent.StateUpdate(isPlaying = false, state = "Paused", reason = ""))
        advanceUntilIdle()
        viewModel.togglePlayback()
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.syncPlayUnpause() }
    }

    @Test
    fun togglePlayback_without_current_group_is_a_noop() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.togglePlayback()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.syncPlayPause() }
        coVerify(exactly = 0) { mediaRepository.syncPlayUnpause() }
    }

    @Test
    fun transport_actions_delegate_to_repository() = runTest(mainDispatcher) {
        coEvery { mediaRepository.syncPlaySeek(123L) } returns Result.success(Unit)
        coEvery { mediaRepository.syncPlayStop() } returns Result.success(Unit)
        coEvery { mediaRepository.syncPlaySetRepeatMode(SyncPlayRepeatMode.REPEAT_ALL) } returns Result.success(Unit)
        coEvery { mediaRepository.syncPlaySetShuffleMode(SyncPlayShuffleMode.SHUFFLE) } returns Result.success(Unit)
        coEvery { mediaRepository.syncPlaySetIgnoreWait(true) } returns Result.success(Unit)
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.seekTo(123L)
        viewModel.stop()
        viewModel.setRepeatMode(SyncPlayRepeatMode.REPEAT_ALL)
        viewModel.setShuffleMode(SyncPlayShuffleMode.SHUFFLE)
        viewModel.setIgnoreWait(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.syncPlaySeek(123L) }
        coVerify(exactly = 1) { mediaRepository.syncPlayStop() }
        coVerify(exactly = 1) { mediaRepository.syncPlaySetRepeatMode(SyncPlayRepeatMode.REPEAT_ALL) }
        coVerify(exactly = 1) { mediaRepository.syncPlaySetShuffleMode(SyncPlayShuffleMode.SHUFFLE) }
        coVerify(exactly = 1) { mediaRepository.syncPlaySetIgnoreWait(true) }
    }

    // ── misc state ────────────────────────────────────────────────────────

    @Test
    fun refreshGroups_updates_list_and_failure_is_silent() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1")))
        viewModel.refreshGroups()
        advanceUntilIdle()
        assertEquals(listOf("g1"), viewModel.uiState.value.groups.map { it.groupId })

        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.failure(RuntimeException("offline"))
        viewModel.refreshGroups()
        advanceUntilIdle()
        // Silent by design (background poll): list kept, no error surfaced.
        assertEquals(listOf("g1"), viewModel.uiState.value.groups.map { it.groupId })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun updateShowCreateDialog_toggles_flag() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.updateShowCreateDialog(true)
        assertTrue(viewModel.uiState.value.showCreateDialog)

        viewModel.updateShowCreateDialog(false)
        assertFalse(viewModel.uiState.value.showCreateDialog)
    }
}
