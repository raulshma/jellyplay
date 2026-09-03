package com.raulshma.jellyplay.feature.syncplay

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
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
 * Boundary invariants of [SyncPlayViewModel.loadGroups] NOT pinned by
 * [SyncPlayViewModelTest]:
 *
 * 1. Reloading groups while inside a group PRESERVES [SyncPlayUiState.currentGroup]
 *    (the `if (state.isInGroup) state.currentGroup else null` guard) — a
 *    background poll must not blank the group header mid-session.
 * 2. Reloading while NOT in a group clears any stale currentGroup.
 * 3. The auto-accept-invites path requires a non-empty result: an empty group
 *    list never triggers a join even with the preference on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayViewModelLoadGroupsBoundaryTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (SyncPlayViewModelTest pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var syncPlayCastStore: SyncPlayCastStore
    private lateinit var eventsFlow: MutableSharedFlow<com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent>
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
        mediaRepository = mediaRepository,
        syncPlayManager = syncPlayManager,
        syncPlayCastStore = syncPlayCastStore,
    )

    private fun group(id: String, name: String = "Group $id") =
        SyncPlayGroup(groupId = id, groupName = name, participantCount = 1)

    @Test
    fun reloading_groups_while_in_a_group_keeps_currentGroup() = runTest(mainDispatcher) {
        every { syncPlayManager.activeGroupId } returns "g1"
        coEvery { syncPlayManager.joinGroup("g1") } returns Result.success(Unit)
        coEvery { mediaRepository.getSyncPlayInfo("g1") } returns Result.success(
            SyncPlayGroupInfo(groupId = "g1", groupName = "Party"),
        )
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.joinGroup("g1")
        advanceUntilIdle()
        assertEquals("Party", viewModel.uiState.value.currentGroup?.groupName)

        // A fresh poll (e.g. refreshGroups/loadGroups cycle) with an empty
        // server list must not blank the live group header.
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())
        viewModel.loadGroups()
        advanceUntilIdle()

        assertEquals("Party", viewModel.uiState.value.currentGroup?.groupName)
        assertTrue(viewModel.uiState.value.isInGroup)
    }

    @Test
    fun reloading_groups_while_not_in_a_group_clears_stale_currentGroup() = runTest(mainDispatcher) {
        // Seed a stale group (simulating a leftover header after an ejection
        // that bypassed state resets) and reload.
        every { syncPlayManager.activeGroupId } returns null
        val viewModel = newViewModel()
        advanceUntilIdle()
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(listOf(group("g1")))
        viewModel.loadGroups()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentGroup)
        assertFalse(viewModel.uiState.value.isInGroup)
    }

    @Test
    fun auto_accept_with_an_empty_group_list_joins_nothing() = runTest(mainDispatcher) {
        castPrefs.value = SyncPlayCastSlice(syncPlayAutoAcceptInvites = true)
        coEvery { mediaRepository.getSyncPlayGroups() } returns Result.success(emptyList())

        val viewModel = newViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncPlayManager.joinGroup(any()) }
        assertFalse(viewModel.uiState.value.isInGroup)
    }
}
