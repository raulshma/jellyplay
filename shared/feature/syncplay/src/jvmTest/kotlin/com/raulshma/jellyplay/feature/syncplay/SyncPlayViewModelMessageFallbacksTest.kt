package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.feature.syncplay.generated.resources.Res
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_create_group
import com.raulshma.jellyplay.feature.syncplay.generated.resources.syncplay_error_leave_group
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Message-seam and guard gaps in [SyncPlayViewModel] NOT pinned by
 * [SyncPlayViewModelTest] / [SyncPlayViewModelLoadGroupsBoundaryTest]:
 *
 * 1. [SyncPlayViewModel.createGroup] and [SyncPlayViewModel.leaveGroup]
 *    failures WITH an exception message map to [SyncPlayMessage.Raw] (the two
 *    suites only pin the null-message → localized-resource fallbacks).
 * 2. [SyncPlayViewModel.joinGroup] success while
 *    [SyncPlayManager.activeGroupId] is still null: `isInGroup` flips true but
 *    [SyncPlayViewModel.loadCurrentGroup]'s early return keeps `currentGroup`
 *    null (and the event listener is live — a subsequent PlayQueueUpdate still
 *    synthesizes the header).
 * 3. [SyncPlayViewModel.leaveGroup] failure keeps the group membership state
 *    (isInGroup + currentGroup untouched) — only the error is surfaced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayViewModelMessageFallbacksTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (SyncPlayViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: SyncPlayRepository
    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var syncPlayCastStore: SyncPlayCastStore
    private lateinit var eventsFlow: MutableSharedFlow<SyncPlayEvent>
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

    private fun group(id: String, name: String = "Group $id") =
        SyncPlayGroup(groupId = id, groupName = name, participantCount = 1)

    @Test
    fun createGroup_failureWithMessage_mapsToRaw() = runTest(mainDispatcher) {
        coEvery { mediaRepository.createSyncPlayGroup("Party") } returns
            Result.failure(RuntimeException("name taken"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.createGroup("Party")
        advanceUntilIdle()

        assertEquals("name taken", (viewModel.uiState.value.error as SyncPlayMessage.Raw).text)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isInGroup)
    }

    @Test
    fun leaveGroup_failureWithMessage_mapsToRaw_andKeepsMembershipState() = runTest(mainDispatcher) {
        every { syncPlayManager.activeGroupId } returns "g1"
        coEvery { syncPlayManager.joinGroup("g1") } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayInfo("g1") } returns Result.success(
            SyncPlayGroupInfo(groupId = "g1", groupName = "Party"),
        )
        coEvery { syncPlayManager.leaveGroup() } returns Result.failure(RuntimeException("server busy"))
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInGroup)
        assertEquals("Party", viewModel.uiState.value.currentGroup?.groupName)

        viewModel.leaveGroup()
        advanceUntilIdle()

        assertEquals("server busy", (viewModel.uiState.value.error as SyncPlayMessage.Raw).text)
        // The failed leave leaves the session untouched — the user is still in
        // the group with its header intact.
        assertTrue(viewModel.uiState.value.isInGroup)
        assertEquals("Party", viewModel.uiState.value.currentGroup?.groupName)
        // No group reload fired on the failure path (init load only).
        coVerify(exactly = 1) { mediaRepository.getSyncPlayGroups() }
    }

    @Test
    fun joinGroup_success_withoutActiveGroupId_keepsCurrentGroupNull_butListensForEvents() =
        runTest(mainDispatcher) {
            // The manager accepted the join but hasn't asserted the active group
            // yet: the info fetch is skipped (early return), the header stays
            // blank until the first WebSocket event.
            coEvery { syncPlayManager.joinGroup("g1") } returns Result.success(Unit)
            every { syncPlayManager.activeGroupId } returns null
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.joinGroup("g1")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isInGroup)
            assertNull(viewModel.uiState.value.currentGroup)
            coVerify(exactly = 0) { mediaRepository.getSyncPlayInfo(any()) }

            // The event listener is live: the first PlayQueueUpdate synthesizes
            // the placeholder header.
            eventsFlow.tryEmit(
                SyncPlayEvent.PlayQueueUpdate(
                    com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData(
                        playlistItemIds = emptyList(),
                        itemIds = emptyList(),
                        playingItemIndex = 0,
                        playingItemId = "item1",
                        playingPlaylistItemId = "pl-1",
                        startPositionTicks = 7L,
                        isPlaying = true,
                        whenMs = 0L,
                        lastUpdateMs = 0L,
                        repeatMode = com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_NONE,
                        shuffleMode = com.raulshma.jellyplay.core.model.SyncPlayShuffleMode.SORTED,
                        reason = "",
                    ),
                ),
            )
            advanceUntilIdle()

            val header = viewModel.uiState.value.currentGroup
            assertEquals("item1", header?.playingItemId)
            assertEquals(7L, header?.positionTicks)
        }

    @Test
    fun loadGroups_error_isClearedByALaterSuccessfulReload() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.failure(RuntimeException("offline"))
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error is SyncPlayMessage.Raw)

        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1")))
        viewModel.loadGroups()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("g1"), viewModel.uiState.value.groups.map { it.groupId })
    }
}
