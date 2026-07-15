package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val editor: PreferencesEditor,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
) : JellyPlayViewModel() {

    /** Playback-screen slice — recomposes this screen only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = store.playbackPreferences

    val showAdvancedSettings: StateFlow<Boolean> = store.preferences
        .map { it.showAdvancedSettings }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { setShowAdvancedSettings(enabled) }

    fun setVideoGesturesEnabled(enabled: Boolean) = editor.setVideoGesturesEnabled(enabled)
    fun setVideoHoldSpeedEnabled(enabled: Boolean) =
        editor.edit { setVideoHoldSpeedEnabled(enabled) }
    fun setVideoAutoplayNext(enabled: Boolean) = editor.setVideoAutoplayNext(enabled)
    fun setTrailerAutoplay(enabled: Boolean) =
        editor.edit { setTrailerAutoplay(enabled) }
    fun setCinemaModeEnabled(enabled: Boolean) =
        editor.edit { setCinemaModeEnabled(enabled) }
    fun setAndroidTvWatchNextEnabled(enabled: Boolean) = editor.edit {
        setAndroidTvWatchNextEnabled(enabled)
        tvWatchNextScheduler.scheduleRefresh()
    }
    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) =
        editor.edit { setVideoEpisodeBrowserEnabled(enabled) }
    fun setVideoShowPlaybackMetadata(enabled: Boolean) =
        editor.edit { setVideoShowPlaybackMetadata(enabled) }
    fun setVideoRememberBrightness(enabled: Boolean) =
        editor.edit { setVideoRememberBrightness(enabled) }
    fun setTrickplayEnabled(enabled: Boolean) =
        editor.edit { setTrickplayEnabled(enabled) }
    fun setTrickplayOnSeekGesture(enabled: Boolean) =
        editor.edit { setTrickplayOnSeekGesture(enabled) }
    fun setBackgroundVideoAudioEnabled(enabled: Boolean) =
        editor.edit { setBackgroundVideoAudioEnabled(enabled) }
    fun setKeepScreenOnDuringVideo(enabled: Boolean) =
        editor.edit { setKeepScreenOnDuringVideo(enabled) }
    fun setIncognitoModeEnabled(enabled: Boolean) =
        editor.edit { setIncognitoModeEnabled(enabled) }
    fun setShowTimeRemaining(enabled: Boolean) =
        editor.edit { setShowTimeRemaining(enabled) }
    fun setShowClockInPlayer(enabled: Boolean) =
        editor.edit { setShowClockInPlayer(enabled) }
    fun setPauseOnAudioFocusLoss(enabled: Boolean) =
        editor.edit { setPauseOnAudioFocusLoss(enabled) }
    fun setDuckOnTransientFocusLoss(enabled: Boolean) =
        editor.edit { setDuckOnTransientFocusLoss(enabled) }
    fun setDialogueBoostEnabled(enabled: Boolean) =
        editor.edit { setDialogueBoostEnabled(enabled) }
    fun setDialogueBoostStrength(strength: EffectStrength) =
        editor.edit { setDialogueBoostStrength(strength) }
    fun setDecoderMode(mode: DecoderMode) =
        editor.edit { setDecoderMode(mode) }
    fun setAudioPassthrough(enabled: Boolean) =
        editor.edit { setAudioPassthrough(enabled) }
    fun setFrameRateMatching(enabled: Boolean) =
        editor.edit { setFrameRateMatching(enabled) }
    fun setStreamingQuality(quality: StreamingQuality) = editor.setStreamingQuality(quality)
    fun setMpvConfig(config: MpvEngineConfig) =
        editor.edit { setMpvConfig(config) }
    fun setLibVlcConfig(config: LibVlcEngineConfig) =
        editor.edit { setLibVlcConfig(config) }
    fun setExoPlayerConfig(config: ExoPlayerEngineConfig) =
        editor.edit { setExoPlayerConfig(config) }
    fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) =
        editor.edit { setSegmentBehavior(type, behavior) }
    fun setSyncPlayAutoAcceptInvites(enabled: Boolean) =
        editor.edit { setSyncPlayAutoAcceptInvites(enabled) }
    fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) =
        editor.edit { setSyncPlayJoinBehavior(behavior) }
    fun setSyncPlayToleranceMs(ms: Long) =
        editor.edit { setSyncPlayToleranceMs(ms) }
    fun setBackgroundCastingEnabled(enabled: Boolean) =
        editor.edit { setBackgroundCastingEnabled(enabled) }
    fun setPreferredRenderer(renderer: String?) =
        editor.edit { setPreferredRenderer(renderer) }
    fun setDefaultCastingStrategy(strategy: CastingStrategy) =
        editor.edit { setDefaultCastingStrategy(strategy) }
    fun setDvrPrePaddingMinutes(minutes: Int) =
        editor.edit { setDvrPrePaddingMinutes(minutes) }
    fun setDvrPostPaddingMinutes(minutes: Int) =
        editor.edit { setDvrPostPaddingMinutes(minutes) }
    fun setDvrRecordingQuality(quality: String) =
        editor.edit { setDvrRecordingQuality(quality) }
    fun setPreferredPlayer(playerType: PlayerType) = editor.setPreferredPlayer(playerType)
    fun setVideoDefaultOrientation(mode: OrientationMode) = editor.setVideoDefaultOrientation(mode)
    fun setVideoDefaultAspectRatio(ratio: String) =
        editor.edit { setVideoDefaultAspectRatio(ratio) }
    fun setVideoDefaultSpeed(speed: Float) =
        editor.edit { setVideoDefaultSpeed(speed) }
    fun setVideoHoldSpeedMultiplier(multiplier: Float) =
        editor.edit { setVideoHoldSpeedMultiplier(multiplier) }
    fun setVideoSeekDurationMs(ms: Long) = editor.setVideoSeekDurationMs(ms)
    fun setVideoControlsTimeoutMs(ms: Long) =
        editor.edit { setVideoControlsTimeoutMs(ms) }
    fun setVideoSkipBackOnResumeMs(ms: Long) =
        editor.edit { setVideoSkipBackOnResumeMs(ms) }
    fun setVideoPassOutProtectionHours(hours: Int) =
        editor.edit { setVideoPassOutProtectionHours(hours) }
    fun setVideoSwipeSeekMaxMs(ms: Long) =
        editor.edit { setVideoSwipeSeekMaxMs(ms) }
    fun setVideoPreloadBufferSize(size: PreloadBufferSize) =
        editor.edit { setVideoPreloadBufferSize(size) }
    fun setAudioDelayMs(ms: Long) =
        editor.edit { setAudioDelay(ms) }
    fun setVideoBrightnessLevel(level: Float) =
        editor.edit { setVideoBrightnessLevel(level) }
    fun setAutoPlayCountdownSec(sec: Int) =
        editor.edit { setAutoPlayCountdownSec(sec) }
    fun setTvZoomModePercent(percent: Float) =
        editor.edit { setTvZoomModePercent(percent) }
}
