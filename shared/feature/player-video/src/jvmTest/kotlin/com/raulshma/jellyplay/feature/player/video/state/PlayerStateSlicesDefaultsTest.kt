package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadState
import com.raulshma.jellyplay.feature.player.video.SubtitleDownloadStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Defaults snapshot for the @Immutable state slices the player screen is
 * composed from — the stable contract the sheets/controllers read when the
 * ViewModel constructs its initial [com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState].
 * Deliberately asserts only defaults (+ one copy round-trip per slice to pin
 * data-class semantics); per-feature behavior lives with each feature's tests.
 */
class PlayerStateSlicesDefaultsTest {

    @Test
    fun episodeBrowserState_defaults() {
        val state = EpisodeBrowserState()
        assertNull(state.nextEpisode)
        assertNull(state.previousEpisode)
        assertNull(state.currentSeasonId)
        assertTrue(state.seriesSeasons.isEmpty())
        assertTrue(state.seasonEpisodes.isEmpty())
        assertFalse(state.isLoadingEpisodes)
        // Episode browsing is opt-out only: the default must stay discoverable.
        assertTrue(state.videoEpisodeBrowserEnabled)

        val updated = state.copy(videoEpisodeBrowserEnabled = false, currentSeasonId = "season-1")
        assertFalse(updated.videoEpisodeBrowserEnabled)
        assertEquals("season-1", updated.currentSeasonId)
    }

    @Test
    fun trackState_defaults() {
        val state = TrackState()
        assertTrue(state.audioTracks.isEmpty())
        assertTrue(state.subtitleTracks.isEmpty())
        // No override/pref is active until the resolver or the user sets one.
        assertFalse(state.hasAudioOverride)
        assertFalse(state.hasSubtitleOverride)
        assertFalse(state.hasSeriesAudioPref)
        assertFalse(state.hasSeriesSubtitlePref)
        assertFalse(state.hasSeriesSubtitleOffPref)
        assertFalse(state.hasSeriesDialogueBoostPref)

        val updated = state.copy(hasSeriesSubtitleOffPref = true)
        assertTrue(updated.hasSeriesSubtitleOffPref)
    }

    @Test
    fun syncPlayUiState_defaults() {
        val state = SyncPlayUiState()
        assertFalse(state.isInSyncPlaySession)
        assertNull(state.syncPlayGroupName)
        assertEquals(0, state.syncPlayParticipantCount)
        assertFalse(state.isSyncPlaySynced)
        assertFalse(state.isSyncPlaySyncing)
        assertEquals(SyncPlayRepeatMode.REPEAT_NONE, state.syncPlayRepeatMode)
        assertEquals(SyncPlayShuffleMode.SORTED, state.syncPlayShuffleMode)

        val updated = state.copy(isInSyncPlaySession = true, syncPlayParticipantCount = 3)
        assertTrue(updated.isInSyncPlaySession)
        assertEquals(3, updated.syncPlayParticipantCount)
    }

    @Test
    fun videoFxState_defaults() {
        val state = VideoFxState()
        // The default effects config is the neutral identity — no hidden filter
        // may be applied to a fresh session.
        assertTrue(state.videoEffects.isNeutral)
        assertEquals(AspectRatio.AUTO, state.aspectRatio)
        assertNull(state.detectedAspectRatio)
        assertEquals(0f, state.tvZoomModePercent, 0.001f)

        val updated = state.copy(aspectRatio = AspectRatio.FILL, detectedAspectRatio = AspectRatio.RATIO_4_3)
        assertEquals(AspectRatio.FILL, updated.aspectRatio)
        assertEquals(AspectRatio.RATIO_4_3, updated.detectedAspectRatio)
    }

    @Test
    fun segmentState_defaults() {
        val state = SegmentState()
        assertTrue(state.segments.isEmpty())
        // Behaviors default to the server/user-agnostic base map: every known
        // segment type has a non-null behavior (no missing-branch skips).
        assertEquals(SegmentBehavior.DEFAULT_BEHAVIORS, state.segmentBehaviors)
        assertEquals(
            SegmentBehavior.SHOW_BUTTON,
            state.segmentBehaviors[MediaSegmentType.INTRO],
        )
        assertEquals(
            SegmentBehavior.IGNORE,
            state.segmentBehaviors[MediaSegmentType.UNKNOWN],
        )

        val updated = state.copy(segments = emptyList(), segmentBehaviors = emptyMap())
        assertTrue(updated.segmentBehaviors.isEmpty())
    }

    @Test
    fun sleepTimerState_defaults() {
        val state = SleepTimerState()
        assertFalse(state.sleepTimerActive)
        assertFalse(state.sleepTimerEndOfEpisode)
        assertEquals(0L, state.sleepTimerLastUsedDurationMs)

        val updated = state.copy(sleepTimerActive = true, sleepTimerEndOfEpisode = true)
        assertTrue(updated.sleepTimerActive)
        assertTrue(updated.sleepTimerEndOfEpisode)
    }

    @Test
    fun gesturePrefsState_defaults() {
        val state = GesturePrefsState()
        assertTrue(state.gesturesEnabled)
        assertTrue(state.holdSpeedEnabled)
        assertEquals(2.0f, state.holdSpeedMultiplier, 0.001f)
        assertFalse(state.isHoldSpeedActive)
        assertEquals(1.0f, state.defaultSpeed, 0.001f)
        assertEquals(120_000L, state.swipeSeekMaxMs)
        assertEquals(10_000L, state.seekDurationMs)
        assertFalse(state.rememberBrightness)
        assertEquals(0.5f, state.brightnessLevel, 0.001f)
        assertEquals(GestureIndicatorSide.OPPOSITE, state.gestureIndicatorSide)
        assertFalse(state.frameRateMatching)
        assertEquals(RefreshRateMode.OFF, state.refreshRateMode)

        val updated = state.copy(isHoldSpeedActive = true, frameRateMatching = true)
        assertTrue(updated.isHoldSpeedActive)
        assertTrue(updated.frameRateMatching)
    }

    @Test
    fun playerUiPrefsState_defaults() {
        val state = PlayerUiPrefsState()
        assertEquals(5_000L, state.controlsTimeoutMs)
        assertEquals(OrientationMode.SENSOR_LANDSCAPE, state.defaultOrientation)
        assertEquals(0, state.passOutProtectionHours) // pass-out protection off by default
        assertFalse(state.showVideoStats)
        assertTrue(state.showPlaybackMetadata)
        assertFalse(state.showClock)
        assertFalse(state.showTimeRemaining)
        assertTrue(state.keepScreenOnDuringVideo)
        assertFalse(state.usePinForPlayerLock)
        assertFalse(state.hasPin) // presence flag only — never the hash itself
        assertTrue(state.trickplayEnabled)
        assertTrue(state.trickplayOnSeekGesture)
        assertNull(state.trickplayInfo)
        assertEquals(StreamingQuality.AUTO, state.streamingQuality)
        assertTrue(state.adaptiveBitrateEnabled)
        assertEquals(PlaybackMode.AUTO, state.playbackMode)

        val updated = state.copy(hasPin = true, playbackMode = PlaybackMode.FORCE_TRANSCODE)
        assertTrue(updated.hasPin)
        assertEquals(PlaybackMode.FORCE_TRANSCODE, updated.playbackMode)
    }

    @Test
    fun autoplayState_defaults() {
        val state = AutoplayState()
        // Autoplay-next is opt-in; the countdown is the standard 10 s.
        assertFalse(state.videoAutoplayNext)
        assertEquals(10, state.autoPlayCountdownSec)
        assertFalse(state.autoplayCancelled)

        val updated = state.copy(videoAutoplayNext = true, autoplayCancelled = true)
        assertTrue(updated.videoAutoplayNext)
        assertTrue(updated.autoplayCancelled)
    }

    @Test
    fun audioEffectsState_defaults() {
        val state = AudioEffectsState()
        // Every toggle starts off; the strengths/enums are the neutral presets.
        assertFalse(state.nightModeEnabled)
        assertEquals(EffectStrength.MODERATE, state.nightModeStrength)
        assertFalse(state.audioPassthrough)
        assertEquals(DecoderMode.HW_PREFERRED, state.decoderMode)
        assertEquals(AudioNormalizationMode.NONE, state.audioNormalizationMode)
        assertFalse(state.audioNormalizationEnabled)
        assertEquals(ChannelMixMode.AUTO, state.channelMixMode)
        assertFalse(state.channelMixEnabled)
        assertFalse(state.bassBoostEnabled)
        assertEquals(EffectStrength.MODERATE, state.bassBoostStrength)
        assertFalse(state.virtualizerEnabled)
        assertEquals(500, state.virtualizerStrength)
        assertEquals(ReverbPreset.NONE, state.reverbPreset)
        assertEquals(0L, state.audioDelayMs)

        val updated = state.copy(audioDelayMs = 250L, bassBoostEnabled = true)
        assertEquals(250L, updated.audioDelayMs)
        assertTrue(updated.bassBoostEnabled)
    }

    @Test
    fun subtitleState_defaults() {
        val state = SubtitleState()
        assertTrue(state.remoteSubtitles.isEmpty())
        assertTrue(state.subtitleCultures.isEmpty())
        assertTrue(state.searchedSubtitles.isEmpty())
        assertFalse(state.isSearchingSubtitles)
        assertFalse(state.hasSearchedSubtitles)
        assertNull(state.subtitleSearchError)
        assertFalse(state.isUploadingSubtitle)
        assertFalse(state.isLoadingRemoteSubtitles)
        assertNull(state.remoteSubtitlesError)
        assertEquals("eng", state.defaultSearchLanguage)
        assertTrue(state.downloadingSubtitles.isEmpty())
        assertTrue(state.readySubtitles.isEmpty())
        assertTrue(state.providerSearchResults.isEmpty())
        assertTrue(state.providerSearchErrors.isEmpty())
        assertTrue(state.configuredSubtitleProviders.isEmpty())

        val updated = state.copy(
            hasSearchedSubtitles = true,
            downloadingSubtitles = mapOf(
                "sub-1" to SubtitleDownloadStatus("sub-1", SubtitleDownloadState.DOWNLOADING),
            ),
        )
        assertTrue(updated.hasSearchedSubtitles)
        assertEquals(SubtitleDownloadState.DOWNLOADING, updated.downloadingSubtitles["sub-1"]?.state)
    }

    @Test
    fun subtitleRowKey_namespaces_stayDistinct() {
        // Sheet-row key and engine side-load id are two different id spaces;
        // the builders must keep them mechanically derivable but not equal.
        val rowKey = providerSubtitleRowKey(SubtitleProviderKind.WYZIE, "sub-9")
        assertEquals("${SubtitleProviderKind.WYZIE}:sub-9", rowKey)
        assertEquals("provider:$rowKey", providerSubtitleEngineId(rowKey))
        assertTrue(providerSubtitleEngineId(rowKey) != rowKey)
    }
}
