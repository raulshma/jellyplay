package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class SegmentCalculatorTest {

    private fun input(
        segments: List<MediaSegment> = emptyList(),
        chapters: List<ChapterInfo> = emptyList(),
        segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
        durationMs: Long = 0L,
        autoplayCancelled: Boolean = false,
        isInSyncPlaySession: Boolean = false,
        hasNextEpisode: Boolean = false,
        seriesId: String? = null,
    ) = SegmentCalculatorInput(
        segments = segments,
        chapters = chapters,
        segmentBehaviors = segmentBehaviors,
        durationMs = durationMs,
        autoplayCancelled = autoplayCancelled,
        isInSyncPlaySession = isInSyncPlaySession,
        hasNextEpisode = hasNextEpisode,
        seriesId = seriesId,
    )

    // ms → ticks helper. 1 ms == 10_000 ticks.
    private fun ms(ms: Long) = ms
    private fun ticks(ms: Long) = ms * 10_000

    private fun segment(
        type: MediaSegmentType = MediaSegmentType.INTRO,
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

    @Test
    fun computeActiveSegment_returnsNull_whenNoSegmentsOrChapters() {
        val result = SegmentCalculator.computeActiveSegment(input(), positionMs = 5_000)
        assertNull(result)
    }

    @Test
    fun computeActiveSegment_matchesApiSegmentByPriority() {
        // COMMERCIAL has higher priority than INTRO per MediaSegmentType.SEGMENT_PRIORITY.
        val commercial = segment(MediaSegmentType.COMMERCIAL, startMs = 1_000, endMs = 5_000)
        val intro = segment(MediaSegmentType.INTRO, startMs = 1_000, endMs = 5_000)
        val result = SegmentCalculator.computeActiveSegment(
            input(segments = listOf(intro, commercial)),
            positionMs = 2_000,
        )
        assertNotNull(result)
        assertEquals(MediaSegmentType.COMMERCIAL, result!!.type)
    }

    @Test
    fun computeActiveSegment_halfOpenInterval_endExclusive() {
        val intro = segment(MediaSegmentType.INTRO, startMs = 1_000, endMs = 5_000)
        // At exactly endMs (5_000), the segment no longer contains the position.
        val result = SegmentCalculator.computeActiveSegment(
            input(segments = listOf(intro)),
            positionMs = 5_000,
        )
        assertNull(result)
    }

    @Test
    fun computeActiveSegment_fallsBackToChapterName() {
        val chapter = ChapterInfo(
            startPositionTicks = ticks(0),
            name = "Intro",
        )
        val result = SegmentCalculator.computeActiveSegment(
            input(chapters = listOf(chapter), durationMs = 60_000),
            positionMs = 2_000,
        )
        assertNotNull(result)
        assertEquals(MediaSegmentType.INTRO, result!!.type)
        assertEquals("chapter-INTRO-0", result.id)
    }

    @Test
    fun isInSegmentType_falseWhenBehaviorIgnore() {
        val intro = segment(MediaSegmentType.INTRO, startMs = 0, endMs = 5_000)
        val behaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.IGNORE)
        val result = SegmentCalculator.isInSegmentType(
            input(segments = listOf(intro), segmentBehaviors = behaviors),
            positionMs = 1_000,
            type = MediaSegmentType.INTRO,
        )
        assertFalse(result)
    }

    @Test
    fun isInSegmentType_trueWhenInSegmentAndBehaviorNotIgnore() {
        val intro = segment(MediaSegmentType.INTRO, startMs = 0, endMs = 5_000)
        val result = SegmentCalculator.isInSegmentType(
            input(segments = listOf(intro)),
            positionMs = 1_000,
            type = MediaSegmentType.INTRO,
        )
        assertTrue(result) // DEFAULT_BEHAVIORS has INTRO → SHOW_BUTTON
    }

    @Test
    fun shouldShowUpNext_falseWhenAutoplayCancelled() {
        val result = SegmentCalculator.shouldShowUpNext(
            input(autoplayCancelled = true, hasNextEpisode = true, seriesId = "s1"),
            positionMs = 0,
        )
        assertFalse(result)
    }

    @Test
    fun shouldShowUpNext_falseWhenInSyncPlay() {
        val result = SegmentCalculator.shouldShowUpNext(
            input(isInSyncPlaySession = true, hasNextEpisode = true, seriesId = "s1"),
            positionMs = 0,
        )
        assertFalse(result)
    }

    @Test
    fun shouldShowUpNext_trueWhenWithin30sOfEnd() {
        val result = SegmentCalculator.shouldShowUpNext(
            input(durationMs = 100_000, hasNextEpisode = true, seriesId = "s1"),
            positionMs = 80_000, // > 100_000 - 30_000
        )
        assertTrue(result)
    }

    @Test
    fun shouldShowUpNext_falseWhenNoNextEpisode() {
        val result = SegmentCalculator.shouldShowUpNext(
            input(durationMs = 100_000, hasNextEpisode = false, seriesId = "s1"),
            positionMs = 99_000,
        )
        assertFalse(result)
    }

    @Test
    fun shouldShowUpNext_falseWhenNoSeriesId() {
        val result = SegmentCalculator.shouldShowUpNext(
            input(durationMs = 100_000, hasNextEpisode = true, seriesId = null),
            positionMs = 99_000,
        )
        assertFalse(result)
    }

    @Test
    fun isOutroNearEnd_trueWhenOutroEndsWithin30sOfDuration() {
        val outro = segment(MediaSegmentType.OUTRO, startMs = 90_000, endMs = 95_000)
        val result = SegmentCalculator.isOutroNearEnd(
            input(segments = listOf(outro), durationMs = 100_000),
            positionMs = 92_000,
        )
        assertTrue(result) // (100_000 - 95_000)ms = 5s < 30s
    }

    @Test
    fun isOutroNearEnd_falseWhenOutroFarFromEnd() {
        val outro = segment(MediaSegmentType.OUTRO, startMs = 10_000, endMs = 15_000)
        val result = SegmentCalculator.isOutroNearEnd(
            input(segments = listOf(outro), durationMs = 100_000),
            positionMs = 12_000,
        )
        assertFalse(result) // 85s remaining > 30s
    }

    @Test
    fun behaviorForType_returnsIgnoreWhenAbsent() {
        val result = SegmentCalculator.behaviorForType(
            input(segmentBehaviors = emptyMap()),
            type = MediaSegmentType.INTRO,
        )
        assertEquals(SegmentBehavior.IGNORE, result)
    }

    @Test
    fun segmentEndTicks_prefersApiMatchOverChapterSynthesis() {
        val apiSeg = segment(MediaSegmentType.INTRO, startMs = 0, endMs = 10_000, id = "api-1")
        val result = SegmentCalculator.segmentEndTicks(
            input(segments = listOf(apiSeg)),
            segment = apiSeg,
        )
        assertEquals(ticks(10_000), result)
    }
}
