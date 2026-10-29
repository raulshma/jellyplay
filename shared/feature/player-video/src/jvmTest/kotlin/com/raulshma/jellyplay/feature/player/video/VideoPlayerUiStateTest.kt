package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * VideoPlayerUiState tests. The state carries no position/duration snapshot
 * (those ride the ViewModel's dedicated flows), so the time-dependent verdicts
 * feed the position-explicit overloads: [VideoPlayerUiState.computeActiveSegment]
 * takes the raw position, and the intro/credits/up-next verdicts run
 * [SegmentCalculator] against `SegmentProjection(state).toSegmentInput(duration)`.
 */
class VideoPlayerUiStateTest {

    @Test
    fun defaultState_hasCorrectDefaults() {
        val state = VideoPlayerUiState()
        assertEquals(state.title, "")
        assertEquals(state.subtitle, "")
        assertFalse(state.isPlaying)
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
        assertTrue(state.chapters.isEmpty())
        assertFalse(state.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.dialogueBoostStrength)
        assertNull(state.videoFx.detectedAspectRatio)
        assertNull(state.media.streamUrl)
    }

    @Test
    fun isInIntro_noSegments_returnsFalse() {
        val state = VideoPlayerUiState()
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 30_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInIntro_emptySegment_returnsFalse() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 0, endTicks = 0,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 30_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInIntro_positionBeforeSegment_returnsFalse() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 0L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInIntro_positionInSegmentRange_returnsTrue() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertTrue(SegmentCalculator.isInSegmentType(input, 20_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInIntro_positionAfterSegment_returnsFalse() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 30_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInIntro_behaviorIgnore_returnsFalse() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
                segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.IGNORE),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 20_000L, MediaSegmentType.INTRO))
    }

    @Test
    fun isInCredits_noSegments_returnsFalse() {
        val state = VideoPlayerUiState()
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 3_600_000L, MediaSegmentType.OUTRO))
    }

    @Test
    fun isInCredits_positionInRange_returnsTrue() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.OUTRO,
                        startTicks = 35_000_000_000, endTicks = 38_000_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertTrue(SegmentCalculator.isInSegmentType(input, 3_600_000L, MediaSegmentType.OUTRO))
    }

    @Test
    fun isInCredits_positionBeforeRange_returnsFalse() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.OUTRO,
                        startTicks = 35_000_000_000, endTicks = 38_000_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.isInSegmentType(input, 3_400_000L, MediaSegmentType.OUTRO))
    }

    @Test
    fun activeSegment_commercialTakesPriorityOverIntro() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    ),
                    MediaSegment(
                        id = "2", itemId = "1", type = MediaSegmentType.COMMERCIAL,
                        startTicks = 150_000_000, endTicks = 250_000_000,
                    ),
                ),
            ),
        )
        val seg = state.computeActiveSegment(20_000L)
        assertTrue(seg != null && seg.type == MediaSegmentType.COMMERCIAL)
    }

    @Test
    fun activeSegment_recapTakesPriorityOverPreview() {
        val state = VideoPlayerUiState(
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.PREVIEW,
                        startTicks = 0, endTicks = 100_000_000,
                    ),
                    MediaSegment(
                        id = "2", itemId = "1", type = MediaSegmentType.RECAP,
                        startTicks = 0, endTicks = 80_000_000,
                    ),
                ),
            ),
        )
        val seg = state.computeActiveSegment(5_000L)
        assertTrue(seg != null && seg.type == MediaSegmentType.RECAP)
    }

    @Test
    fun shouldShowUpNext_noNextEpisode_returnsFalse() {
        val state = VideoPlayerUiState()
        val input = SegmentProjection(state).toSegmentInput(0L)
        assertFalse(SegmentCalculator.shouldShowUpNext(input, 0L))
    }

    @Test
    fun shouldShowUpNext_queuedMovieWithoutSibling_nearEnd_returnsTrue() {
        // The former seriesId bypass is GONE (the calculator is pure
        // time-window math; the projection folds the queue mirror into
        // hasNextEpisode) — so this replaces the old
        // shouldShowUpNext_noSeriesId_returnsFalse pin, which only passed
        // vacuously (duration 0 kept the window closed). A queued movie with
        // NO episode sibling near the end is exactly the case it used to
        // misjudge: the card must show.
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(nextEpisode = null),
            hasQueuedNext = true,
            media = MediaContentState(seriesId = null),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun shouldShowUpNext_inCreditsNearEnd_returnsTrue() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.OUTRO,
                        startTicks = 35_000_000_000, endTicks = 38_000_000_000,
                    )
                ),
            ),
        )
        val input = SegmentProjection(state).toSegmentInput(3_800_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_600_000L))
    }

    @Test
    fun shouldShowUpNext_nearEndWithoutCredits_returnsTrue() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_575_000L))
    }

    @Test
    fun shouldShowUpNext_farFromEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.shouldShowUpNext(input, 1_800_000L))
    }

    @Test
    fun shouldShowUpNext_exactly30sBeforeEnd_returnsTrue() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertTrue(SegmentCalculator.shouldShowUpNext(input, 3_570_000L))
    }

    @Test
    fun shouldShowUpNext_moreThan30sBeforeEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
        )
        val input = SegmentProjection(state).toSegmentInput(3_600_000L)
        assertFalse(SegmentCalculator.shouldShowUpNext(input, 3_569_000L))
    }

    @Test
    fun hdrType_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.AUDIO),
                ),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_videoStreamNoRange_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO),
                ),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrVideo_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR"),
                ),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrCaseInsensitive_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "sdr"),
                ),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_hdr10Video_returnsHDR10() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10"),
                ),
            ),
        )
        assertEquals(state.hdrType, "HDR10")
    }

    @Test
    fun hdrType_dolbyVisionVideo_returnsDolbyVision() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "DOVI"),
                ),
            ),
        )
        assertEquals(state.hdrType, "DOVI")
    }

    @Test
    fun videoFrameRate_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState()
        assertNull(state.videoFrameRate)
    }

    @Test
    fun videoFrameRate_withVideoStream_returnsFrameRate() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO, realFrameRate = 23.976f),
                ),
            ),
        )
        assertEquals(23.976f, state.videoFrameRate!!, 0.001f)
    }

    @Test
    fun videoFrameRate_noFrameRate_returnsNull() {
        val state = VideoPlayerUiState(
            media = MediaContentState(
                mediaStreams = listOf(
                    MediaStream(index = 0, type = StreamType.VIDEO),
                ),
            ),
        )
        assertNull(state.videoFrameRate)
    }
}
