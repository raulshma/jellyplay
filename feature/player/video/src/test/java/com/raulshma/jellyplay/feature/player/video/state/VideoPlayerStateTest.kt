package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerStateTest {

    @Test
    fun playerUiPrefsState_defaultValuesAndCopy() {
        val state = PlayerUiPrefsState()
        assertEquals(5_000L, state.controlsTimeoutMs)
        assertEquals(OrientationMode.SENSOR_LANDSCAPE, state.defaultOrientation)
        assertTrue(state.showPlaybackMetadata)
        assertFalse(state.usePinForPlayerLock)
        assertEquals(StreamingQuality.AUTO, state.streamingQuality)
        assertEquals(PlaybackMode.AUTO, state.playbackMode)

        val updated = state.copy(usePinForPlayerLock = true, streamingQuality = StreamingQuality.FHD_1080P)
        assertTrue(updated.usePinForPlayerLock)
        assertEquals(StreamingQuality.FHD_1080P, updated.streamingQuality)
    }

    @Test
    fun trackState_defaultValuesAndCopy() {
        val state = TrackState()
        assertTrue(state.audioTracks.isEmpty())
        assertTrue(state.subtitleTracks.isEmpty())
        assertFalse(state.hasAudioOverride)
        assertFalse(state.hasSubtitleOverride)

        val updated = state.copy(hasAudioOverride = true, hasSubtitleOverride = true)
        assertTrue(updated.hasAudioOverride)
        assertTrue(updated.hasSubtitleOverride)
    }

    @Test
    fun videoFxState_defaultValuesAndCopy() {
        val state = VideoFxState()
        assertEquals(AspectRatio.AUTO, state.aspectRatio)
        assertNull(state.detectedAspectRatio)
        assertEquals(0f, state.tvZoomModePercent, 0.001f)

        val updated = state.copy(aspectRatio = AspectRatio.FILL, tvZoomModePercent = 100f)
        assertEquals(AspectRatio.FILL, updated.aspectRatio)
        assertEquals(100f, updated.tvZoomModePercent, 0.001f)
    }

    @Test
    fun audioEffectsState_defaultValuesAndCopy() {
        val state = AudioEffectsState()
        assertFalse(state.dialogueBoostEnabled)
        assertFalse(state.nightModeEnabled)
        assertEquals(DecoderMode.HW_PREFERRED, state.decoderMode)
        assertEquals(0L, state.audioDelayMs)

        val updated = state.copy(dialogueBoostEnabled = true, decoderMode = DecoderMode.SW_ONLY, audioDelayMs = 250L)
        assertTrue(updated.dialogueBoostEnabled)
        assertEquals(DecoderMode.SW_ONLY, updated.decoderMode)
        assertEquals(250L, updated.audioDelayMs)
    }

    @Test
    fun gesturePrefsState_defaultValuesAndCopy() {
        val state = GesturePrefsState()
        assertTrue(state.gesturesEnabled)
        assertTrue(state.holdSpeedEnabled)
        assertEquals(2.0f, state.holdSpeedMultiplier, 0.001f)
        assertEquals(10_000L, state.seekDurationMs)

        val updated = state.copy(gesturesEnabled = false, holdSpeedMultiplier = 3.0f)
        assertFalse(updated.gesturesEnabled)
        assertEquals(3.0f, updated.holdSpeedMultiplier, 0.001f)
    }

    @Test
    fun episodeBrowserState_defaultValuesAndCopy() {
        val state = EpisodeBrowserState()
        assertNull(state.nextEpisode)
        assertTrue(state.seriesSeasons.isEmpty())
        assertTrue(state.seasonEpisodes.isEmpty())
        assertFalse(state.isLoadingEpisodes)

        val updated = state.copy(isLoadingEpisodes = true, currentSeasonId = "season-1")
        assertTrue(updated.isLoadingEpisodes)
        assertEquals("season-1", updated.currentSeasonId)
    }

    @Test
    fun segmentState_defaultValuesAndCopy() {
        val state = SegmentState()
        assertTrue(state.segments.isEmpty())
        assertFalse(state.segmentBehaviors.isEmpty())
    }

    @Test
    fun subtitleState_defaultValuesAndCopy() {
        val state = SubtitleState()
        assertTrue(state.remoteSubtitles.isEmpty())
        assertFalse(state.isSearchingSubtitles)
        assertEquals("eng", state.defaultSearchLanguage)

        val updated = state.copy(isSearchingSubtitles = true, defaultSearchLanguage = "spa")
        assertTrue(updated.isSearchingSubtitles)
        assertEquals("spa", updated.defaultSearchLanguage)
    }
}
