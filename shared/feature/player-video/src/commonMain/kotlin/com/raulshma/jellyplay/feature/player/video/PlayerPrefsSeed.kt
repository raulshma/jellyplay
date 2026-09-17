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
 * Leaf inventory (30, grouped by destination slice):
 *  - root: `preferredPlayerType`
 *  - `uiPrefs`: defaultOrientation, controlsTimeoutMs, passOutProtectionHours,
 *    trickplayEnabled, trickplayOnSeekGesture, showPlaybackMetadata, showClock,
 *    showTimeRemaining, keepScreenOnDuringVideo, streamingQuality,
 *    adaptiveBitrateEnabled, playbackMode
 *  - `gestures`: gesturesEnabled, holdSpeedEnabled, holdSpeedMultiplier,
 *    defaultSpeed, swipeSeekMaxMs, seekDurationMs, rememberBrightness,
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
 * passOutProtectionHours, autoPlayCountdownSec overlap) re-projected per
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
                ),
                gestures = gestures.copy(
                    gesturesEnabled = agg.videoPlayer.videoGesturesEnabled,
                    holdSpeedEnabled = agg.videoPlayer.videoHoldSpeedEnabled,
                    holdSpeedMultiplier = agg.videoPlayer.videoHoldSpeedMultiplier,
                    defaultSpeed = agg.videoPlayer.videoDefaultSpeed,
                    swipeSeekMaxMs = agg.videoPlayer.videoSwipeSeekMaxMs,
                    seekDurationMs = agg.videoPlayer.videoSeekDurationMs,
                    rememberBrightness = agg.videoPlayer.videoRememberBrightness,
                    brightnessLevel = agg.videoPlayer.videoBrightnessLevel,
                    gestureIndicatorSide = agg.videoPlayer.videoGestureIndicatorSide,
                    frameRateMatching = agg.playback.frameRateMatching,
                    refreshRateMode = agg.playback.refreshRateMode,
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
