package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEvent
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pins [SyncPlayOpenCoordinator]'s open-dedupe invariant against its own
 * interface: one open request per playing-item change, with the dedupe key
 * reset when the group is left — so rejoining a group playing the same item
 * still opens the player, while play-queue churn for the same item does not.
 */
class SyncPlayOpenCoordinatorTest {

    private val events = MutableSharedFlow<SyncPlayEvent>(extraBufferCapacity = 16)
    private val currentGroup = MutableStateFlow<SyncPlayGroup?>(
        SyncPlayGroup(groupId = "group-1", groupName = "Group", participantCount = 1),
    )

    private val syncPlayManager = mockk<SyncPlayManager>(relaxed = true)

    @Before
    fun setUp() {
        every { syncPlayManager.events } returns events
        every { syncPlayManager.currentGroupFlow } returns currentGroup
        every { syncPlayManager.isInSyncPlaySession } returns true
    }

    @Test
    fun `one open request per playing-item change, dedupe resets on leaving the group`() = runTest {
        // Stand-in for the ViewModel scope `start` runs on in production:
        // shares the test scheduler so advanceUntilIdle drives it, cancelled at
        // the end so the collectors never outlive the test.
        val lifecycleScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = SyncPlayOpenCoordinator(syncPlayManager)
        coordinator.start(lifecycleScope)
        advanceUntilIdle()

        val requests = mutableListOf<SyncPlayOpenRequest>()
        val collector = launch { coordinator.openRequests.collect { requests += it } }
        advanceUntilIdle()

        // Same item twice (e.g. pause/unpause queue updates) → one open.
        events.tryEmit(queueUpdate(playingItemId = "item-1", playlistItemId = "pl-1"))
        events.tryEmit(queueUpdate(playingItemId = "item-1", playlistItemId = "pl-1"))
        advanceUntilIdle()
        assertEquals(1, requests.size)
        assertEquals("item-1", requests[0].itemId)

        // Item change → open again.
        events.tryEmit(queueUpdate(playingItemId = "item-2", playlistItemId = "pl-2"))
        advanceUntilIdle()
        assertEquals(2, requests.size)

        // Leave the group → dedupe key resets; the same item opens on rejoin.
        currentGroup.value = null
        advanceUntilIdle()
        currentGroup.value = SyncPlayGroup(groupId = "group-2", groupName = "Group", participantCount = 1)
        advanceUntilIdle()
        events.tryEmit(queueUpdate(playingItemId = "item-2", playlistItemId = "pl-2"))
        advanceUntilIdle()
        assertEquals(3, requests.size)

        collector.cancel()
        lifecycleScope.cancel()
    }

    private fun queueUpdate(playingItemId: String, playlistItemId: String) =
        SyncPlayEvent.PlayQueueUpdate(
            SyncPlayQueueUpdateData(
                playlistItemIds = listOf(playlistItemId),
                itemIds = listOf(playingItemId),
                playingItemIndex = 0,
                playingItemId = playingItemId,
                playingPlaylistItemId = playlistItemId,
                startPositionTicks = 10_000L,
                isPlaying = false,
                whenMs = 0L,
                lastUpdateMs = 0L,
                repeatMode = SyncPlayRepeatMode.REPEAT_NONE,
                shuffleMode = SyncPlayShuffleMode.SORTED,
                reason = "test",
            ),
        )
}
