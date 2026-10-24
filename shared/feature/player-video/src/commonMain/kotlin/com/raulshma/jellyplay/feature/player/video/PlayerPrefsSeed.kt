package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * The seed half of the aggregate → uiState preferences mapping: ONE home for
 * "which pref feeds which [VideoPlayerUiState] leaf" when a session load
 * starts. [seededProjection] folds a [VideoPlayerAggregate] (plus the network
 * slice's `adaptiveBitrateEnabled`, passed in so this module stays pure and
 * store-free) into the extension-lambda shape [SessionLoadOutputs.onPrefsProjected]
 * applies to the residual uiState.
 *
 * Leaf inventory (45, grouped by destination slice):
 *  - root: `preferredPlayerType`
 *  - `uiPrefs`: defaultOrientation, controlsTimeoutMs, hideOsdOnPause,
 *    passOutProtectionHours, trickplayEnabled, trickplayOnSeekGesture,
 *    trickplayMode, showPlaybackMetadata, showClock, showTimeRemaining,
 *    keepScreenOnDuringVideo, streamingQuality, adaptiveBitrateEnabled,
 *    playbackMode, mpvToneMappingCurve, mpvDebandMode,
 *    mpvMotionInterpolation, bitmapSubtitleScaleMode, mpvCustomButtons,
 *    pauseScreensaverMode,
 *    pauseScreensaverAfterMinutes, ambientBlurEnabled, pauseDetailsEnabled,
 *    nextUpButtonPrerollSec, autoplayEpisodeLimit,
 *    showNextEpisodeDetailsAfterFinish
 *  - `gestures`: gestureMode (tap/swipe tier flags are derived views),
 *    holdSpeedEnabled, holdSpeedMultiplier, defaultSpeed, swipeSeekMaxMs,
 *    seekDurationMs, doubleTapHoldSeekEnabled, rememberBrightness,
 *    brightnessLevel, gestureIndicatorSide, frameRateMatching, refreshRateMode
 *  - `videoFx`: aspectRatio (parsed from `videoDefaultAspectRatio`), tvZoomModePercent
 *  - `segmentState`: segmentBehaviors (with the INTRO/OUTRO auto-skip flags OR-ed in)
 *  - `episodes`: videoEpisodeBrowserEnabled
 *  - `autoplay`: videoAutoplayNext, autoPlayCountdownSec
 *
 * Every other leaf is untouched: the transform is a plain `copy` of the
 * receiver, so session state (position, tracks, errors, controllers' slices)
 * survives the seed.
 *
 * This is the *load-time, unguarded* projection — one whole copy per session
 * load is the point. The *change-time* counterpart is [SettingsProjector]:
 * the same pref→leaf vocabulary (showPlaybackMetadata, showClock,
 * showTimeRemaining, tvZoomModePercent, keepScreenOnDuringVideo,
 * passOutProtectionHours, autoPlayCountdownSec, and the thirteen
 * render/pause/continuation leaves — mpvToneMappingCurve, mpvDebandMode,
 * mpvMotionInterpolation, bitmapSubtitleScaleMode, mpvCustomButtons, trickplayMode,
 * pauseScreensaverMode, pauseScreensaverAfterMinutes, ambientBlurEnabled,
 * pauseDetailsEnabled, nextUpButtonPrerollSec, autoplayEpisodeLimit,
 * showNextEpisodeDetailsAfterFinish — overlap) re-projected per
 * DataStore emission with distinct-until-changed guards. When adding or moving
 * a leaf, update both sides or drop a leaf here deliberately.
 *
 * Extracted verbatim from [SessionLoadPipeline]'s inline `onPrefsProjected {
 * copy(...) }` block; the pipeline keeps the stage order and only loses the
 * copy. Pinned by [PlayerPrefsSeedTest].
 */
internal object PlayerPrefsSeed {

    /**
     * Builds the prefs-derived uiState transform for one session load. Pure:
     * the returned lambda reads only [agg] / [adaptiveBitrateEnabled] and its
     * receiver, so callers may build it before or after other stages without
     * changing the result.
     */
    fun seededProjection(
        agg: VideoPlayerAggregate,
        adaptiveBitrateEnabled: Boolean,
    ): VideoPlayerUiState.() -> VideoPlayerUiState {
        val defaultAspectRatio = when (agg.videoPlayer.videoDefaultAspectRatio) {
            "FIT" -> AspectRatio.FIT
            "FILL" -> AspectRatio.FILL
            "CROP" -> AspectRatio.CROP
            "16:9" -> AspectRatio.RATIO_16_9
            "4:3" -> AspectRatio.RATIO_4_3
            "21:9" -> AspectRatio.RATIO_21_9
            else -> AspectRatio.AUTO
        }
        return {
            copy(
                preferredPlayerType = agg.playback.preferredPlayer,
                uiPrefs = uiPrefs.copy(
                    defaultOrientation = agg.videoPlayer.videoDefaultOrientation,
                    controlsTimeoutMs = agg.videoPlayer.videoControlsTimeoutMs,
                    skipPromptDurationMs = agg.videoPlayer.skipPromptDurationMs,
                    hideOsdOnPause = agg.videoPlayer.videoHideOsdOnPause,
                    passOutProtectionHours = agg.videoPlayer.videoPassOutProtectionHours,
                    trickplayEnabled = agg.videoPlayer.trickplayEnabled,
                    trickplayOnSeekGesture = agg.videoPlayer.trickplayOnSeekGesture,
                    showPlaybackMetadata = agg.videoPlayer.videoShowPlaybackMetadata,
                    showClock = agg.videoPlayer.showClockInPlayer,
                    showTimeRemaining = agg.videoPlayer.showTimeRemaining,
                    keepScreenOnDuringVideo = agg.playback.keepScreenOnDuringVideo,
                    streamingQuality = agg.playback.streamingQuality,
                    adaptiveBitrateEnabled = adaptiveBitrateEnabled,
                    playbackMode = agg.playback.playbackMode,
                    trickplayMode = agg.videoPlayer.trickplayMode,
                    mpvToneMappingCurve = agg.videoPlayer.mpvToneMappingCurve,
                    mpvDebandMode = agg.videoPlayer.mpvDebandMode,
                    mpvMotionInterpolation = agg.videoPlayer.mpvMotionInterpolation,
                    bitmapSubtitleScaleMode = agg.videoPlayer.bitmapSubtitleScaleMode,
                    mpvCustomButtons = agg.videoPlayer.mpvCustomButtons,
                    pauseScreensaverMode = agg.videoPlayer.pauseScreensaverMode,
                    pauseScreensaverAfterMinutes = agg.videoPlayer.pauseScreensaverAfterMinutes,
                    ambientBlurEnabled = agg.videoPlayer.ambientBlurEnabled,
                    pauseDetailsEnabled = agg.videoPlayer.pauseDetailsEnabled,
                    nextUpButtonPrerollSec = agg.videoPlayer.nextUpButtonPrerollSec,
                    autoplayEpisodeLimit = agg.videoPlayer.autoplayEpisodeLimit,
                    showNextEpisodeDetailsAfterFinish = agg.videoPlayer.showNextEpisodeDetailsAfterFinish,
                ),
                gestures = gestures.copy(
                    gestureMode = agg.videoPlayer.videoGestureMode,
                    holdSpeedEnabled = agg.videoPlayer.videoHoldSpeedEnabled,
                    holdSpeedMultiplier = agg.videoPlayer.videoHoldSpeedMultiplier,
                    defaultSpeed = agg.videoPlayer.videoDefaultSpeed,
                    swipeSeekMaxMs = agg.videoPlayer.videoSwipeSeekMaxMs,
                    seekDurationMs = agg.videoPlayer.videoSeekDurationMs,
                    doubleTapHoldSeekEnabled = agg.videoPlayer.videoDoubleTapHoldSeekEnabled,
                    rememberBrightness = agg.videoPlayer.videoRememberBrightness,
                    brightnessLevel = agg.videoPlayer.videoBrightnessLevel,
                    gestureIndicatorSide = agg.videoPlayer.videoGestureIndicatorSide,
                    frameRateMatching = agg.playback.frameRateMatching,
                    refreshRateMode = agg.playback.refreshRateMode,
                    inputMap = agg.videoPlayer.videoInputBindings,
                ),
                videoFx = videoFx.copy(
                    aspectRatio = defaultAspectRatio,
                    tvZoomModePercent = agg.videoPlayer.tvZoomModePercent,
                ),
                segmentState = segmentState.copy(
                    segmentBehaviors = run {
                        val base = agg.videoPlayer.segmentBehaviors.toMutableMap()
                        if (agg.videoPlayer.videoAutoSkipIntro) {
                            base[MediaSegmentType.INTRO] = SegmentBehavior.AUTO_SKIP
                        }
                        if (agg.videoPlayer.videoAutoSkipOutro) {
                            base[MediaSegmentType.OUTRO] = SegmentBehavior.AUTO_SKIP
                        }
                        base.toMap()
                    },
                    segmentSkipDelaysMs = agg.videoPlayer.segmentSkipDelaysMs,
                ),
                episodes = episodes.copy(
                    videoEpisodeBrowserEnabled = agg.videoPlayer.videoEpisodeBrowserEnabled,
                ),
                autoplay = autoplay.copy(
                    videoAutoplayNext = agg.videoPlayer.videoAutoplayNext,
                    autoPlayCountdownSec = agg.playback.autoPlayCountdownSec,
                ),
            )
        }
    }
}
