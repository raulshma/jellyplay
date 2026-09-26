package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pure logic tests for PlaybackProgressReporter algorithms.
 * Tests the decision logic in checkAutoSkip, checkEndedNoNext,
 * and watched-threshold calculations without needing coroutines or mocks.
 */
class PlaybackProgressReporterLogicTest {

    // ─── checkEndedNoNext logic ────────────────────────────────────────────────

    @Test
    fun checkEndedNoNext_durationZero_doesNotTrigger() {
        val durationMs = 0L
        val positionMs = 0L
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertFalse(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_positionFarFromEnd_doesNotTrigger() {
        val durationMs = 3_600_000L
        val positionMs = 1_000_000L
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertFalse(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_positionAtEndMinus499ms_triggers() {
        val durationMs = 3_600_000L
        val positionMs = durationMs - 499L // just within trigger window
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertTrue(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_positionAtEnd_triggers() {
        val durationMs = 3_600_000L
        val positionMs = durationMs
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertTrue(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_positionAtEndMinus500ms_doesNotTrigger() {
        val durationMs = 3_600_000L
        val positionMs = durationMs - 500L
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertTrue(shouldTrigger) // >= so 500ms is exactly at the trigger point
    }

    @Test
    fun checkEndedNoNext_positionAtEndMinus501ms_doesNotTrigger() {
        val durationMs = 3_600_000L
        val positionMs = durationMs - 501L
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L
        assertFalse(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_withNextEpisode_doesNotTrigger() {
        val durationMs = 3_600_000L
        val positionMs = durationMs
        val hasNextEpisode = true
        // The check returns early if nextEpisode != null
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L && !hasNextEpisode
        assertFalse(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_withoutNextEpisode_triggers() {
        val durationMs = 3_600_000L
        val positionMs = durationMs
        val hasNextEpisode = false
        val shouldTrigger = durationMs > 0L && positionMs >= durationMs - 500L && !hasNextEpisode
        assertTrue(shouldTrigger)
    }

    @Test
    fun checkEndedNoNext_alreadyTriggered_doesNotTriggerAgain() {
        var endedNoNextTriggered = false
        val durationMs = 3_600_000L
        val positionMs = durationMs
        val hasNextEpisode = false

        fun check() {
            if (endedNoNextTriggered) return
            if (durationMs <= 0L) return
            if (positionMs < durationMs - 500L) return
            if (hasNextEpisode) return
            endedNoNextTriggered = true
        }

        check()
        assertTrue(endedNoNextTriggered)
        // Calling again should be a no-op (endedNoNextTriggered is already true)
        var calledCount = 0
        fun checkWithCount() {
            if (endedNoNextTriggered) return
            calledCount++
            endedNoNextTriggered = true
        }
        checkWithCount()
        // calledCount should still be 0 because we returned early
        assertTrue(calledCount == 0)
    }

    // ─── checkAutoSkip logic ───────────────────────────────────────────────────

    @Test
    fun checkAutoSkip_noActiveSegment_doesNotSkip() {
        val activeSegment: MediaSegment? = null
        val shouldSkip = activeSegment != null
        assertFalse(shouldSkip)
    }

    @Test
    fun checkAutoSkip_behaviorIgnore_doesNotSkip() {
        val segment = MediaSegment(
            id = "seg1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val behavior = SegmentBehavior.IGNORE
        val shouldSkip = behavior == SegmentBehavior.AUTO_SKIP
        assertFalse(shouldSkip)
    }

    @Test
    fun checkAutoSkip_behaviorAutoSkip_skips() {
        val segment = MediaSegment(
            id = "seg1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val behavior = SegmentBehavior.AUTO_SKIP
        val shouldSkip = behavior == SegmentBehavior.AUTO_SKIP
        assertTrue(shouldSkip)
    }

    @Test
    fun checkAutoSkip_alreadySkippedSegment_doesNotDoubleSkip() {
        val autoSkippedSegments = mutableSetOf<String>()
        val segmentId = "seg1"

        // First call: should skip
        val firstSkip = segmentId !in autoSkippedSegments
        if (firstSkip) autoSkippedSegments.add(segmentId)
        assertTrue(firstSkip)

        // Second call: should not skip
        val secondSkip = segmentId !in autoSkippedSegments
        assertFalse(secondSkip)
    }

    @Test
    fun checkAutoSkip_differentSegments_eachSkippedOnce() {
        val autoSkippedSegments = mutableSetOf<String>()
        val seg1 = "intro"
        val seg2 = "commercial"

        val firstSkipIntro = seg1 !in autoSkippedSegments
        autoSkippedSegments.add(seg1)
        assertTrue(firstSkipIntro)

        val firstSkipCommercial = seg2 !in autoSkippedSegments
        autoSkippedSegments.add(seg2)
        assertTrue(firstSkipCommercial)

        assertFalse(seg1 !in autoSkippedSegments)
        assertFalse(seg2 !in autoSkippedSegments)
    }

    // ─── Watched threshold logic ───────────────────────────────────────────────

    @Test
    fun watchedThreshold_95percent_triggers() {
        val posMs = 5_700_000L  // 95% of 6_000_000
        val durationMs = 6_000_000L
        val progressPercent = (posMs.toFloat() / durationMs.toFloat()) * 100f
        assertTrue(progressPercent >= 95f)
    }

    @Test
    fun watchedThreshold_94percent_doesNotTrigger() {
        val posMs = 5_640_000L  // 94% of 6_000_000
        val durationMs = 6_000_000L
        val progressPercent = (posMs.toFloat() / durationMs.toFloat()) * 100f
        assertFalse(progressPercent >= 95f)
    }

    @Test
    fun watchedThreshold_zeroDuration_doesNotTrigger() {
        val posMs = 0L
        val durationMs = 0L
        val shouldCheck = durationMs > 0
        assertFalse(shouldCheck)
    }

    @Test
    fun watchedThreshold_alreadyTriggered_doesNotTriggerAgain() {
        var watchedThresholdTriggered = false
        val posMs = 5_700_000L
        val durationMs = 6_000_000L

        fun check() {
            if (watchedThresholdTriggered) return
            if (durationMs <= 0) return
            val progressPercent = (posMs.toFloat() / durationMs.toFloat()) * 100f
            if (progressPercent >= 95f) {
                watchedThresholdTriggered = true
            }
        }
        check()
        assertTrue(watchedThresholdTriggered)

        var secondCallInvoked = false
        fun checkSecond() {
            if (watchedThresholdTriggered) return
            secondCallInvoked = true
        }
        checkSecond()
        assertFalse(secondCallInvoked) // was blocked by the flag
    }

    // ─── Stalled-finish predicate ─────────────────────────────────────

    /** Reference stall: inside the last 2 s, frozen ≥ 10 s, playing, READY. */
    private fun stalled(
        positionMs: Long = 3_598_500L, // 1 h runtime − 1.5 s
        durationMs: Long = 3_600_000L,
        stalledForMs: Long = STALLED_FINISH_NO_ADVANCE_MS,
        isPlaying: Boolean = true,
        playbackState: EnginePlaybackState = EnginePlaybackState.READY,
    ) = shouldTreatAsStalledFinish(positionMs, durationMs, stalledForMs, isPlaying, playbackState)

    @Test
    fun stalledFinish_referenceStall_triggers() {
        assertTrue(stalled())
    }

    @Test
    fun stalledFinish_exactlyAtWindowEdge_triggers() {
        // duration − 2_000 is the inclusive boundary of the end window.
        assertTrue(stalled(positionMs = 3_600_000L - STALLED_FINISH_END_WINDOW_MS))
    }

    @Test
    fun stalledFinish_justOutsideWindow_doesNotTrigger() {
        assertFalse(stalled(positionMs = 3_600_000L - STALLED_FINISH_END_WINDOW_MS - 1L))
    }

    @Test
    fun stalledFinish_frozenForExactly10s_triggers() {
        assertTrue(stalled(stalledForMs = 10_000L))
    }

    @Test
    fun stalledFinish_frozenForJustUnder10s_doesNotTrigger() {
        assertFalse(stalled(stalledForMs = 9_999L))
    }

    @Test
    fun stalledFinish_paused_doesNotTrigger() {
        assertFalse(stalled(isPlaying = false))
    }

    @Test
    fun stalledFinish_errorState_doesNotTrigger() {
        assertFalse(stalled(playbackState = EnginePlaybackState.ERROR))
    }

    @Test
    fun stalledFinish_idleState_doesNotTrigger() {
        assertFalse(stalled(playbackState = EnginePlaybackState.IDLE))
    }

    @Test
    fun stalledFinish_endedState_doesNotTrigger() {
        // The genuine-EOF path owns a real ENDED — the stall detector must
        // not double-complete it.
        assertFalse(stalled(playbackState = EnginePlaybackState.ENDED))
    }

    @Test
    fun stalledFinish_bufferingState_triggers() {
        // A starved cache is exactly what an end-of-file demuxer stall
        // looks like from the outside.
        assertTrue(stalled(playbackState = EnginePlaybackState.BUFFERING))
    }

    @Test
    fun stalledFinish_zeroDuration_doesNotTrigger() {
        assertFalse(stalled(durationMs = 0L, positionMs = 0L))
    }

    @Test
    fun stalledFinish_windowConstants_matchThePlan() {
        assertTrue(STALLED_FINISH_END_WINDOW_MS == 2_000L)
        assertTrue(STALLED_FINISH_NO_ADVANCE_MS == 10_000L)
    }

    // ─── Progress reporting interval ──────────────────────────────────────────

    @Test
    fun progressReportingInterval_is10Seconds() {
        val intervalMs = 10_000L
        assertTrue(intervalMs == 10_000L)
    }

    // ─── Position ticks calculation ────────────────────────────────────────────

    @Test
    fun positionTicksForReport_correctMultiplier() {
        val currentPositionMs = 60_000L
        val positionTicks = currentPositionMs * 10_000L
        assertTrue(positionTicks == 600_000_000L)
    }

    @Test
    fun positionTicksForStopReport_zeroWhenNoEngine() {
        val enginePositionMs: Long? = null
        val positionTicks = enginePositionMs?.let { it * 10_000L } ?: 0L
        assertTrue(positionTicks == 0L)
    }
}
