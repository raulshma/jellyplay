package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.data.playback.VideoQueueController
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.PlayerCastController
import com.raulshma.jellyplay.feature.player.video.PlayerSessionManager
import com.raulshma.jellyplay.feature.player.video.PlayerUiCommands
import com.raulshma.jellyplay.feature.player.video.RenderControls
import com.raulshma.jellyplay.feature.player.video.SessionRenderState
import com.raulshma.jellyplay.feature.player.video.SleepTimerController
import com.raulshma.jellyplay.feature.player.video.SubtitleManager
import com.raulshma.jellyplay.feature.player.video.SubtitleStyleController
import com.raulshma.jellyplay.feature.player.video.SyncPlayBridge
import com.raulshma.jellyplay.feature.player.video.TrackSelectionHelper
import com.raulshma.jellyplay.feature.player.video.VideoEffectsController
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The playback-mode verdicts' truth table ([SheetsSessionSlice.switchableEngines]
 * / [SheetsSessionSlice.localControlBlocked]) — the ONE fold the PlaybackMode
 * sheet renders and the ViewModel's SwitchPlayerEngine arm consumes. The
 * session-slice repair's contract: cast connectivity, SyncPlay activity,
 * engine identity and platform availability fold HERE, once — a second site
 * re-folding any of those terms is the defect shape the slice retired.
 *
 * Drive style: the slice graph's collaborators are relaxed mocks with REAL
 * StateFlows at every fold input (the [com.raulshma.jellyplay.feature.player.video.PlaybackSessionTestGraph]
 * discipline), on an Unconfined scheduler so the Eagerly-shared verdict flows
 * settle synchronously per write.
 */
class SheetsSessionSliceVerdictTest {

    /** The platform universe the tests parameterize: every embedded engine by default. */
    private val allPlatforms = listOf(PlayerType.EXO_PLAYER, PlayerType.MPV, PlayerType.LIBVLC)

    private fun graph(
        availablePlayerTypes: List<PlayerType> = allPlatforms,
    ): VerdictGraph = VerdictGraph(availablePlayerTypes)

    /** An engine whose identity is [type]'s display name (the fold's identity term). */
    private fun engineOf(type: PlayerType): MediaEngine =
        mockk(relaxed = true) { every { displayName } returns type.displayName }

    // ── switchableEngines ──────────────────────────────────────────────────

    @Test
    fun noEngine_switchableEnginesIsEmpty() {
        val g = graph()
        g.engineFlow.value = null
        assertEquals(emptyList(), g.slice.switchableEngines.value)
        assertFalse(g.slice.localControlBlocked.value)
    }

    @Test
    fun castConnected_switchableEnginesIsEmpty() {
        val g = graph()
        g.engineFlow.value = engineOf(PlayerType.EXO_PLAYER)
        assertTrue(g.slice.switchableEngines.value.isNotEmpty())
        g.castConnected.value = true
        assertEquals(emptyList(), g.slice.switchableEngines.value)
        assertTrue(g.slice.localControlBlocked.value)
        // The verdict RE-OPENS on disconnect — a blocked verdict is a state,
        // not a latch.
        g.castConnected.value = false
        assertEquals(listOf(PlayerType.MPV, PlayerType.LIBVLC), g.slice.switchableEngines.value)
        assertFalse(g.slice.localControlBlocked.value)
    }

    @Test
    fun syncPlayActive_switchableEnginesIsEmpty() {
        val g = graph()
        g.engineFlow.value = engineOf(PlayerType.MPV)
        assertTrue(g.slice.switchableEngines.value.isNotEmpty())
        g.syncPlayState.value = SyncPlayUiState(isInSyncPlaySession = true)
        assertEquals(emptyList(), g.slice.switchableEngines.value)
        assertTrue(g.slice.localControlBlocked.value)
    }

    @Test
    fun currentEngineExcluded_fromSwitchableEngines() {
        val g = graph()
        g.engineFlow.value = engineOf(PlayerType.MPV)
        // Platform order preserved, current engine dropped: the sheet's
        // engine chips and the VM's membership check see the same list.
        assertEquals(listOf(PlayerType.EXO_PLAYER, PlayerType.LIBVLC), g.slice.switchableEngines.value)
    }

    @Test
    fun unavailableTypeAbsent_fromSwitchableEngines() {
        // A platform offering only ExoPlayer: no other engine chip, no
        // switch to an unconstructed engine — and the current engine alone
        // in the universe folds to EMPTY, not to itself.
        val g = graph(availablePlayerTypes = listOf(PlayerType.EXO_PLAYER))
        g.engineFlow.value = engineOf(PlayerType.EXO_PLAYER)
        assertEquals(emptyList(), g.slice.switchableEngines.value)
        g.engineFlow.value = engineOf(PlayerType.MPV)
        assertEquals(listOf(PlayerType.EXO_PLAYER), g.slice.switchableEngines.value)
    }

    @Test
    fun engineSwapReemitsTheVerdict() {
        val g = graph()
        g.engineFlow.value = engineOf(PlayerType.EXO_PLAYER)
        assertEquals(listOf(PlayerType.MPV, PlayerType.LIBVLC), g.slice.switchableEngines.value)
        g.engineFlow.value = engineOf(PlayerType.LIBVLC)
        assertEquals(listOf(PlayerType.EXO_PLAYER, PlayerType.MPV), g.slice.switchableEngines.value)
    }

    // ── localControlBlocked ────────────────────────────────────────────────

    @Test
    fun localControlBlocked_trueOnCastOrSyncPlay_falseOtherwise() {
        val g = graph()
        g.engineFlow.value = engineOf(PlayerType.EXO_PLAYER)
        assertFalse(g.slice.localControlBlocked.value)
        g.syncPlayState.value = SyncPlayUiState(isInSyncPlaySession = true)
        assertTrue(g.slice.localControlBlocked.value)
        // Both terms together: still blocked (OR, not XOR).
        g.castConnected.value = true
        assertTrue(g.slice.localControlBlocked.value)
        g.syncPlayState.value = SyncPlayUiState(isInSyncPlaySession = false)
        assertTrue(g.slice.localControlBlocked.value)
        g.castConnected.value = false
        assertFalse(g.slice.localControlBlocked.value)
    }

    /**
     * The verdict graph: relaxed collaborator mocks with REAL StateFlows at
     * the three fold inputs (engine, cast connectivity, SyncPlay state).
     */
    private class VerdictGraph(
        availablePlayerTypes: List<PlayerType>,
    ) {
        val engineFlow = MutableStateFlow<MediaEngine?>(null)
        val castConnected = MutableStateFlow(false)
        val syncPlayState = MutableStateFlow(SyncPlayUiState())

        private val sessionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        val slice = SheetsSessionSlice(
            scope = sessionScope,
            subtitles = mockk(relaxed = true),
            subtitleStyleController = mockk(relaxed = true),
            trackSelectionHelper = mockk(relaxed = true),
            videoQueue = VideoQueueController(),
            effects = mockk(relaxed = true),
            syncPlay = mockk<SyncPlayBridge>(relaxed = true) {
                every { state } returns this@VerdictGraph.syncPlayState
            },
            sleepTimer = mockk(relaxed = true),
            renderControls = mockk<RenderControls>(relaxed = true),
            sessionRender = mockk<SessionRenderState>(relaxed = true),
            playerSessionManager = mockk<PlayerSessionManager>(relaxed = true) {
                every { engineFlow } returns this@VerdictGraph.engineFlow
            },
            cast = mockk<PlayerCastController>(relaxed = true) {
                every { isConnectedFlow } returns this@VerdictGraph.castConnected
            },
            uiCommands = mockk<PlayerUiCommands>(relaxed = true),
            subtitlePreferences = MutableStateFlow(SubtitleSlice()),
            availablePlayerTypes = availablePlayerTypes,
        )
    }
}
