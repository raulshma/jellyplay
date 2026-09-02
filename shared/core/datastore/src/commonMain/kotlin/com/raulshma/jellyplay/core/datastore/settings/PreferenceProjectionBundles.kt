package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.PlaybackPreferences

/**
 * Tuple-carrier data classes for the nested `combine(...)` calls in
 * [PreferenceProjections]. Each bundles the slices one half of a nested
 * combine produces, so a projection that reads more than Flow's 5-arg
 * combine arity can be built in two stages.
 *
 * Extracted into their own file so the "which slices feed which combine
 * half" plumbing — which changes only when a projection's combine shape
 * changes — is separate from the projection field-sets in
 * [PreferenceProjections], which change when a screen's field set changes.
 * That keeps [PreferenceProjections] from being edited for two unrelated
 * reasons (Divergent Change).
 *
 * `internal` rather than `private`: the carriers are implementation details
 * of this package's read layer and are never consumed outside it.
 */
internal data class PlaybackCoreBundle(
    val playback: PlaybackSlice,
    val video: VideoPlayerSlice,
    val effects: AudioEffectsSlice,
    val subtitle: SubtitleSlice,
    val audio: AudioSlice,
    val engine: PlayerEngineSlice,
) {
    fun toPlaybackPreferences(sp: SyncPlayCastSlice) = PlaybackPreferences(
        preferredPlayer = playback.preferredPlayer,
        decoderMode = playback.decoderMode,
        audioPassthrough = playback.audioPassthrough,
        frameRateMatching = playback.frameRateMatching,
        refreshRateMode = playback.refreshRateMode,
        videoSeekDurationMs = video.videoSeekDurationMs,
        videoDefaultOrientation = video.videoDefaultOrientation,
        videoControlsTimeoutMs = video.videoControlsTimeoutMs,
        videoGesturesEnabled = video.videoGesturesEnabled,
        videoHoldSpeedEnabled = video.videoHoldSpeedEnabled,
        videoHoldSpeedMultiplier = video.videoHoldSpeedMultiplier,
        videoDefaultSpeed = video.videoDefaultSpeed,
        videoDefaultAspectRatio = video.videoDefaultAspectRatio,
        videoAutoplayNext = video.videoAutoplayNext,
        trailerAutoplay = video.trailerAutoplay,
        cinemaModeEnabled = video.cinemaModeEnabled,
        videoSwipeSeekMaxMs = video.videoSwipeSeekMaxMs,
        videoRememberBrightness = video.videoRememberBrightness,
        videoBrightnessLevel = video.videoBrightnessLevel,
        videoGestureIndicatorSide = video.videoGestureIndicatorSide,
        videoSkipBackOnResumeMs = video.videoSkipBackOnResumeMs,
        videoPassOutProtectionHours = video.videoPassOutProtectionHours,
        trickplayEnabled = video.trickplayEnabled,
        trickplayOnSeekGesture = video.trickplayOnSeekGesture,
        segmentBehaviors = video.segmentBehaviors,
        videoEpisodeBrowserEnabled = video.videoEpisodeBrowserEnabled,
        videoShowPlaybackMetadata = video.videoShowPlaybackMetadata,
        videoPreloadBufferSize = video.videoPreloadBufferSize,
        videoCacheSizeMb = video.videoCacheSizeMb,
        keepScreenOnDuringVideo = playback.keepScreenOnDuringVideo,
        showTimeRemaining = video.showTimeRemaining,
        pauseOnAudioFocusLoss = playback.pauseOnAudioFocusLoss,
        duckOnTransientFocusLoss = playback.duckOnTransientFocusLoss,
        dialogueBoostEnabled = effects.dialogueBoostEnabled,
        dialogueBoostStrength = effects.dialogueBoostStrength,
        audioDelayMs = audio.audioDelayMs,
        backgroundVideoAudioEnabled = playback.backgroundVideoAudioEnabled,
        autoPlayCountdownSec = playback.autoPlayCountdownSec,
        incognitoModeEnabled = video.incognitoModeEnabled,
        showClockInPlayer = video.showClockInPlayer,
        tvZoomModePercent = video.tvZoomModePercent,
        streamingQuality = playback.streamingQuality,
        liveStreamOption = playback.liveStreamOption,
        mpvConfig = engine.mpvConfig,
        libVlcConfig = engine.libVlcConfig,
        exoPlayerConfig = engine.exoPlayerConfig,
        syncPlayJoinBehavior = sp.syncPlayJoinBehavior,
        syncPlayToleranceMs = sp.syncPlayToleranceMs,
        syncPlayAutoAcceptInvites = sp.syncPlayAutoAcceptInvites,
        defaultCastingStrategy = sp.defaultCastingStrategy,
        backgroundCastingEnabled = sp.backgroundCastingEnabled,
        preferredRenderer = sp.preferredRenderer,
        dvrPrePaddingMinutes = sp.dvrPrePaddingMinutes,
        dvrPostPaddingMinutes = sp.dvrPostPaddingMinutes,
        dvrRecordingQuality = sp.dvrRecordingQuality,
        androidTvWatchNextEnabled = playback.androidTvWatchNextEnabled,
    )
}

/** Slices for the nested half of the appearance-screen combine. */
internal data class AppearanceScreenBundle(
    val appearance: AppearanceSlice,
    val navigation: NavigationSlice,
    val home: HomeDiscoverySlice,
    val library: LibrarySlice,
    val experimental: ExperimentalSlice,
)

/** Slices for the nested half of the detail-screen combine. */
internal data class DetailScreenBundle(
    val appearance: AppearanceSlice,
    val video: VideoPlayerSlice,
    val subtitle: SubtitleSlice,
    val experimental: ExperimentalSlice,
)

/** First half of the onboarding combine: appearance + home + nav + playback. */
internal data class OnboardingThemeHomeNavPlaybackBundle(
    val appearance: AppearanceSlice,
    val home: HomeDiscoverySlice,
    val navigation: NavigationSlice,
    val playback: PlaybackSlice,
)

/** Second half of the onboarding combine: player + audio + subtitle + security. */
internal data class OnboardingPlayerAudioSubSecurityBundle(
    val video: VideoPlayerSlice,
    val audio: AudioSlice,
    val subtitle: SubtitleSlice,
    val security: SecuritySlice,
)

/** Slice half of the settings-screen combine. */
internal data class SettingsCoreBundle(
    val appearance: AppearanceSlice,
    val playback: PlaybackSlice,
    val audio: AudioSlice,
    val subtitle: SubtitleSlice,
    val security: SecuritySlice,
)

/** Experimental + notification + screensaver half of the settings-screen combine. */
internal data class SettingsAuxBundle(
    val experimental: ExperimentalSlice,
    val notification: NotificationSlice,
    val screensaver: ScreensaverSlice,
)

/** Slice half of the main-screen combine. */
internal data class MainScreenBundle(
    val appearance: AppearanceSlice,
    val security: SecuritySlice,
    val home: HomeDiscoverySlice,
    val navigation: NavigationSlice,
    val experimental: ExperimentalSlice,
)
