package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared stats change-guard both engines' `updateVideoStats` funnels
 * ride: scalar-equality suppression, any-change admit, the forced full
 * re-read cadence (at the boundary, on a fake clock), the published-shape
 * comparison semantics (the guard compares exactly the values it is handed —
 * the Exo HDR fold happens caller-side), and the per-item reset. See
 * [StatsPublishGuard]'s KDoc for why the cadence ticks on admit and why
 * sample-to-sample equality stands in for the former last-published-field
 * comparisons.
 */
class StatsPublishGuardTest {

    /** The Exo-shaped seven-scalar sample (buffered, bandwidth, counters, folded HDR trio). */
    private fun exoSample(
        buffered: Long = 5_000L,
        bandwidth: Long = 1_000_000L,
        dropped: Long = 0L,
        rendered: Long = 100L,
        hdrActive: Boolean = false,
        hdrNotice: String? = null,
        displayHdrTypes: String? = null,
    ): Array<out Any?> = arrayOf<Any?>(buffered, bandwidth, dropped, rendered, hdrActive, hdrNotice, displayHdrTypes)

    @Test
    fun firstTick_alwaysAdmits() {
        val guard = StatsPublishGuard(fullRereadCadenceMs = null, nowMs = { 0L })
        // No previous sample — nothing to compare, the full read must run
        // (the former `last == null` arm of both engines' guards).
        assertTrue(guard.admit(*exoSample()))
    }

    @Test
    fun unchangedScalars_noAdmit() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = null, nowMs = { now })
        assertTrue(guard.admit(*exoSample()))
        now += 500
        // Every sampled scalar frozen → the expensive full read is skipped.
        assertFalse(guard.admit(*exoSample()))
    }

    @Test
    fun anyScalarChange_admits() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = null, nowMs = { now })
        assertTrue(guard.admit(*exoSample()))
        // Each sampled field alone must be admit-worthy — including the
        // nullable ones (null → value and back), i.e. equality-with-null is
        // a real comparison, not "always equal".
        val flips = listOf<Array<out Any?>>(
            exoSample(buffered = 5_400L),
            exoSample(bandwidth = 1_200_000L),
            exoSample(dropped = 1L),
            exoSample(rendered = 101L),
            exoSample(hdrActive = true),
            exoSample(hdrNotice = "tone mapping"),
            exoSample(displayHdrTypes = "HDR10"),
        )
        for (flipped in flips) {
            now += 500
            assertTrue(guard.admit(*flipped), "a moved scalar must admit: ${flipped.contentToString()}")
            now += 500
            // …and the moved value itself becomes the frozen baseline.
            assertFalse(guard.admit(*flipped), "the same sample must not re-admit")
        }
    }

    @Test
    fun cadence_forcesFullReread_atTheBoundary_evenWhenFrozen() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = 2_000L, nowMs = { now })
        assertTrue(guard.admit(*exoSample())) // t=0: first admit sets the clock
        now = 1_999L
        assertFalse(guard.admit(*exoSample()), "just under the cadence: still suppressed")
        now = 2_000L
        assertTrue(guard.admit(*exoSample()), "at the boundary the full re-read is forced")
        now = 3_999L
        assertFalse(guard.admit(*exoSample()), "the forced admit re-armed the clock")
        now = 4_000L
        assertTrue(guard.admit(*exoSample()))
    }

    @Test
    fun noCadence_neverForces() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = null, nowMs = { now })
        assertTrue(guard.admit(*exoSample()))
        now += 86_400_000L
        // The Exo shape: hours of frozen scalars still skip the full read —
        // only a moved scalar admits.
        assertFalse(guard.admit(*exoSample()))
    }

    @Test
    fun publishedShape_comparison_guardSeesOnlyWhatItIsGiven() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = null, nowMs = { now })
        // The Exo case, end to end: the raw passthrough DECISION is "active"
        // on both ticks, but the observed-SDR fold downgrades the PUBLISHED
        // value to false on both — the guard must suppress, because what it
        // is handed (the published shape) did not move. Keying on the raw
        // decision instead would spuriously admit; keying on the pre-fold
        // expectation is exactly the bug the fold-before-guard ordering
        // exists to prevent.
        val downgraded = exoSample(hdrActive = false, hdrNotice = null)
        assertTrue(guard.admit(*downgraded))
        now += 500
        assertFalse(guard.admit(*downgraded))
        now += 500
        // The downgrade LIFTING (target flips HDR) moves the published value
        // while the frame counters stay frozen — must admit.
        assertTrue(guard.admit(*exoSample(hdrActive = true)))
    }

    @Test
    fun reset_clearsSampleAndCadenceClock() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = 2_000L, nowMs = { now })
        assertTrue(guard.admit(*exoSample()))
        assertFalse(guard.admit(*exoSample()))
        // Item switch: the previous item's sample must not suppress the new
        // item's first tick, and the cadence clock re-arms from that fresh
        // admit (the stale pre-reset clock is gone).
        guard.reset()
        now = 1L
        assertTrue(guard.admit(*exoSample()), "post-reset first tick admits")
        now = 2_000L
        assertFalse(guard.admit(*exoSample()), "cadence re-armed from the post-reset admit (t=1)")
        now = 2_001L
        assertTrue(guard.admit(*exoSample()), "full cadence since the post-reset admit forces the re-read")
    }

    @Test
    fun mpvShaped_fiveScalarSample_frozenBitratesAndCounters_suppress() {
        var now = 0L
        val guard = StatsPublishGuard(fullRereadCadenceMs = 2_000L, nowMs = { now })
        // The mpv host's scalar-only sample (bitrates nullable — 0 read
        // normalizes to null); the HDR fold rides the cadence arm instead.
        val mpvSample = arrayOf<Any?>(640_000, null, 5_000L, 0L, 42L)
        assertTrue(guard.admit(*mpvSample))
        now += 1_000
        assertFalse(guard.admit(*mpvSample))
        // Array covariance: identical values as a fresh array must compare
        // equal element-wise (the guard compares values, not identity).
        now += 500
        assertFalse(guard.admit(640_000, null, 5_000L, 0L, 42L))
        now += 500
        assertTrue(guard.admit(*mpvSample), "cadence boundary forces the full re-read")
    }
}
