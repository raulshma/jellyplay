package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the skip-on-forward-seek clamp ladder ([SegmentSeekClamp.kt])
 * — the guard rails that keep a user-initiated seek from dropping into the
 * middle of an AUTO_SKIP segment, and equally keep every other seek exactly
 * where it was aimed: backward never clamps, SHOW_BUTTON/IGNORE segments
 * never clamp, a segment ending inside the last 1% of the duration never
 * clamps (the up-next machinery owns that window), and the disabled setting
 * is a full pass-through.
 */
class SegmentSeekClampTest {

    // ms → ticks helper. 1 ms == 10_000 ticks (the player's convention).
    private fun ticks(ms: Long) = ms * 10_000

    private fun segment(
        type: MediaSegmentType,
        startMs: Long,
        endMs: Long,
        id: String = "seg-$type-$startMs",
    ) = MediaSegment(
        id = id,
        itemId = "",
        type = type,
        startTicks = ticks(startMs),
        endTicks = ticks(endMs),
    )

    private val defaults = SegmentBehavior.DEFAULT_BEHAVIORS

    private fun clamp(
        targetMs: Long,
        currentPositionMs: Long = 0L,
        segments: List<MediaSegment>,
        durationMs: Long,
        enabled: Boolean = true,
        behaviors: Map<MediaSegmentType, SegmentBehavior> = defaults,
    ): Long? = resolveForwardSeekSegmentClamp(
        targetMs = targetMs,
        currentPositionMs = currentPositionMs,
        segments = segments,
        segmentBehaviors = behaviors,
        durationMs = durationMs,
        enabled = enabled,
    )?.adjustedTargetMs

    // ── The headline: forward into AUTO_SKIP clamps to the segment end ────

    @Test
    fun forwardIntoAutoSkip_clampsToSegmentEnd() {
        // COMMERCIAL → AUTO_SKIP in DEFAULT_BEHAVIORS. A 10 s step landing
        // mid-commercial comes out at the commercial's end.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertEquals(
            35_000L,
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun clampTruncatesSubMillisecondTicks() {
        // endTicks / 10_000 truncates, like segmentEndSeekTarget.
        val commercial = MediaSegment(
            id = "c",
            itemId = "",
            type = MediaSegmentType.COMMERCIAL,
            startTicks = ticks(20_000),
            endTicks = ticks(35_000) + 5_000, // +0.5 ms of sub-tick remainder
        )
        assertEquals(
            35_000L,
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun seekToExactSegmentStart_doesNotClamp() {
        // "Strictly inside" is the plan's rule: startMs itself is the first
        // contained position of the half-open interval, but a seek aimed at
        // it passes through unchanged — only targets past the start clamp.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 20_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun seekToExactSegmentEnd_andBeyond_doNotClamp() {
        // Half-open interval, matching SegmentCalculator.containsPos(): the
        // end position is already outside the segment.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 35_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
        assertNull(
            clamp(
                targetMs = 40_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
    }

    // ── Backward seeks never clamp ─────────────────────────────────────────

    @Test
    fun backwardSeek_neverClamps() {
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        // Same-type target, but the position is already past it: backward.
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 40_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
        // The degenerate "seek to where we already are" is not forward either.
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 30_000,
                segments = listOf(commercial),
                durationMs = 600_000,
            ),
        )
    }

    // ── Only AUTO_SKIP-effective segments clamp ────────────────────────────

    @Test
    fun showButtonSegments_neverClamp() {
        val intro = segment(MediaSegmentType.INTRO, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(intro),
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun ignoreSegments_neverClamp() {
        val recap = segment(MediaSegmentType.RECAP, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(recap),
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun autoSkipOverride_makesShowButtonDefaultType_clamp() {
        // The EFFECTIVE behavior wins over the type's default: a user who set
        // INTRO → AUTO_SKIP gets the clamp on intros too.
        val intro = segment(MediaSegmentType.INTRO, startMs = 20_000, endMs = 35_000)
        val behaviors = defaults + (MediaSegmentType.INTRO to SegmentBehavior.AUTO_SKIP)
        assertEquals(
            35_000L,
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(intro),
                durationMs = 600_000,
                behaviors = behaviors,
            ),
        )
    }

    @Test
    fun autoSkipDemotedToIgnore_neverClamps() {
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        val behaviors = defaults + (MediaSegmentType.COMMERCIAL to SegmentBehavior.IGNORE)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
                behaviors = behaviors,
            ),
        )
    }

    @Test
    fun missingBehaviorEntry_meansIgnore_neverClamps() {
        val unknown = segment(MediaSegmentType.UNKNOWN, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(unknown),
                durationMs = 600_000,
                behaviors = emptyMap(),
            ),
        )
    }

    // ── The near-duration guard ─────────────────────────────────────────────

    @Test
    fun segmentEndingInLastOnePercent_neverClamps() {
        // Outro (AUTO_SKIP for this matrix) ending 0.05% before the item's
        // end: up-next / playback-ended owns that window, the clamp stands
        // down.
        val outro = segment(MediaSegmentType.OUTRO, startMs = 95_000, endMs = 99_950)
        val behaviors = defaults + (MediaSegmentType.OUTRO to SegmentBehavior.AUTO_SKIP)
        assertNull(
            clamp(
                targetMs = 97_000,
                currentPositionMs = 50_000,
                segments = listOf(outro),
                durationMs = 100_000,
                behaviors = behaviors,
            ),
        )
    }

    @Test
    fun segmentEndingJustOutsideLastOnePercent_clamps() {
        // 2% remaining: outside the guard, the clamp applies.
        val outro = segment(MediaSegmentType.OUTRO, startMs = 90_000, endMs = 98_000)
        val behaviors = defaults + (MediaSegmentType.OUTRO to SegmentBehavior.AUTO_SKIP)
        assertEquals(
            98_000L,
            clamp(
                targetMs = 95_000,
                currentPositionMs = 50_000,
                segments = listOf(outro),
                durationMs = 100_000,
                behaviors = behaviors,
            ),
        )
    }

    @Test
    fun unknownDuration_neverClamps() {
        // Live streams / unresolved containers (duration 0): the guard cannot
        // fire, so the clamp stays off entirely.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 0L,
            ),
        )
    }

    @Test
    fun segmentEndingBeyondDuration_neverClamps() {
        // A malformed segment that claims to outlive the item is left alone.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 120_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 100_000,
            ),
        )
    }

    // ── The disabled flag ───────────────────────────────────────────────────

    @Test
    fun disabledSetting_isAFullPassThrough() {
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(commercial),
                durationMs = 600_000,
                enabled = false,
            ),
        )
    }

    // ── Overlap resolution ──────────────────────────────────────────────────

    @Test
    fun overlappingAutoSkipSegments_resolveInSegmentPriorityOrder() {
        // COMMERCIAL outranks INTRO in MediaSegmentType.SEGMENT_PRIORITY —
        // the calculator scans priority-first, so the clamp honors the same
        // owner of the target position (here: the commercial's end, not the
        // intro's).
        val intro = segment(MediaSegmentType.INTRO, startMs = 20_000, endMs = 40_000)
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        assertEquals(
            35_000L,
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = listOf(intro, commercial),
                durationMs = 600_000,
                behaviors = defaults + (MediaSegmentType.INTRO to SegmentBehavior.AUTO_SKIP),
            ),
        )
    }

    @Test
    fun emptySegmentList_neverClamps() {
        assertNull(
            clamp(
                targetMs = 30_000,
                currentPositionMs = 10_000,
                segments = emptyList(),
                durationMs = 600_000,
            ),
        )
    }

    // ── The data-carrying resolution (the toast's segment type) ─────────────

    @Test
    fun fullResolution_carriesTheSegmentType() {
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 20_000, endMs = 35_000)
        val resolution = resolveForwardSeekSegmentClamp(
            targetMs = 30_000,
            currentPositionMs = 10_000,
            segments = listOf(commercial),
            segmentBehaviors = defaults,
            durationMs = 600_000,
            enabled = true,
        )
        assertEquals(SegmentSeekClamp(adjustedTargetMs = 35_000L, segmentType = MediaSegmentType.COMMERCIAL), resolution)
    }

    @Test
    fun fullResolution_miss_returnsNull() {
        val resolution = resolveForwardSeekSegmentClamp(
            targetMs = 30_000,
            currentPositionMs = 10_000,
            segments = emptyList(),
            segmentBehaviors = defaults,
            durationMs = 600_000,
            enabled = true,
        )
        assertNull(resolution)
    }
}
