package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
) : JellyPlayViewModel() {

    /** Playback-screen slice — recomposes this screen only on playback-field writes. */
    val preferences: StateFlow<PlaybackPreferences> = projections.playbackPreferences

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    fun setVideoGesturesEnabled(enabled: Boolean) = editor.setVideoGesturesEnabled(enabled)
    fun setVideoHoldSpeedEnabled(enabled: Boolean) =
        editor.edit { videoPlayer.setVideoHoldSpeedEnabled(enabled) }
    fun setVideoAutoplayNext(enabled: Boolean) = editor.setVideoAutoplayNext(enabled)
    fun setTrailerAutoplay(enabled: Boolean) =
        editor.edit { videoPlayer.setTrailerAutoplay(enabled) }
    fun setCinemaModeEnabled(enabled: Boolean) =
        editor.edit { videoPlayer.setCinemaModeEnabled(enabled) }
    fun setAndroidTvWatchNextEnabled(enabled: Boolean) = editor.edit {
        playback.setAndroidTvWatchNextEnabled(enabled)
        tvWatchNextScheduler.scheduleRefresh()
    }
    fun setVideoEpisodeBrowserEnabled(enabled: Boolean) =
        editor.edit { videoPlayer.setVideoEpisodeBrowserEnabled(enabled) }
    fun setVideoShowPlaybackMetadata(enabled: Boolean) =
        editor.edit { videoPlayer.setVideoShowPlaybackMetadata(enabled) }
    fun setVideoRememberBrightness(enabled: Boolean) =
        editor.edit { videoPlayer.setVideoRememberBrightness(enabled) }
    fun setTrickplayEnabled(enabled: Boolean) =
        editor.edit { videoPlayer.setTrickplayEnabled(enabled) }
    fun setTrickplayOnSeekGesture(enabled: Boolean) =
        editor.edit { videoPlayer.setTrickplayOnSeekGesture(enabled) }
    fun setBackgroundVideoAudioEnabled(enabled: Boolean) =
        editor.edit { playback.setBackgroundVideoAudioEnabled(enabled) }
    fun setKeepScreenOnDuringVideo(enabled: Boolean) =
        editor.edit { playback.setKeepScreenOnDuringVideo(enabled) }
    fun setIncognitoModeEnabled(enabled: Boolean) =
        editor.edit { videoPlayer.setIncognitoModeEnabled(enabled) }
    fun setShowTimeRemaining(enabled: Boolean) =
        editor.edit { videoPlayer.setShowTimeRemaining(enabled) }
    fun setShowClockInPlayer(enabled: Boolean) =
        editor.edit { videoPlayer.setShowClockInPlayer(enabled) }
    fun setPauseOnAudioFocusLoss(enabled: Boolean) =
        editor.edit { playback.setPauseOnAudioFocusLoss(enabled) }
    fun setDuckOnTransientFocusLoss(enabled: Boolean) =
        editor.edit { playback.setDuckOnTransientFocusLoss(enabled) }
    fun setDialogueBoostEnabled(enabled: Boolean) =
        editor.edit { audioEffects.setDialogueBoostEnabled(enabled) }
    fun setDialogueBoostStrength(strength: EffectStrength) =
        editor.edit { audioEffects.setDialogueBoostStrength(strength) }
    fun setDecoderMode(mode: DecoderMode) =
        editor.edit { playback.setDecoderMode(mode) }
    fun setAudioPassthrough(enabled: Boolean) =
        editor.edit { playback.setAudioPassthrough(enabled) }
    fun setFrameRateMatching(enabled: Boolean) =
        editor.edit { playback.setFrameRateMatching(enabled) }
    fun setRefreshRateMode(mode: com.raulshma.jellyplay.core.model.RefreshRateMode) =
        editor.edit { playback.setRefreshRateMode(mode) }
    fun setStreamingQuality(quality: StreamingQuality) = editor.setStreamingQuality(quality)
    fun setLiveStreamOption(option: LiveStreamOption) = editor.setLiveStreamOption(option)
    fun setMpvConfig(config: MpvEngineConfig) =
        editor.edit { engine.setMpvConfig(config) }
    fun setLibVlcConfig(config: LibVlcEngineConfig) =
        editor.edit { engine.setLibVlcConfig(config) }
    fun setExoPlayerConfig(config: ExoPlayerEngineConfig) =
        editor.edit { engine.setExoPlayerConfig(config) }
    fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) =
        editor.edit { videoPlayer.setSegmentBehavior(type, behavior) }
    fun setSyncPlayAutoAcceptInvites(enabled: Boolean) =
        editor.edit { syncPlayCast.setSyncPlayAutoAcceptInvites(enabled) }
    fun setSyncPlayJoinBehavior(behavior: SyncPlayJoinBehavior) =
        editor.edit { syncPlayCast.setSyncPlayJoinBehavior(behavior) }
    fun setSyncPlayToleranceMs(ms: Long) =
        editor.edit { syncPlayCast.setSyncPlayToleranceMs(ms) }
    fun setBackgroundCastingEnabled(enabled: Boolean) =
        editor.edit { syncPlayCast.setBackgroundCastingEnabled(enabled) }
    fun setPreferredRenderer(renderer: String?) =
        editor.edit { syncPlayCast.setPreferredRenderer(renderer) }
    fun setDefaultCastingStrategy(strategy: CastingStrategy) =
        editor.edit { syncPlayCast.setDefaultCastingStrategy(strategy) }
    fun setDvrPrePaddingMinutes(minutes: Int) =
        editor.edit { syncPlayCast.setDvrPrePaddingMinutes(minutes) }
    fun setDvrPostPaddingMinutes(minutes: Int) =
        editor.edit { syncPlayCast.setDvrPostPaddingMinutes(minutes) }
    fun setDvrRecordingQuality(quality: String) =
        editor.edit { syncPlayCast.setDvrRecordingQuality(quality) }
    fun setPreferredPlayer(playerType: PlayerType) = editor.setPreferredPlayer(playerType)
    fun setVideoDefaultOrientation(mode: OrientationMode) = editor.setVideoDefaultOrientation(mode)
    fun setVideoDefaultAspectRatio(ratio: String) =
        editor.edit { videoPlayer.setVideoDefaultAspectRatio(ratio) }
    fun setVideoDefaultSpeed(speed: Float) =
        editor.edit { videoPlayer.setVideoDefaultSpeed(speed) }
    fun setVideoHoldSpeedMultiplier(multiplier: Float) =
        editor.edit { videoPlayer.setVideoHoldSpeedMultiplier(multiplier) }
    fun setVideoSeekDurationMs(ms: Long) = editor.setVideoSeekDurationMs(ms)
    fun setVideoControlsTimeoutMs(ms: Long) =
        editor.edit { videoPlayer.setVideoControlsTimeoutMs(ms) }
    fun setVideoSkipBackOnResumeMs(ms: Long) =
        editor.edit { videoPlayer.setVideoSkipBackOnResumeMs(ms) }
    fun setVideoPassOutProtectionHours(hours: Int) =
        editor.edit { videoPlayer.setVideoPassOutProtectionHours(hours) }
    fun setVideoSwipeSeekMaxMs(ms: Long) =
        editor.edit { videoPlayer.setVideoSwipeSeekMaxMs(ms) }
    fun setVideoPreloadBufferSize(size: PreloadBufferSize) =
        editor.edit { videoPlayer.setVideoPreloadBufferSize(size) }
    fun setAudioDelayMs(ms: Long) =
        editor.edit { audio.setAudioDelay(ms) }
    fun setVideoBrightnessLevel(level: Float) =
        editor.edit { videoPlayer.setVideoBrightnessLevel(level) }
    fun setVideoGestureIndicatorSide(side: GestureIndicatorSide) =
        editor.edit { videoPlayer.setVideoGestureIndicatorSide(side) }
    fun setAutoPlayCountdownSec(sec: Int) =
        editor.edit { playback.setAutoPlayCountdownSec(sec) }
    fun setTvZoomModePercent(percent: Float) =
        editor.edit { videoPlayer.setTvZoomModePercent(percent) }

    /**
     * Resets a single preference category. Mirrors
     * [AppearanceSettingsViewModel.resetCategory], delegating to the shared
     * [PreferencesEditor] so the coverage-guarded key list stays the single
     * source of truth.
     */
    fun resetCategory(category: PreferenceResetCategory) = editor.resetCategory(category)

    /**
     * Screen-level reset for the Playback settings screen. Resets every category
     * rendered here — the player/advanced prefs ([PreferenceResetCategory.PLAYBACK])
     * and the per-engine config ([PreferenceResetCategory.PLAYER_ENGINES]) — so the
     * whole screen returns to defaults in one action, mirroring the appearance
     * screen's reset but spanning both categories this screen owns.
     */
    fun resetPlaybackSettings() {
        editor.resetCategory(PreferenceResetCategory.PLAYBACK)
        editor.resetCategory(PreferenceResetCategory.PLAYER_ENGINES)
    }
}
