package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStallRecoveryAction
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStallWatchdog
import com.raulshma.jellyplay.feature.player.video.engine.mpv.isAimagereaderFailureLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins for the stall watchdog ([MpvStallWatchdog]) — the "position frozen
 * while claiming to play" detector behind the mpv wedged-session rescues
 * (the zero-copy hwdec AImageReader wedge and the spdif-fallback play-loop
 * wedge, both observed on the Adreno 509 repro device). The attempt ladder,
 * the state gates and the reset semantics are load-bearing: a wrong fire
 * turns into surprise seeks mid-playback, a missed fire turns back into the
 * eternal black screen.
 *
 * Tick cadence here: 1s ticks (the production ticker's default), 8s confirm
 * window. The first tick of a session always reads as "progress" (the -1
 * seed), so a session that is wedged from birth opens its window at t=1000
 * and fires at t=9000; each further attempt opens the next window at the
 * next tick (t=10000 → fires at t=18000).
 */
class MpvStallWatchdogTest {

    /** Runs frozen-while-playing ticks across [fromMs]..[toMs], returning the LAST action. */
    private fun frozen(
        watchdog: MpvStallWatchdog,
        fromMs: Long,
        toMs: Long,
        positionMs: Long = 0L,
        aimagereader: Boolean = false,
        playbackState: EnginePlaybackState = EnginePlaybackState.READY,
        isPlaying: Boolean = true,
    ): MpvStallRecoveryAction? {
        var last: MpvStallRecoveryAction? = null
        for (t in fromMs..toMs step 1_000) {
            last = watchdog.onTick(t, playbackState, isPlaying, positionMs, aimagereader)
        }
        return last
    }

    // ── healthy session ─────────────────────────────────────────────────────

    @Test
    fun playingSessionWithProgress_neverFires() {
        val watchdog = MpvStallWatchdog()
        for (i in 0..30) {
            assertNull(
                watchdog.onTick(
                    nowMs = i * 1_000L,
                    playbackState = EnginePlaybackState.READY,
                    isPlaying = true,
                    positionMs = i * 1_000L,
                    aimagereaderFailureObserved = false,
                ),
                "tick $i",
            )
        }
    }

    // ── stall ladder ────────────────────────────────────────────────────────

    @Test
    fun stalledWhilePlaying_firesKickAfterConfirmWindow_atFrozenPositionPlusSlack() {
        val watchdog = MpvStallWatchdog()
        // t=1000 reads as progress (the -1 seed); the window opens at 2000.
        // Nothing may fire inside the confirm window (2000..9000).
        assertNull(frozen(watchdog, fromMs = 1_000L, toMs = 9_000L))
        val action = watchdog.onTick(10_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = false)
        assertEquals(MpvStallRecoveryAction.Kick(kickToMs = MpvStallWatchdog.KICK_FORWARD_MS), action)
    }

    @Test
    fun stalledWithAimagereaderEvidence_firstAttemptSwitchesHwdecThenKicks() {
        val watchdog = MpvStallWatchdog()
        assertNull(frozen(watchdog, fromMs = 1_000L, toMs = 9_000L, aimagereader = true))
        val action = watchdog.onTick(10_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = true)
        assertEquals(
            MpvStallRecoveryAction.SwitchHwdecThenKick(hwdecValue = MpvStallWatchdog.HWDEC_STALL_FALLBACK, kickToMs = MpvStallWatchdog.KICK_FORWARD_MS),
            action,
        )
    }

    @Test
    fun secondAttempt_needsAnotherFullWindow_andSwitchesHwdecUnconditionally() {
        val watchdog = MpvStallWatchdog()
        // Attempt 1 (no aimagereader evidence) — the plain kick, at t=10000
        // (the -1 seed makes t=1000 a progress tick, so the window opens at
        // 2000 and fires at 2000+8000).
        assertNull(frozen(watchdog, 1_000L, 9_000L))
        assertEquals(
            MpvStallRecoveryAction.Kick(kickToMs = MpvStallWatchdog.KICK_FORWARD_MS),
            watchdog.onTick(10_000L, EnginePlaybackState.READY, true, 0L, false),
        )
        // The kick did not move the session — the next window (re-anchored at
        // the fire) must elapse before attempt 2 (never back-to-back):
        // 11000..17000 silent.
        assertNull(frozen(watchdog, 11_000L, 17_000L))
        // Attempt 2 at t=18000: the switch+kick even though the aimagereader
        // signature was never observed (covers direct-path stalls whose log
        // lines were missed).
        assertEquals(
            MpvStallRecoveryAction.SwitchHwdecThenKick(hwdecValue = MpvStallWatchdog.HWDEC_STALL_FALLBACK, kickToMs = MpvStallWatchdog.KICK_FORWARD_MS),
            watchdog.onTick(18_000L, EnginePlaybackState.READY, true, 0L, false),
        )
    }

    @Test
    fun attemptBudgetExhausted_watchdogStandsDown() {
        val watchdog = MpvStallWatchdog()
        // Burn both attempts while frozen (fires at 10s and 18s).
        assertNull(frozen(watchdog, 1_000L, 9_000L))
        watchdog.onTick(10_000L, EnginePlaybackState.READY, true, 0L, false)
        assertNull(frozen(watchdog, 11_000L, 17_000L))
        watchdog.onTick(18_000L, EnginePlaybackState.READY, true, 0L, false)
        // Third window — budget gone, permanently silent (the shipped
        // failure mode returns rather than an endless seek loop).
        for (t in 19_000L..40_000L step 1_000) {
            assertNull(
                watchdog.onTick(t, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = false),
                "t=$t",
            )
        }
    }

    // ── gates + resets ──────────────────────────────────────────────────────

    @Test
    fun pausedSession_resetsStallWindow() {
        val watchdog = MpvStallWatchdog()
        // 6s of frozen-while-playing, then the user pauses.
        assertNull(frozen(watchdog, 1_000L, 6_000L))
        watchdog.onTick(7_000L, EnginePlaybackState.READY, isPlaying = false, positionMs = 0L, aimagereaderFailureObserved = false)
        // Resume: the window restarts from zero — 7s later still nothing.
        assertNull(frozen(watchdog, 8_000L, 15_000L))
        // ... and the fire lands exactly one confirm window after the resume.
        val action = watchdog.onTick(16_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = false)
        assertEquals(MpvStallRecoveryAction.Kick(kickToMs = MpvStallWatchdog.KICK_FORWARD_MS), action)
    }

    @Test
    fun bufferingState_doesNotCountTowardTheStallWindow() {
        val watchdog = MpvStallWatchdog()
        assertNull(frozen(watchdog, 1_000L, 6_000L))
        // A buffer stall parks the fold in BUFFERING — excluded by the gate.
        assertNull(frozen(watchdog, 7_000L, 14_000L, playbackState = EnginePlaybackState.BUFFERING))
        // Back to READY: the window starts fresh.
        assertNull(frozen(watchdog, 15_000L, 22_000L))
        val action = watchdog.onTick(23_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = false)
        assertEquals(MpvStallRecoveryAction.Kick(kickToMs = MpvStallWatchdog.KICK_FORWARD_MS), action)
    }

    @Test
    fun progress_resetsWindowAndAttemptBudget() {
        val watchdog = MpvStallWatchdog()
        // Burn the whole budget while frozen at 0 (fires at 10s and 18s).
        assertNull(frozen(watchdog, 1_000L, 9_000L))
        watchdog.onTick(10_000L, EnginePlaybackState.READY, true, 0L, false)
        assertNull(frozen(watchdog, 11_000L, 17_000L))
        watchdog.onTick(18_000L, EnginePlaybackState.READY, true, 0L, false)
        // The kick lands (or the user seeks) — position moves to 4.2s.
        watchdog.onTick(19_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 4_200L, aimagereaderFailureObserved = false)
        // A NEW stall (now frozen at 4.2s) gets a fresh budget and fires at
        // the new position (window 20000..27000 silent, fire at 28000).
        assertNull(frozen(watchdog, 20_000L, 27_000L, positionMs = 4_200L))
        val action = watchdog.onTick(28_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 4_200L, aimagereaderFailureObserved = false)
        assertEquals(MpvStallRecoveryAction.Kick(kickToMs = 4_200L + MpvStallWatchdog.KICK_FORWARD_MS), action)
    }

    @Test
    fun reset_clearsEverything() {
        val watchdog = MpvStallWatchdog()
        assertNull(frozen(watchdog, 1_000L, 9_000L))
        watchdog.onTick(10_000L, EnginePlaybackState.READY, true, 0L, false)
        watchdog.reset()
        // Post-reset the seed is gone (first tick = progress) and the window
        // + budget start fresh — the next fire needs a full new window
        // (first tick 20000 re-seeds, window 21000..28000, fire at 29000).
        assertNull(frozen(watchdog, 20_000L, 28_000L))
        val action = watchdog.onTick(29_000L, EnginePlaybackState.READY, isPlaying = true, positionMs = 0L, aimagereaderFailureObserved = false)
        assertEquals(MpvStallRecoveryAction.Kick(kickToMs = MpvStallWatchdog.KICK_FORWARD_MS), action)
    }

    // ── the log detector ────────────────────────────────────────────────────

    @Test
    fun aimagereaderDetector_matchesTheWedgedReproSignatures() {
        assertTrue(
            isAimagereaderFailureLog("vo/gpu-next/aimagereader", "acquireLatestImage failed: -30001"),
        )
        assertTrue(
            isAimagereaderFailureLog("vo/gpu-next/aimagereader", "Waiting for frame timed out!"),
        )
    }

    @Test
    fun aimagereaderDetector_ignoresUnrelatedLogs() {
        // Same text under a different prefix — not the zero-copy VO path.
        assertFalse(isAimagereaderFailureLog("cplayer", "acquireLatestImage failed: -30001"))
        // The prefix without a failure signature.
        assertFalse(isAimagereaderFailureLog("vo/gpu-next/aimagereader", "created reader"))
        // "Waiting for ..." from another component.
        assertFalse(isAimagereaderFailureLog("demux", "Waiting for scripts..."))
    }
}
