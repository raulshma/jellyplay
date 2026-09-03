package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the default/initial shape of [LiveTvPlayerUiState] and its derived
 * zap-navigation flags. The VM constructs this state before its channel-load
 * coroutine runs, so the defaults ARE the first frame the player chrome
 * renders: channels must read as loading, the engine as idle, and no error
 * may be showing.
 */
class LiveTvPlayerUiStateTest {

    private fun channel(id: String) = LiveTvChannel(id = id, name = "Channel $id")

    // ── defaults (the pre-load first frame) ────────────────────────────

    @Test
    fun `channels start loading by default`() {
        assertTrue(LiveTvPlayerUiState().isLoadingChannels)
    }

    @Test
    fun `engine starts IDLE`() {
        assertEquals(LiveEngineState.IDLE, LiveTvPlayerUiState().engineState)
    }

    @Test
    fun `no error is showing by default`() {
        val state = LiveTvPlayerUiState()
        assertNull(state.errorMessage)
        assertNull(state.errorDetail)
    }

    @Test
    fun `channel slots start empty`() {
        val state = LiveTvPlayerUiState()
        assertTrue(state.channels.isEmpty())
        assertEquals(0, state.currentIndex)
        assertNull(state.currentChannel)
        assertNull(state.currentProgram)
        assertNull(state.nextProgram)
        assertNull(state.lastChannelId)
    }

    @Test
    fun `playback flags start at rest on the live edge`() {
        val state = LiveTvPlayerUiState()
        assertTrue(state.isBuffering, "a fresh player is buffering until the first frame")
        assertFalse(state.isPlaying)
        assertTrue(state.isAtLiveEdge, "live TV starts pinned to the live edge")
        assertFalse(state.isSwitchingChannel)
    }

    @Test
    fun `session defaults are unmuted auto with no resolved stream`() {
        val state = LiveTvPlayerUiState()
        assertFalse(state.isMuted)
        assertEquals(LiveStreamOption.AUTO, state.liveStreamOption)
        assertNull(state.playMethod, "no delivery method until the first stream resolves")
        assertTrue(state.transcodeReasons.isEmpty())
        assertTrue(state.favorites.isEmpty())
        assertEquals(5_000L, state.controlsTimeoutMs)
    }

    // ── derived zap flags ──────────────────────────────────────────────

    @Test
    fun `empty channel list has neither next nor previous`() {
        val state = LiveTvPlayerUiState()
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `first channel can zap next but not previous`() {
        val state = LiveTvPlayerUiState(channels = listOf(channel("a"), channel("b"), channel("c")))
        assertEquals(0, state.currentIndex)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    @Test
    fun `last channel can zap previous but not next`() {
        val state = LiveTvPlayerUiState(
            channels = listOf(channel("a"), channel("b"), channel("c")),
            currentIndex = 2,
        )
        assertFalse(state.hasNext)
        assertTrue(state.hasPrevious)
    }

    @Test
    fun `single channel can zap neither way`() {
        val state = LiveTvPlayerUiState(channels = listOf(channel("only")))
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
    }

    // ── error travels as one unresolved unit ───────────────────────────

    @Test
    fun `error message and detail travel independently of loading flags`() {
        val failed = LiveTvPlayerUiState().copy(
            isLoadingChannels = false,
            errorMessage = LivePlayerMessage.Raw("tuner offline"),
            errorDetail = "HttpException 502 at /LiveChannels",
        )
        assertTrue(failed.errorMessage is LivePlayerMessage.Raw)
        assertEquals("tuner offline", (failed.errorMessage as LivePlayerMessage.Raw).text)
        assertEquals("HttpException 502 at /LiveChannels", failed.errorDetail)
        assertFalse(failed.isLoadingChannels)
        assertEquals(LiveEngineState.IDLE, failed.engineState, "copying an error must not touch the engine field")

        val detailOnly = failed.copy(errorMessage = null)
        assertNull(detailOnly.errorMessage)
        assertEquals("HttpException 502 at /LiveChannels", detailOnly.errorDetail, "detail survives message clearing")
    }
}
