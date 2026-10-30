package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Supplementary tests for VideoPlayerUiState covering gaps in the existing suite.
 *
 * The state carries no position/duration snapshot (those ride the ViewModel's
 * dedicated flows), so every time-dependent verdict here feeds the
 * position-explicit overloads: [VideoPlayerUiState.computeActiveSegment] takes
 * the raw position, and the intro/credits/up-next verdicts run
 * [SegmentCalculator] against `SegmentProjection(state).toSegmentInput(duration)`.
 */
class VideoPlayerUiStateExtendedTest {

    // ─── Chapter segment detection ─────────────────────────────────────────────

    @Test
    fun detectChapterSegment_introChapterName_returnsIntroSegment() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 600_000_000L), // 1 minute
            ),
        )
        val seg = state.computeActiveSegment(positionMs = 5_000L, durationMs = 3_600_000L)
        assertNotNull(seg)
        assertEquals(MediaSegmentType.INTRO, seg!!.type)
    }

    @Test
    fun detectChapterSegment_outroChapterName_returnsOutroSegment() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Main", startPositionTicks = 0L),
                ChapterInfo(name = "Credits", startPositionTicks = 35_400_000_000L),
            ),
        )
        val seg = state.computeActiveSegment(positionMs = 3_550_000L, durationMs = 3_600_000L)
        assertNotNull(seg)
        assertEquals(MediaSegmentType.OUTRO, seg!!.type)
    }

    @Test
    fun detectChapterSegment_unrecognizedName_returnsNull() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Act 1", startPositionTicks = 0L),
                ChapterInfo(name = "Act 2", startPositionTicks = 600_000_000L),
            ),
        )
        assertNull(state.computeActiveSegment(positionMs = 30_000L, durationMs = 3_600_000L))
    }

    @Test
    fun detectChapterSegment_usesNextChapterStartAsEnd() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L), // 30 seconds ticks
            ),
        )
        val seg = state.computeActiveSegment(positionMs = 5_000L, durationMs = 3_600_000L)
        assertNotNull(seg)
        assertEquals(300_000_000L, seg!!.endTicks)
    }

    @Test
    fun detectChapterSegment_lastChapter_usesDurationAsEnd() {
        val duration = 3_600_000L
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Main", startPositionTicks = 0L),
                ChapterInfo(name = "Credits", startPositionTicks = 35_400_000_000L),
            ),
        )
        val seg = state.computeActiveSegment(positionMs = 3_550_000L, durationMs = duration)
        assertNotNull(seg)
        assertEquals(duration * 10_000, seg!!.endTicks)
    }

    @Test
    fun detectChapterSegment_noChapters_returnsNull() {
        val state = VideoPlayerUiState(chapters = emptyList())
        assertNull(state.computeActiveSegment(positionMs = 30_000L))
    }

    // ─── isInSegmentType ──────────────────────────────────────────────────────

    @Test
    fun isInSegmentType_introWithShowBehavior_trueWhenInIntro() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.isInSegmentType(input, 5_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInSegmentType_introWithIgnoreBehavior_falseEvenWhenInIntro() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.IGNORE),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 5_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInSegmentType_outro_falseWhenInIntro() {
        val state = VideoPlayerUiState(
            chapters = listOf(
                ChapterInfo(name = "Intro", startPositionTicks = 0L),
                ChapterInfo(name = "Main", startPositionTicks = 300_000_000L),
            ),
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 5_000L, MediaSegmentType.OUTRO))
    }

    // ─── segmentEndTicksForType ────────────────────────────────────────────────

    @Test
    fun segmentEndTicksForType_introType_returnsEndTicks() {
        val segment = MediaSegment(
            id = "intro1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertEquals(300_000_000L, SegmentCalculator.segmentEndTicksForType(input, 5_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun segmentEndTicksForType_outroType_returnsNullWhenActiveIsIntro() {
        val segment = MediaSegment(
            id = "intro1", itemId = "item1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 300_000_000L,
        )
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertNull(SegmentCalculator.segmentEndTicksForType(input, 5_000L, MediaSegmentType.OUTRO))
    }

    @Test
    fun segmentEndTicksForType_noActiveSegment_returnsNull() {
        val state = VideoPlayerUiState(segmentState = SegmentState(segments = emptyList()))
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertNull(SegmentCalculator.segmentEndTicksForType(input, 30_000L, MediaSegmentType.INTRO))
    }

    // ─── segmentEndTicks(segment) ─────────────────────────────────────────────

    @Test
    fun segmentEndTicks_hasSegmentTrue_returnsEndTicks() {
        val seg = MediaSegment(
            id = "s1", itemId = "i1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 600_000_000L,
        )
        val state = VideoPlayerUiState(segmentState = SegmentState(segments = listOf(seg)))
        assertEquals(600_000_000L, state.segmentEndTicks(seg))
    }

    @Test
    fun segmentEndTicks_hasSegmentFalse_returnsNull() {
        val seg = MediaSegment(
            id = "s1", itemId = "i1",
            type = MediaSegmentType.INTRO,
            startTicks = 0L, endTicks = 0L,
        )
        val state = VideoPlayerUiState()
        assertNull(state.segmentEndTicks(seg))
    }

    // ─── isOutroNearEnd ───────────────────────────────────────────────────────

    @Test
    fun isOutroNearEnd_outroEndsNearDuration_true() {
        // Outro ends 10 seconds before the end (10s < 30s threshold)
        val duration = 3_600_000L   // 60-minute video in ms
        val outroEndTicks = (duration - 10_000L) * 10_000L // 10 seconds before end in ticks
        val segment = MediaSegment(
            id = "outro", itemId = "item",
            type = MediaSegmentType.OUTRO,
            startTicks = (duration - 60_000L) * 10_000L,
            endTicks = outroEndTicks,
        )
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(duration)
        assertTrue(SegmentCalculator.isOutroNearEnd(input, duration - 30_000L))
    }

    @Test
    fun isOutroNearEnd_outroEndsLongBeforeDuration_false() {
        // Outro ends 5 minutes before the end
        val duration = 3_600_000L
        val outroEndTicks = (duration - 300_000L) * 10_000L
        val segment = MediaSegment(
            id = "outro", itemId = "item",
            type = MediaSegmentType.OUTRO,
            startTicks = (duration - 600_000L) * 10_000L,
            endTicks = outroEndTicks,
        )
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(segment),
                segmentBehaviors = mapOf(MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(duration)
        assertFalse(SegmentCalculator.isOutroNearEnd(input, duration - 350_000L))
    }

    @Test
    fun isOutroNearEnd_zeroDuration_false() {
        val state = VideoPlayerUiState()
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isOutroNearEnd(input, 0L))
    }

    // ─── hdrType ──────────────────────────────────────────────────────────────

    @Test
    fun hdrType_noStreams_returnsNull() {
        val state = VideoPlayerUiState(media = MediaContentState(mediaStreams = emptyList()))
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR")))
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_hdrVideoStream_returnsRange() {
        val state = VideoPlayerUiState(
            media = MediaContentState(mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10")))
        )
        assertEquals(state.hdrType, "HDR10")
    }

    @Test
    fun hdrType_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(mediaStreams = listOf(MediaStream(index = 0, type = StreamType.AUDIO)))
        )
        assertNull(state.hdrType)
    }

    // ─── videoFrameRate ───────────────────────────────────────────────────────

    @Test
    fun videoFrameRate_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(media = MediaContentState(mediaStreams = emptyList()))
        assertNull(state.videoFrameRate)
    }

    @Test
    fun videoFrameRate_videoStreamWithRate_returnsRate() {
        val state = VideoPlayerUiState(
            media = MediaContentState(mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, realFrameRate = 23.976f)))
        )
        assertEquals(23.976f, state.videoFrameRate!!, 0.001f)
    }

    // ─── behaviorForType ──────────────────────────────────────────────────────

    @Test
    fun behaviorForType_defaultsToIgnore_forUnspecifiedType() {
        val state = VideoPlayerUiState(segmentState = SegmentState(segmentBehaviors = emptyMap()))
        assertEquals(SegmentBehavior.IGNORE, state.behaviorForType(MediaSegmentType.COMMERCIAL))
    }

    @Test
    fun behaviorForType_customBehavior_returnsCustom() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.COMMERCIAL to SegmentBehavior.AUTO_SKIP)
            )
        )
        assertEquals(SegmentBehavior.AUTO_SKIP, state.behaviorForType(MediaSegmentType.COMMERCIAL))
    }

    @Test
    fun behaviorForType_showSkipButton_returnsShowSkipButton() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON)
            )
        )
        assertEquals(SegmentBehavior.SHOW_BUTTON, state.behaviorForType(MediaSegmentType.INTRO))
    }
}

/**
 * SyncPlay state tests. The group-display slice (name/count/sync status/
 * repeat/shuffle) moved to SyncPlayUiState, owned by SyncPlayBridge;
 * the session flag stays on VideoPlayerUiState because it feeds the
 * segment-overlay projection.
 */
class VideoPlayerUiStateSyncPlayTest {

    @Test
    fun syncPlay_defaults_groupNameIsNull() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertNull(state.syncPlayGroupName)
    }

    @Test
    fun syncPlay_defaults_participantCountIsZero() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(0, state.syncPlayParticipantCount)
    }

    @Test
    fun syncPlay_defaults_isSyncedFalse() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertFalse(state.isSyncPlaySynced)
    }

    @Test
    fun syncPlay_defaults_repeatModeIsNone() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(SyncPlayRepeatMode.REPEAT_NONE, state.syncPlayRepeatMode)
    }

    @Test
    fun syncPlay_defaults_shuffleModeIsSorted() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
        assertEquals(SyncPlayShuffleMode.SORTED, state.syncPlayShuffleMode)
    }

    @Test
    fun syncPlay_defaults_isInSessionFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.isInSyncPlaySession)
    }

    @Test
    fun shouldShowUpNext_falseWhenInSyncPlaySession() {
        val state = VideoPlayerUiState(
            isInSyncPlaySession = true,
            episodes = EpisodeBrowserState(
                nextEpisode = com.raulshma.jellyplay.core.model.MediaItem(
                    id = "ep2", name = "Episode 2",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE
                ),
            ),
            media = MediaContentState(seriesId = "series1"),
        )
        // 3_570_000 on a 3_600_000 runtime — would normally show up-next.
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun shouldShowUpNext_falseWhenNoNextEpisode() {
        val state = VideoPlayerUiState(
            isInSyncPlaySession = false,
            episodes = EpisodeBrowserState(nextEpisode = null),
            media = MediaContentState(seriesId = "series1"),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun shouldShowUpNext_siblingShowsEvenWithoutSeriesId() {
        // The former seriesId bypass is GONE: the segment calculator is pure
        // time-window math and the projection folds ANY next item (episode
        // sibling OR the Up Next queue mirror) into hasNextEpisode. A sibling
        // without a series id is no longer gated — the time window alone
        // decides (the queue case is pinned in SegmentCalculatorTest's
        // projection-fold section).
        val state = VideoPlayerUiState(
            isInSyncPlaySession = false,
            episodes = EpisodeBrowserState(
                nextEpisode = com.raulshma.jellyplay.core.model.MediaItem(
                    id = "ep2", name = "Episode 2",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE
                ),
            ),
            media = MediaContentState(seriesId = null),
        )
        // 3_570_000 on a 3_600_000 runtime — within the 30s pre-roll.
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun shouldShowUpNext_queuedMovieWithoutSibling_showsNearEnd() {
        // The queue-first fold through the uiState mirror: a queued movie has
        // no episode sibling at all, yet counts as next.
        val state = VideoPlayerUiState(
            isInSyncPlaySession = false,
            episodes = EpisodeBrowserState(nextEpisode = null),
            hasQueuedNext = true,
            media = MediaContentState(seriesId = null),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun syncPlay_copy_updatesGroupName() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(syncPlayGroupName = "Group 1")
        assertEquals(state.syncPlayGroupName, "Group 1")
    }

    @Test
    fun syncPlay_copy_updatesParticipantCount() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(syncPlayParticipantCount = 3)
        assertEquals(3, state.syncPlayParticipantCount)
    }

    @Test
    fun syncPlay_copy_updatesSynced() {
        val state = com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState()
            .copy(isSyncPlaySynced = true)
        assertTrue(state.isSyncPlaySynced)
    }
}
