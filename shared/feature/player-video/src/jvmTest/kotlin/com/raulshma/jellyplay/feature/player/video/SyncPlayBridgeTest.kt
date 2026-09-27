package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayBridgeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var engine: MediaEngine
    private lateinit var bridge: SyncPlayBridge

    // Narrow session-state seam: captures the play/pause mirror writes the
    // ViewModel would apply to its UiState.
    private val isPlayingWrites = mutableListOf<Boolean>()

    private var engineProvider: () -> MediaEngine? = { engine }

    @BeforeTest
    fun setUp() {
        syncPlayManager = mockk(relaxed = true)
        every { syncPlayManager.isInSyncPlaySession } returns false
        every { syncPlayManager.ignoreWait } returns MutableStateFlow(false)
        // start() launches the event listener on the Unconfined scope; a
        // RELAXED mock for `events` makes that collector throw
        // KotlinNothingValueException inside the launched coroutine, which
        // surfaces as UncaughtExceptionsBeforeTest in a LATER test class.
        // A real never-emitting flow parks the collector until teardown cancels it.
        every { syncPlayManager.events } returns MutableSharedFlow()
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

    /**
     * start()/reattachSession() spawn the event-listener collector on the
     * class-level Unconfined scope; without this teardown a leaked collector
     * can throw into the NEXT test class in the same JVM (UncaughtExceptionsBeforeTest).
     */
    @AfterTest
    fun tearDown() {
        scope.cancel()
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
        every { syncPlayManager.lastGroupCommandWasUnpause } returns false
        bridge.onIsPlayingChanged(true)
        coVerify { syncPlayManager.unpauseGroup() }
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
        coVerify(exactly = 0) { syncPlayManager.unpauseGroup() }
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
        every { syncPlayManager.lastGroupCommandWasUnpause } returns true
        bridge.onIsPlayingChanged(true)
        coVerify(exactly = 0) { syncPlayManager.unpauseGroup() }
    }

    @Test
    fun onIsPlayingChanged_pauseEvent_doesNothing() {
        bridge.onIsPlayingChanged(false)
        coVerify(exactly = 0) { syncPlayManager.unpauseGroup() }
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
        verify { syncPlayManager.resetPlaybackSync() }
        verify { syncPlayManager.detachSession() }
        assertFalse(bridge.state.value.isSyncPlaySyncing)
    }

    // ─── start / reattachSession: shared attach core ──────────────────────────

    /**
     * Pins the folded attach core: attachSession (the replace-by-construction
     * registration — the former defensive clear → setCallbacks pair is gone)
     * → group display population → core playlist id, in that order (the
     * former byte-identical start()/reattachSession() bodies).
     */
    @Test
    fun start_registersSessionAndPopulatesLiveGroup() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        every { syncPlayManager.currentGroup } returns SyncPlayGroup(
            groupId = "g",
            groupName = "grp",
            participantCount = 2,
            isPlaying = false,
        )
        bridge.start()
        verifyOrder {
            syncPlayManager.attachSession(bridge)
            syncPlayManager.onQueueItemChanged(null)
        }
        assertEquals("grp", bridge.state.value.syncPlayGroupName)
        assertEquals(2, bridge.state.value.syncPlayParticipantCount)
    }

    @Test
    fun reattachSession_whenInSession_reRegistersAndPopulates() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        every { syncPlayManager.currentGroup } returns SyncPlayGroup(
            groupId = "g",
            groupName = "grp",
            participantCount = 3,
            isPlaying = true,
            playingPlaylistItemId = "pl-1",
        )
        bridge.reattachSession()
        verifyOrder {
            syncPlayManager.attachSession(bridge)
            syncPlayManager.onQueueItemChanged("pl-1")
        }
        assertEquals("grp", bridge.state.value.syncPlayGroupName)
        assertEquals(3, bridge.state.value.syncPlayParticipantCount)
    }

    @Test
    fun reattachSession_whenNotInSession_isNoOp() {
        every { syncPlayManager.isInSyncPlaySession } returns false
        bridge.reattachSession()
        verify(exactly = 0) { syncPlayManager.attachSession(any()) }
        verify(exactly = 0) { syncPlayManager.onQueueItemChanged(any()) }
    }

    // ─── setIgnoreWait ────────────────────────────────────────────────────────

    @Test
    fun setIgnoreWait_delegatesToManager() {
        bridge.setIgnoreWait(true)
        verify { syncPlayManager.setIgnoreWait(true) }
    }

    // ─── togglePlayPause ──────────────────────────────────────────────────────

    @Test
    fun togglePlayPause_whenEnginePlaying_pausesAndNotifiesGroup() {
        every { engine.isPlaying } returns MutableStateFlow(true)
        bridge.togglePlayPause()
        verify { engine.pause() }
        coVerify { syncPlayManager.pauseGroup() }
        assertEquals(listOf(false), isPlayingWrites)
    }

    @Test
    fun togglePlayPause_whenEngineNotPlaying_unpausesGroup() {
        every { engine.isPlaying } returns MutableStateFlow(false)
        bridge.togglePlayPause()
        verify(exactly = 0) { engine.pause() }
        coVerify { syncPlayManager.unpauseGroup() }
    }

    // ─── seekTo ───────────────────────────────────────────────────────────────

    @Test
    fun seekTo_seeksEngineAndNotifiesGroupInTicks() {
        bridge.seekTo(5_000L)
        verify { engine.seekTo(5_000L) }
        coVerify { syncPlayManager.seekGroup(5_000L * 10_000) }
    }

    // ─── leaveGroup ───────────────────────────────────────────────────────────

    @Test
    fun leaveGroup_clearsSessionUiStateAndResetsCore() {
        bridge.leaveGroup()
        coVerify { syncPlayManager.leaveGroup() }
        verify { syncPlayManager.resetPlaybackSync() }
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
        assertEquals(s.syncPlayGroupName, "group-1")
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
    fun sendStop_delegatesToGroupTransport() {
        bridge.sendStop()
        coVerify { syncPlayManager.stopGroup() }
    }

    @Test
    fun sendNextItem_delegatesToGroupTransport() {
        bridge.sendNextItem("pl-item-1")
        coVerify { syncPlayManager.nextQueueItem("pl-item-1") }
    }

    @Test
    fun sendPreviousItem_delegatesToGroupTransport() {
        bridge.sendPreviousItem("pl-item-1")
        coVerify { syncPlayManager.previousQueueItem("pl-item-1") }
    }

    // ─── onPlaybackStateChanged gating ────────────────────────────────────────

    @Test
    fun onPlaybackStateChanged_whenNotInSession_isNoOp() {
        every { syncPlayManager.isInSyncPlaySession } returns false
        bridge.onPlaybackStateChanged(EnginePlaybackState.READY)
        verify(exactly = 0) { syncPlayManager.onPlaybackStateChanged(any()) }
    }

    @Test
    fun onPlaybackStateChanged_whenNoEngine_isNoOp() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        engineProvider = { null }
        bridge.onPlaybackStateChanged(EnginePlaybackState.READY)
        verify(exactly = 0) { syncPlayManager.onPlaybackStateChanged(any()) }
    }

    @Test
    fun onPlaybackStateChanged_whenInSessionWithEngine_delegatesToCore() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(EnginePlaybackState.READY)
        verify { syncPlayManager.onPlaybackStateChanged(3) }
    }

    /**
     * Pin of the engine→core fold's non-obvious arms: ENDED encodes to the
     * core's `4` (an arm the core's `when` deliberately ignores) and ERROR to
     * `1` (the core's stopped/idle no-op arm) — both previously encoded
     * inline in the ViewModel.
     */
    @Test
    fun onPlaybackStateChanged_ended_foldsToCoreEndedCode() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(EnginePlaybackState.ENDED)
        verify { syncPlayManager.onPlaybackStateChanged(4) }
    }

    @Test
    fun onPlaybackStateChanged_error_foldsToCoreStoppedCode() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(EnginePlaybackState.ERROR)
        verify { syncPlayManager.onPlaybackStateChanged(1) }
    }

    @Test
    fun onPlaybackStateChanged_buffering_foldsToCoreBufferingCode() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(EnginePlaybackState.BUFFERING)
        verify { syncPlayManager.onPlaybackStateChanged(2) }
    }

    @Test
    fun onPlaybackStateChanged_idle_foldsToCoreIdleCode() {
        every { syncPlayManager.isInSyncPlaySession } returns true
        bridge.onPlaybackStateChanged(EnginePlaybackState.IDLE)
        verify { syncPlayManager.onPlaybackStateChanged(1) }
    }
}
