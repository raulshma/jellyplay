package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pins [NowPlayingVerdict] — the ONE fold over the video-engine /
 * audio-manager fork (GLOSSARY "Now-playing verdict") — in all four family
 * combinations plus the remote-session face:
 *
 *  - VIDEO ONLY: the bound engine owns the progress face and the paused
 *    cadence REGARDLESS of its playing state (engine-priority, not
 *    either-or); idle is false while any engine stays bound;
 *  - AUDIO ONLY: the progress face answers only over a LOADED session
 *    (engine + claimed item), in ticks ×10 000; the idle face rides the
 *    tracker's item id, not the session pull;
 *  - BOTH: the engine wins every forked face even when it is paused while
 *    audio plays;
 *  - NEITHER: null progress face, idle true, every fold false;
 *  - the isIdle-vs-hasActiveSession split is real: a bound-but-idle engine
 *    and the play()'s claim-before-publish window (session pull true,
 *    tracker item still null) separate the two formulations BY DESIGN;
 *  - the remote-session cell starts off and flips only through the
 *    single-writer mark.
 */
class NowPlayingVerdictTest {

    private val videoEngines = ActivePlayerController()
    private val audioFace = FakeAudioFace()

    @BeforeTest
    fun setUp() {
        audioFace.reset()
    }

    private fun verdict() = NowPlayingVerdict(videoEngines, audioFace)

    private fun bindEngine(playing: Boolean): RemotePlayableEngine {
        val engine = mockk<RemotePlayableEngine>(relaxed = true)
        every { engine.currentPositionMs } returns 1_234L
        every { engine.isPlaying } returns MutableStateFlow(playing)
        videoEngines.bindEngine(engine)
        return engine
    }

    // ── video only ────────────────────────────────────────────────────────

    @Test
    fun `a bound engine owns the progress face in ms-ticks and paused state`() {
        bindEngine(playing = true)
        audioFace.hasActiveSession = true
        audioFace.currentPosition.value = 9_999L

        val verdict = verdict()

        assertEquals(12_340_000L to false, verdict.progressTicksAndPaused())
        assertFalse(verdict.isPausedNow())
        assertTrue(verdict.isPlaying)
        assertTrue(verdict.hasActiveSession)
        assertFalse(verdict.isIdle)
    }

    @Test
    fun `a bound paused engine keeps owning the faces even with audio idle`() {
        bindEngine(playing = false)

        val verdict = verdict()

        assertEquals(12_340_000L to true, verdict.progressTicksAndPaused())
        assertTrue(verdict.isPausedNow())
        assertFalse(verdict.isPlaying)
        assertTrue(verdict.hasActiveSession)
        assertFalse(verdict.isIdle)
    }

    @Test
    fun `idle stays false while a stopped engine stays bound`() {
        bindEngine(playing = false)
        // No audio session at all.
        val verdict = verdict()
        assertFalse(verdict.isIdle)
        assertTrue(verdict.hasActiveSession)
        // The ENGINE leg still answers — the face is engine-priority, the
        // audio session gate only decides the no-engine leg.
        assertEquals(12_340_000L to true, verdict.progressTicksAndPaused())
    }

    // ── audio only ────────────────────────────────────────────────────────

    @Test
    fun `audio answers the progress face only over a loaded session`() {
        audioFace.hasActiveSession = true
        audioFace.isPlaying.value = true
        audioFace.currentPosition.value = 2_500L

        val verdict = verdict()

        assertEquals(25_000_000L to false, verdict.progressTicksAndPaused())
        assertFalse(verdict.isPausedNow())
        assertTrue(verdict.isPlaying)
        assertTrue(verdict.hasActiveSession)
    }

    @Test
    fun `audio without a loaded session answers null`() {
        audioFace.hasActiveSession = false
        audioFace.isPlaying.value = true
        audioFace.currentPosition.value = 2_500L

        val verdict = verdict()

        assertNull(verdict.progressTicksAndPaused())
        // The paused cadence still reads the audio engine — the reporter's
        // delay decider never consulted the session gate.
        assertFalse(verdict.isPausedNow())
        assertTrue(verdict.isPlaying)
        assertFalse(verdict.hasActiveSession)
    }

    @Test
    fun `audio idle rides the tracker item id`() {
        val verdict = verdict()
        assertTrue(verdict.isIdle)
        audioFace.currentPlayingItemId.value = "song-1"
        assertFalse(verdict.isIdle)
    }

    // ── both families ─────────────────────────────────────────────────────

    @Test
    fun `the engine wins the forks even while paused against playing audio`() {
        bindEngine(playing = false)
        audioFace.hasActiveSession = true
        audioFace.isPlaying.value = true
        audioFace.currentPosition.value = 2_500L

        val verdict = verdict()

        // Progress face + paused cadence: engine-priority (video paused).
        assertEquals(12_340_000L to true, verdict.progressTicksAndPaused())
        assertTrue(verdict.isPausedNow())
        // The OR-folds hear BOTH families.
        assertTrue(verdict.isPlaying)
        assertTrue(verdict.hasActiveSession)
        assertFalse(verdict.isIdle)
    }

    // ── neither family ────────────────────────────────────────────────────

    @Test
    fun `nothing anywhere answers idle-true and null-progress`() {
        val verdict = verdict()
        assertFalse(verdict.isPlaying)
        assertFalse(verdict.hasActiveSession)
        assertTrue(verdict.isIdle)
        assertNull(verdict.progressTicksAndPaused())
        assertTrue(verdict.isPausedNow())
    }

    // ── the two idle formulations genuinely split ─────────────────────────

    @Test
    fun `the claim-before-publish window splits isIdle from hasActiveSession`() {
        // The play() window: the chassis cell is claimed (the manager's
        // session pull answers true) while the tracker flow has not
        // published yet — the DisplayContent gate must stay OPEN while the
        // session fold already answers loaded.
        audioFace.hasActiveSession = true
        val verdict = verdict()
        assertTrue(verdict.isIdle)
        assertTrue(verdict.hasActiveSession)
    }

    // ── remote-session face ───────────────────────────────────────────────

    @Test
    fun `the remote-session cell starts off and flips through the single-writer mark`() {
        val verdict = verdict()
        assertFalse(verdict.remoteSessionActive.value)

        verdict.markRemoteSession(true)
        assertTrue(verdict.remoteSessionActive.value)

        verdict.markRemoteSession(false)
        assertFalse(verdict.remoteSessionActive.value)
    }

    /** Mutable stand-in for the audio family's read face. */
    private class FakeAudioFace : AudioNowPlayingFace {
        override val isPlaying = MutableStateFlow(false)
        override val currentPosition = MutableStateFlow(0L)
        override val currentPlayingItemId = MutableStateFlow<String?>(null)
        override val title = MutableStateFlow("")
        override var hasActiveSession = false

        fun reset() {
            isPlaying.value = false
            currentPosition.value = 0L
            currentPlayingItemId.value = null
            title.value = ""
            hasActiveSession = false
        }
    }
}
