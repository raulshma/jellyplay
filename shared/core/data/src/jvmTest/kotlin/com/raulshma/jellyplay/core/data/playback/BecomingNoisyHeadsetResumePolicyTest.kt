package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.playback.HeadsetResumePolicy.RESUME_WINDOW_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The resume-on-headset-insert policy pair: the becoming-noisy pause
 * marker's was-playing gate and the plug-in freshness decision. Pure — the
 * Android receivers supply the clock.
 */
class BecomingNoisyHeadsetResumePolicyTest {

    private val pauseAtMs = 1_000_000L

    // ── the pause marker's was-playing gate ──────────────────────────────────

    @Test
    fun `marker records only a pause that stopped playback`() {
        assertTrue(HeadsetResumePolicy.shouldMarkNoisyPause(wasPlaying = true))
        // Already-paused at broadcast time = the user's pause (or Exo's built-in
        // becoming-noisy handler won the race) — never ours, never marked.
        assertFalse(HeadsetResumePolicy.shouldMarkNoisyPause(wasPlaying = false))
    }

    // ── the plug-in resume decision ──────────────────────────────────────────

    @Test
    fun `resume requires the opt-in pref`() {
        // Fresh marker, pref off → no resume.
        assertFalse(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs + 1_000L,
                noisyPauseAtMs = pauseAtMs,
                prefEnabled = false,
            ),
        )
        assertTrue(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs + 1_000L,
                noisyPauseAtMs = pauseAtMs,
                prefEnabled = true,
            ),
        )
    }

    @Test
    fun `resume requires a marker`() {
        assertFalse(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs + 1_000L,
                noisyPauseAtMs = null,
                prefEnabled = true,
            ),
        )
    }

    @Test
    fun `resume inside the window`() {
        // Boundary: exactly the window's age still resumes.
        assertTrue(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs + RESUME_WINDOW_MS,
                noisyPauseAtMs = pauseAtMs,
                prefEnabled = true,
            ),
        )
        // One tick past the window decays.
        assertFalse(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs + RESUME_WINDOW_MS + 1L,
                noisyPauseAtMs = pauseAtMs,
                prefEnabled = true,
            ),
        )
    }

    @Test
    fun `window is five minutes`() {
        assertEquals(5L * 60_000L, RESUME_WINDOW_MS)
    }

    @Test
    fun `negative age never resumes`() {
        // Defensive: a clock reset (or a marker stamped after `nowMs`) is stale.
        assertFalse(
            HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = pauseAtMs - 1L,
                noisyPauseAtMs = pauseAtMs,
                prefEnabled = true,
            ),
        )
    }
}
