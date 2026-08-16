package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayBridgeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var playbackCore: SyncPlayPlaybackCore
    private lateinit var engine: MediaEngine
    private lateinit var bridge: SyncPlayBridge

    // Narrow session-state seam: captures the play/pause mirror writes the
    // ViewModel would apply to its UiState.
    private val isPlayingWrites = mutableListOf<Boolean>()

    private var engineProvider: () -> MediaEngine? = { engine }

    @Before
    fun setUp() {
        playbackCore = mockk(relaxed = true)
        every { playbackCore.ignoreWait } returns MutableStateFlow(false)
        syncPlayManager = mockk(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore
        every { syncPlayManager.isInSyncPlaySession } returns false
        engine = mockk(relaxed = true)
        every { engine.isPlaying } returns MutableStateFlow(false)
        every { engine.currentPositionMs } returns 0L
        every { engine.durationMs } returns 0L

        bridge = SyncPlayBridge(
            syncPlayManager = syncPlayManager,
            getMediaEngine = { engineProvider() },
            getCurrentItemId = { null },
            onLoadItem = { _, _ -> },
            setIsPlaying = { isPlayingWrites += it },
            scope = scope,
        )
    }

    // ─── PlaybackCoreCallbacks ────────────────────────────────────────────────

    @Test
    fun localPlay_callsEnginePlay() {
        bridge.localPlay()
        verify { engine.play() }
    }

    @Test
    fun localPause_callsEnginePause() {
        bridge.localPause()
        verify { engine.pause() }
    }

    @Test
    fun localSeek_callsEngineSeekWithMs() {
        bridge.localSeek(5_000L)
        verify { engine.seekTo(5_000L) }
    }

    @Test
    fun setPlaybackRate_callsEngineSetSpeed() {
        bridge.setPlaybackRate(1.25f)
        verify { engine.setPlaybackSpeed(1.25f) }
    }

    @Test
    fun currentPositionMs_returnsEnginePosition() {
        every { engine.currentPositionMs } returns 4_200L
        assertEquals(4_200L, bridge.currentPositionMs())
    }

    @Test
    fun currentPositionMs_withNoEngine_returnsZero() {
        engineProvider = { null }
        assertEquals(0L, bridge.currentPositionMs())
    }

    @Test
    fun durationMs_returnsEngineDuration() {
        every { engine.durationMs } returns 180_000L
        assertEquals(180_000L, bridge.durationMs())
    }

    @Test
    fun isPlaying_reflectsEngineState() {
        every { engine.isPlaying } returns MutableStateFlow(true)
        assertTrue(bridge.isPlaying())
    }

    @Test
    fun isPlaying_withNoEngine_returnsFalse() {
        engineProvider = { null }
        assertFalse(bridge.isPlaying())
    }

    @Test
    fun isBuffering_reflectsEnginePlaybackState() {
        every { engine.playbackState } returns MutableStateFlow(
            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.BUFFERING,
        )
        assertTrue(bridge.isBuffering())
    }

    @Test
    fun isBuffering_whenReady_returnsFalse() {
        every { engine.playbackState } returns MutableStateFlow(
            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.READY,
        )
        assertFalse(bridge.isBuffering())
    }

    @Test
    fun isBuffering_withNoEngine_returnsFalse() {
        engineProvider = { null }
        assertFalse(bridge.isBuffering())
    }

    // ─── onIsPlayingChanged group propagation ────────────────────────────────

    @Test
    fun onIsPlayingChanged_localStartWhileGroupPaused_propagatesUnpause() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        every { syncPlayManager.currentGroup } returns SyncPlayGroup(
            groupId = "g",
            groupName = "g",
            participantCount = 1,
            isPlaying = false,
        )
        every { playbackCore.lastCommand } returns null
        bridge.onIsPlayingChanged(true)
        coVerify { syncPlayManager.syncPlayController.unpause() }
    }

    @Test
    fun onIsPlayingChanged_groupAlreadyPlaying_doesNotEcho() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        every { syncPlayManager.currentGroup } returns SyncPlayGroup(
            groupId = "g",
            groupName = "g",
            participantCount = 1,
            isPlaying = true,
        )
        bridge.onIsPlayingChanged(true)
        coVerify(exactly = 0) { syncPlayManager.syncPlayController.unpause() }
    }

    @Test
    fun onIsPlayingChanged_groupDrivenUnpause_doesNotEcho() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        every { syncPlayManager.currentGroup } returns SyncPlayGroup(
            groupId = "g",
            groupName = "g",
            participantCount = 1,
            isPlaying = false,
        )
        every { playbackCore.lastCommand } returns com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand(
            command = "Unpause",
            whenMs = 0L,
            positionTicks = 0L,
            playlistItemId = "",
            emittedAtMs = 0L,
        )
        bridge.onIsPlayingChanged(true)
        coVerify(exactly = 0) { syncPlayManager.syncPlayController.unpause() }
    }

    @Test
    fun onIsPlayingChanged_pauseEvent_doesNothing() {
        bridge.onIsPlayingChanged(false)
        coVerify(exactly = 0) { syncPlayManager.syncPlayController.unpause() }
    }

    // ─── onSyncStateChanged ───────────────────────────────────────────────────

    @Test
    fun onSyncStateChanged_synced_updatesUiState() {
        bridge.onSyncStateChanged(synced = true, syncing = false)
        assertTrue(bridge.state.value.isSyncPlaySynced)
        assertFalse(bridge.state.value.isSyncPlaySyncing)
    }

    @Test
    fun onSyncStateChanged_syncing_updatesUiState() {
        bridge.onSyncStateChanged(synced = false, syncing = true)
        assertFalse(bridge.state.value.isSyncPlaySynced)
        assertTrue(bridge.state.value.isSyncPlaySyncing)
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsCoreAndCallbacksAndSyncingFlag() {
        bridge.reset()
        verify { playbackCore.reset() }
        verify { playbackCore.clearCallbacks() }
        assertFalse(bridge.state.value.isSyncPlaySyncing)
    }

    // ─── setIgnoreWait ────────────────────────────────────────────────────────

    @Test
    fun setIgnoreWait_delegatesToPlaybackCore() {
        bridge.setIgnoreWait(true)
        verify { playbackCore.setIgnoreWait(true) }
    }

    // ─── togglePlayPause ──────────────────────────────────────────────────────

    @Test
    fun togglePlayPause_whenEnginePlaying_pausesAndNotifiesController() {
        every { engine.isPlaying } returns MutableStateFlow(true)
        bridge.togglePlayPause()
        verify { engine.pause() }
        coVerify { syncPlayManager.syncPlayController.pause() }
        assertEquals(listOf(false), isPlayingWrites)
    }

    @Test
    fun togglePlayPause_whenEngineNotPlaying_unpausesViaController() {
        every { engine.isPlaying } returns MutableStateFlow(false)
        bridge.togglePlayPause()
        verify(exactly = 0) { engine.pause() }
        coVerify { syncPlayManager.syncPlayController.unpause() }
    }

    // ─── seekTo ───────────────────────────────────────────────────────────────

    @Test
    fun seekTo_seeksEngineAndNotifiesControllerInTicks() {
        bridge.seekTo(5_000L)
        verify { engine.seekTo(5_000L) }
        coVerify { syncPlayManager.syncPlayController.seek(5_000L * 10_000) }
    }

    // ─── leaveGroup ───────────────────────────────────────────────────────────

    @Test
    fun leaveGroup_clearsSessionUiStateAndResetsCore() {
        bridge.leaveGroup()
        coVerify { syncPlayManager.leaveGroup() }
        verify { playbackCore.reset() }
        assertNull(bridge.state.value.syncPlayGroupName)
        assertEquals(0, bridge.state.value.syncPlayParticipantCount)
        assertFalse(bridge.state.value.isInSyncPlaySession)
        assertFalse(bridge.state.value.isSyncPlaySynced)
    }

    // ─── joinGroup / group-display state ────────────────────────────────────

    @Test
    fun joinGroup_populatesGroupDisplayState() {
        every { syncPlayManager.currentGroup } returns null
        bridge.joinGroup("group-1")

        val s = bridge.state.value
        assertTrue(s.isInSyncPlaySession)
        assertEquals("group-1", s.syncPlayGroupName)
        assertEquals(0, s.syncPlayParticipantCount)
        assertEquals(
            com.raulshma.jellyplay.core.model.SyncPlayRepeatMode.REPEAT_NONE,
            s.syncPlayRepeatMode,
        )
    }

    /** Item-switch semantics: reset() restores the default group-display state. */
    @Test
    fun reset_clearsGroupDisplayState() {
        every { syncPlayManager.currentGroup } returns null
        bridge.joinGroup("group-1")
        assertTrue(bridge.state.value.isInSyncPlaySession)

        bridge.reset()

        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState(),
            bridge.state.value,
        )
    }

    // ─── sendNextItem / sendPreviousItem / sendStop ───────────────────────────

    @Test
    fun sendStop_delegatesToController() {
        bridge.sendStop()
        coVerify { syncPlayManager.syncPlayController.stop() }
    }

    @Test
    fun sendNextItem_delegatesToController() {
        bridge.sendNextItem("pl-item-1")
        coVerify { syncPlayManager.syncPlayController.nextItem("pl-item-1") }
    }

    @Test
    fun sendPreviousItem_delegatesToController() {
        bridge.sendPreviousItem("pl-item-1")
        coVerify { syncPlayManager.syncPlayController.previousItem("pl-item-1") }
    }

    // ─── onPlaybackStateChanged gating ────────────────────────────────────────

    @Test
    fun onPlaybackStateChanged_whenNotInSession_isNoOp() {
        every { syncPlayManager.isInSyncPlaySession } returns false
        bridge.onPlaybackStateChanged(3)
        verify(exactly = 0) { playbackCore.onPlaybackStateChanged(any()) }
    }

    @Test
    fun onPlaybackStateChanged_whenNoEngine_isNoOp() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        engineProvider = { null }
        bridge.onPlaybackStateChanged(3)
        verify(exactly = 0) { playbackCore.onPlaybackStateChanged(any()) }
    }

    @Test
    fun onPlaybackStateChanged_whenInSessionWithEngine_delegatesToCore() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(3)
        verify { playbackCore.onPlaybackStateChanged(3) }
    }
}
