package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamType
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
        assertTrue(state.audioTracks.isEmpty())
        assertTrue(state.subtitleTracks.isEmpty())
        assertTrue(state.chapters.isEmpty())
        assertFalse(state.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.dialogueBoostStrength)
        assertFalse(state.nightModeEnabled)
        assertEquals(EffectStrength.MODERATE, state.nightModeStrength)
        assertFalse(state.audioPassthrough)
        assertFalse(state.isOcrRunning)
        assertNull(state.detectedAspectRatio)
        assertNull(state.ocrText)
        assertNull(state.streamUrl)
    }

    @Test
    fun isInIntro_noTimestamps_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_hasIntroFalse_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            introTimestamps = IntroTimestamps(
                itemId = "1",
                introStartTicks = 0,
                introEndTicks = 0,
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_positionBeforePrompt_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 0L,
            introTimestamps = IntroTimestamps(
                itemId = "1",
                introStartTicks = 100_000_000,
                introEndTicks = 300_000_000,
                showSkipPromptAtTicks = 150_000_000,
                hideSkipPromptAtTicks = 280_000_000,
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_positionInPromptRange_returnsTrue() {
        val state = VideoPlayerUiState(
            currentPosition = 20_000L,
            introTimestamps = IntroTimestamps(
                itemId = "1",
                introStartTicks = 100_000_000,
                introEndTicks = 300_000_000,
                showSkipPromptAtTicks = 150_000_000,
                hideSkipPromptAtTicks = 280_000_000,
            ),
        )
        assertTrue(state.isInIntro)
    }

    @Test
    fun isInIntro_positionAfterPrompt_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            introTimestamps = IntroTimestamps(
                itemId = "1",
                introStartTicks = 100_000_000,
                introEndTicks = 300_000_000,
                showSkipPromptAtTicks = 150_000_000,
                hideSkipPromptAtTicks = 280_000_000,
            ),
        )
        assertFalse(state.isInIntro)
    }

    @Test
    fun isInIntro_withoutPromptTicks_usesStartEnd() {
        val state = VideoPlayerUiState(
            currentPosition = 20_000L,
            introTimestamps = IntroTimestamps(
                itemId = "1",
                introStartTicks = 100_000_000,
                introEndTicks = 300_000_000,
                showSkipPromptAtTicks = 0,
                hideSkipPromptAtTicks = 0,
            ),
        )
        assertTrue(state.isInIntro)
    }

    @Test
    fun isInCredits_noTimestamps_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 3_600_000L,
        )
        assertFalse(state.isInCredits)
    }

    @Test
    fun isInCredits_hasCreditsFalse_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 3_600_000L,
            creditTimestamps = CreditTimestamps(
                itemId = "1",
                creditStartTicks = 0,
                creditEndTicks = 0,
            ),
        )
        assertFalse(state.isInCredits)
    }

    @Test
    fun isInCredits_positionInRange_returnsTrue() {
        val state = VideoPlayerUiState(
            currentPosition = 3_600_000L,
            creditTimestamps = CreditTimestamps(
                itemId = "1",
                creditStartTicks = 35_000_000_000,
                creditEndTicks = 38_000_000_000,
                showSkipPromptAtTicks = 35_500_000_000,
                hideSkipPromptAtTicks = 37_500_000_000,
            ),
        )
        assertTrue(state.isInCredits)
    }

    @Test
    fun isInCredits_lastChapter_stillInRange_returnsTrue() {
        val state = VideoPlayerUiState(
            currentPosition = 3_700_000L,
            creditTimestamps = CreditTimestamps(
                itemId = "1",
                creditStartTicks = 35_000_000_000,
                creditEndTicks = 38_000_000_000,
            ),
        )
        assertTrue(state.isInCredits)
    }

    @Test
    fun isInCredits_positionBeforeRange_returnsFalse() {
        val state = VideoPlayerUiState(
            currentPosition = 3_400_000L,
            creditTimestamps = CreditTimestamps(
                itemId = "1",
                creditStartTicks = 35_000_000_000,
                creditEndTicks = 38_000_000_000,
            ),
        )
        assertFalse(state.isInCredits)
    }

    @Test
    fun shouldShowUpNext_noNextEpisode_returnsFalse() {
        val state = VideoPlayerUiState()
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_noSeriesId_returnsFalse() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = null,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_inCredits_returnsTrue() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = "series1",
            creditTimestamps = CreditTimestamps(
                itemId = "1",
                creditStartTicks = 35_000_000_000,
                creditEndTicks = 38_000_000_000,
            ),
            currentPosition = 3_600_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_nearEndWithoutCredits_returnsTrue() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 3_575_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_farFromEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 1_800_000L,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_exactly30sBeforeEnd_returnsTrue() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 3_570_000L,
        )
        assertTrue(state.shouldShowUpNext)
    }

    @Test
    fun shouldShowUpNext_moreThan30sBeforeEnd_returnsFalse() {
        val state = VideoPlayerUiState(
            nextEpisode = MediaItem(id = "2", name = "Ep 2", mediaType = MediaType.EPISODE),
            seriesId = "series1",
            duration = 3_600_000L,
            currentPosition = 3_569_000L,
        )
        assertFalse(state.shouldShowUpNext)
    }

    @Test
    fun hdrType_noVideoStream_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.AUDIO),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_videoStreamNoRange_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrVideo_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR"),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_sdrCaseInsensitive_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "sdr"),
            ),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_hdr10Video_returnsHDR10() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10"),
            ),
        )
        assertEquals("HDR10", state.hdrType)
    }

    @Test
    fun hdrType_dolbyVisionVideo_returnsDolbyVision() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "DOVI"),
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
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, realFrameRate = 23.976f),
            ),
        )
        assertEquals(23.976f, state.videoFrameRate!!, 0.001f)
    }

    @Test
    fun videoFrameRate_noFrameRate_returnsNull() {
        val state = VideoPlayerUiState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO),
            ),
        )
        assertNull(state.videoFrameRate)
    }
}
