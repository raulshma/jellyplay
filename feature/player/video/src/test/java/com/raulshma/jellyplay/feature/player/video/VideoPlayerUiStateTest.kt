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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerUiStateTest {

    @Test
    fun defaultState_hasCorrectDefaults() {
        val state = VideoPlayerUiState()
        assertEquals("", state.title)
        assertEquals("", state.subtitle)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
        assertTrue(state.chapters.isEmpty())
        assertFalse(state.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.dialogueBoostStrength)
        assertNull(state.videoFx.detectedAspectRatio)
        assertNull(state.media.streamUrl)
    }

    @Test
    fun isInIntro_noSegments_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_emptySegment_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 0, endTicks = 0,
                    )
                ),
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_positionBeforeSegment_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 0L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_positionInSegmentRange_returnsTrue() {
        val state = VideoPlayerUiState(
            currentPosition = 20_000L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        assertTrue(state.isInIntro)
    }

    @Test
    fun isInIntro_positionAfterSegment_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.INTRO,
                        startTicks = 100_000_000, endTicks = 300_000_000,
                    )
                ),
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_behaviorIgnore_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 20_000L,
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
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInCredits_noSegments_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 3_600_000L,
        )
        assertFalse(state.isInCredits)
    }

    @Test
    fun isInCredits_positionInRange_returnsTrue() {
        val state = VideoPlayerUiState(
            currentPosition = 3_600_000L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.OUTRO,
                        startTicks = 35_000_000_000, endTicks = 38_000_000_000,
                    )
                ),
            ),
        )
        assertTrue(state.isInCredits)
    }

    @Test
    fun isInCredits_positionBeforeRange_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 3_400_000L,
            segmentState = SegmentState(
                segments = listOf(
                    MediaSegment(
                        id = "1", itemId = "1", type = MediaSegmentType.OUTRO,
                        startTicks = 35_000_000_000, endTicks = 38_000_000_000,
                    )
                ),
            ),
        )
        assertFalse(state.isInCredits)
    }

    @Test
    fun activeSegment_commercialTakesPriorityOverIntro() {
        val state = VideoPlayerUiState(
            currentPosition = 20_000L,
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
        val seg = state.activeSegment
        assertTrue(seg != null && seg.type == MediaSegmentType.COMMERCIAL)
    }

    @Test
    fun activeSegment_recapTakesPriorityOverPreview() {
        val state = VideoPlayerUiState(
            currentPosition = 5_000L,
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
        val seg = state.activeSegment
        assertTrue(seg != null && seg.type == MediaSegmentType.RECAP)
    }

    @Test
    fun shouldShowUpNext_noNextEpisode_returnsFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_noSeriesId_returnsFalse() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = null),
        )
        assertFalse(state.shouldShowUpNext)
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
            duration = 3_800_000L,
            currentPosition = 3_600_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_nearEndWithoutCredits_returnsTrue() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
            duration = 3_600_000L,
            currentPosition = 3_575_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_farFromEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
            duration = 3_600_000L,
            currentPosition = 1_800_000L,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_exactly30sBeforeEnd_returnsTrue() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
            duration = 3_600_000L,
            currentPosition = 3_570_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_moreThan30sBeforeEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            episodes = EpisodeBrowserState(
                nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            ),
            media = MediaContentState(seriesId = "series1"),
            duration = 3_600_000L,
            currentPosition = 3_569_000L,
        )
        assertFalse(state.shouldShowUpNext)
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
        assertEquals("HDR10", state.hdrType)
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
        assertEquals("DOVI", state.hdrType)
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
