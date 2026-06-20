package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
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
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>
    private lateinit var uiState: StateFlowHandle<VideoPlayerUiState>
    private lateinit var bridge: SyncPlayBridge

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
        state = MutableStateFlow(VideoPlayerUiState())
        uiState = StateFlowHandle(state)

        bridge = SyncPlayBridge(
            syncPlayManager = syncPlayManager,
            uiState = uiState,
            getMediaEngine = { engineProvider() },
            getCurrentItemId = { null },
            onLoadItem = { _, _ -> },
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

    // ─── onSyncStateChanged ───────────────────────────────────────────────────

    @Test
    fun onSyncStateChanged_synced_updatesUiState() {
        bridge.onSyncStateChanged(synced = true, syncing = false)
        assertTrue(uiState.value.isSyncPlaySynced)
        assertFalse(uiState.value.isSyncPlaySyncing)
    }

    @Test
    fun onSyncStateChanged_syncing_updatesUiState() {
        bridge.onSyncStateChanged(synced = false, syncing = true)
        assertFalse(uiState.value.isSyncPlaySynced)
        assertTrue(uiState.value.isSyncPlaySyncing)
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsCoreAndCallbacksAndSyncingFlag() {
        bridge.reset()
        verify { playbackCore.reset() }
        verify { playbackCore.clearCallbacks() }
        assertFalse(uiState.value.isSyncPlaySyncing)
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
        assertFalse(uiState.value.isPlaying)
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
        assertNull(uiState.value.syncPlayGroupName)
        assertEquals(0, uiState.value.syncPlayParticipantCount)
        assertFalse(uiState.value.isInSyncPlaySession)
        assertFalse(uiState.value.isSyncPlaySynced)
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
