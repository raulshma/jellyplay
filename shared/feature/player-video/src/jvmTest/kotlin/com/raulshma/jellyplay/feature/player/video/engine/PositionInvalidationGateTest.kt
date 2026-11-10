package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [PositionInvalidationGate] — the throttle behind the media-session
 * adapter's position-only `invalidateState()`:
 *
 *  - the first emission always admits (initial position stamp);
 *  - smooth-playhead emissions admit at most once per the configured interval,
 *    at the boundary, on a fake clock (the StatsPublishGuard boundary convention);
 *  - an emission that jumped ≥ the threshold from the last ADMITTED position
 *    admits immediately, forward or backward (seek / correction);
 *  - the interval clock re-arms on every admit, jump admits included;
 *  - the speed-blind jump threshold is pinned as a deliberate trade-off: at
 *    2x playback the ~1 Hz cadence still holds, at 4x the jump arm admits
 *    every other tick, and past ~8x the gate matches the historical
 *    invalidate-per-emission rate — never below it;
 *  - the hidden-controls heartbeat (1 Hz emissions) admits every tick, so
 *    backgrounded sessions keep advancing at ~1 Hz — the rate the gate targets,
 *    not a regression of it.
 *
 * All timing runs on a hand-advanced fake clock (monotonic, in ms), mirroring
 * the production clock (`SystemClock.elapsedRealtime`).
 */
class PositionInvalidationGateTest {

    /** The production shape: ~1 Hz smooth-playhead cadence, 2 s seek threshold. */
    private fun gate(now: () -> Long): PositionInvalidationGate = PositionInvalidationGate(
        minIntervalMs = 1_000L,
        jumpThresholdMs = 2_000L,
        nowMs = now,
    )

    @Test
    fun firstEmission_alwaysAdmits() {
        val g = gate { 0L }
        // Nothing to compare against — the session must stamp an initial
        // position rather than render a blank playhead until the first
        // interval elapses.
        assertTrue(g.admit(42_000L))
    }

    @Test
    fun steadyPlayback_admitsAtMostOncePerInterval_atTheBoundary() {
        var now = 0L
        val g = gate { now }
        var pos = 0L
        assertTrue(g.admit(pos), "first emission admits")
        // Visible-controls cadence: 250 ms emissions, steady 1x playhead.
        // Admits land exactly at the interval boundary; every emission inside
        // the window is suppressed.
        val expected = mutableListOf(true)
        for (t in 1..16) {
            now += 250L
            pos += 250L
            val due = t % 4 == 0
            expected.add(due)
            assertEquals(due, g.admit(pos), "t=$now: admit iff a full interval has elapsed")
        }
        // The pinned schedule: admits at t=0, 1000, 2000, 3000, 4000 — 1 Hz
        // out of a 4 Hz emission stream.
        assertTrue(expected.count { it } == 5, "admits at 1 Hz, got ${expected.count { it }}")
    }

    @Test
    fun intervalBoundary_admitsExactlyAtMinInterval_notJustBelow() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        now = 999L
        assertFalse(g.admit(999L), "just under the interval: suppressed")
        now = 1_000L
        assertTrue(g.admit(1_000L), "at the boundary the smooth-playhead emission admits")
        now = 1_250L
        assertFalse(g.admit(1_250L), "the boundary admit re-armed the clock")
    }

    @Test
    fun positionJump_admitsImmediately_insideTheInterval() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        now += 250L
        // Forward seek deep inside the suppressed window: the notification must
        // reflect the new playhead now, not when the 1 Hz window next opens.
        assertTrue(g.admit(30_000L), "forward jump admits immediately")
        now += 250L
        // Backward seeks are equally discontinuities — replay / repeat / A-B
        // jumps; the absolute delta admits them.
        assertTrue(g.admit(4_000L), "backward jump admits immediately")
    }

    @Test
    fun jumpThresholdBoundary_admitsAtExactlyTheThreshold_notJustBelow() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        now += 250L
        assertFalse(g.admit(1_999L), "just under the threshold: a smooth playhead, suppressed")
        now += 250L
        assertTrue(g.admit(2_000L), "exactly the threshold: a discontinuity, admits")
    }

    @Test
    fun jumpAdmit_rearmsTheIntervalClock() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        now += 250L
        assertTrue(g.admit(30_000L), "seek admits instantly")
        // Normal playback resumes FROM the seek: the next small steps are
        // suppressed until a full interval has elapsed since the jump admit,
        // then the ~1 Hz cadence picks up again.
        now += 250L
        assertFalse(g.admit(30_250L), "post-seek smooth step inside the interval: suppressed")
        now += 750L
        assertTrue(g.admit(31_000L), "a full interval since the jump admit: cadence resumes")
    }

    @Test
    fun jumpIsMeasuredAgainstTheLastAdmittedPosition_notThePreviousEmission() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        // Three suppressed 700 ms steps (2.1 s of playhead) land inside one
        // interval... they stay suppressed (interval not due, each step small).
        now += 250L
        assertFalse(g.admit(700L))
        now += 250L
        assertFalse(g.admit(1_400L))
        // A seek measured from the last SUPPRESSED emission would read this as
        // a 700 ms nudge; measured from the last ADMITTED position it is
        // exactly the 2 s discontinuity it is — the jump rule keys on what the
        // session actually rendered, not on what it suppressed.
        now += 250L
        assertTrue(g.admit(2_100L), "2.1 s past the last admit is a jump even though the previous emission was close")
    }

    @Test
    fun highSpeedAdvance_admitsOnTheJumpArm_betweenTheCadenceAndTheHistoricalRate() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        // 4x playback: 1000 ms of playhead per 250 ms emission. The speed-blind
        // threshold admits when the CUMULATIVE advance from the last admitted
        // position clears 2 s — every other tick at 4x (2 Hz), between the 1 Hz
        // cadence at common speeds and the historical 4 Hz
        // invalidate-per-emission ceiling. Pinned on purpose: the throttle must
        // never invalidate LESS than the old behavior, and playhead
        // extrapolation is exactly what misleads at high speed, so erring
        // toward MORE invalidations there is the safe direction.
        now += 250L
        assertFalse(g.admit(1_000L), "1000 ms advance: under the threshold and inside the interval")
        now += 250L
        assertTrue(g.admit(2_000L), "cumulative 2000 ms advance from the last admit: the jump arm fires")
        now += 250L
        assertFalse(g.admit(3_000L), "the jump admit re-armed both arms")
        now += 250L
        assertTrue(g.admit(4_000L))
    }

    @Test
    fun veryHighSpeed_admitsEveryEmission_matchingTheHistoricalRate() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(0L))
        // 8x playback: 2000 ms of playhead per 250 ms emission — every
        // emission clears the threshold, so the gate admits at the full
        // historical rate. This is the degenerate ceiling: beyond it the gate
        // cannot fall back to anything MORE eager than invalidate-per-emission,
        // i.e. throttling never costs correctness at extreme speeds.
        repeat(3) { t ->
            now += 250L
            assertTrue(g.admit((t + 1) * 2_000L), "each 8x emission clears the threshold and admits")
        }
    }

    @Test
    fun hiddenControlsHeartbeat_oneHertzEmissions_admitEveryTime() {
        var now = 0L
        val g = gate { now }
        var pos = 0L
        assertTrue(g.admit(pos))
        // Controls hidden: the ticker itself slows to 1 Hz. Every emission is a
        // full interval apart, so all of them admit — the gate targets ~1 Hz
        // invalidations and must not push a backgrounded session's playhead
        // below the rate the engine already emits at.
        repeat(5) {
            now += 1_000L
            pos += 1_000L
            assertTrue(g.admit(pos), "1 Hz heartbeat tick must admit")
        }
    }

    @Test
    fun pauseEdgeFinalTick_suppressedWhenInsideTheInterval_harmlessEitherWay() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(10_000L))
        // Pause 300 ms later: the ticker emits exactly one final tick with the
        // (barely advanced) position. Inside the interval it is suppressed —
        // harmless, because the isPlaying collector invalidated immediately at
        // the pause and that invalidation already stamped the live position.
        now += 300L
        assertFalse(g.admit(10_300L))
        // The same final tick arriving after a quiet stretch (paused for a
        // while, then the ticker's bounded re-check cycles emit) admits via the
        // interval arm — the session's playhead lands on the final position.
        now += 5_000L
        assertTrue(g.admit(10_300L), "interval elapsed: final position admitted")
    }

    @Test
    fun stalledPlayback_zeroAdvance_suppressedInsideInterval_thenAdmittedByCadence() {
        var now = 0L
        val g = gate { now }
        assertTrue(g.admit(5_000L))
        // Frozen playhead (stall/buffering with the ticker still firing): the
        // identical positions carry no new state, so the gate skips them until
        // the cadence arm opens — at which point the re-resolution is cheap and
        // correct (it re-reads live engine state), just never more eager than
        // ~1 Hz.
        now += 250L
        assertFalse(g.admit(5_000L))
        now += 250L
        assertFalse(g.admit(5_000L))
        now += 500L
        assertTrue(g.admit(5_000L), "cadence due: admit even with a frozen playhead")
    }
}
