package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * ONE home for "which preference feeds which [VideoPlayerUiState] leaf" — the
 * declarative leaf table the three execution strategies read:
 *
 *  - [PlayerPrefsSeed] (load-time): applies EVERY row's write unguarded, one
 *    whole projection per session load;
 *  - [SettingsProjector] (change-time): applies the PROJECTED rows (those
 *    with a state read) with distinct-until-changed guards, one uiState copy
 *    per changed leaf;
 *  - [PlayerPrefsFanout] (reactions): diffs the
 *    [PlayerPrefLeaf.rebuildsEngineConfig] rows to trigger the engine-config
 *    rebuild.
 *
 * Adding a pref→uiState mapping is ONE row here — the seed write, the
 * guarded projection and (when it feeds the engine config) the rebuild
 * trigger all follow; the former "nineteen leaves are mapped in both places,
 * update both or drop one side deliberately" prose sync is gone because
 * there is only one side. Rows whose source is not the aggregate (the
 * network slice's `adaptiveBitrateEnabled`), whose write is not a plain
 * leaf copy (the HDR-aware subtitle-style resolution, the PIN pair's
 * one-copy double write), or whose reaction is not a uiState leaf (the
 * audio-effects / mpvInputConf / HDR-posture diffs, the controller seeds,
 * the autoplay flip, the focus push) stay hand-written in their strategy —
 * see each strategy's KDoc.
 *
 * Plain data + lambdas, no reflection (the PreferenceSpec tier's rule).
 */
internal class PlayerPrefLeaf internal constructor(
    val name: String,
    internal val readAggregateAny: (VideoPlayerAggregate) -> Any?,
    /** The same leaf's read off the current uiState — null marks a seed-only row. */
    internal val readStateAny: ((VideoPlayerUiState) -> Any?)?,
    internal val writeAny: VideoPlayerUiState.(Any?) -> VideoPlayerUiState,
    /** True when a change-time diff on this row must rebuild the engine config. */
    val rebuildsEngineConfig: Boolean,
) {
    /** Change-time rows have a state read; seed-only rows do not. */
    val projected: Boolean get() = readStateAny != null
}

/** The type-checked row factory — each row's read/write pair shares its V. */
private fun <V> prefLeaf(
    name: String,
    readAggregate: (VideoPlayerAggregate) -> V,
    readState: ((VideoPlayerUiState) -> V)? = null,
    write: VideoPlayerUiState.(V) -> VideoPlayerUiState,
    rebuildsEngineConfig: Boolean = false,
): PlayerPrefLeaf = PlayerPrefLeaf(
    name = name,
    readAggregateAny = readAggregate,
    readStateAny = readState,
    writeAny = { v ->
        @Suppress("UNCHECKED_CAST")
        this.write(v as V)
    },
    rebuildsEngineConfig = rebuildsEngineConfig,
)

/** The aspect-ratio pref's parse (verbatim from the former seed body). */
internal fun parseAspectRatioPref(raw: String): AspectRatio = when (raw) {
    "FIT" -> AspectRatio.FIT
    "FILL" -> AspectRatio.FILL
    "CROP" -> AspectRatio.CROP
    "16:9" -> AspectRatio.RATIO_16_9
    "4:3" -> AspectRatio.RATIO_4_3
    "21:9" -> AspectRatio.RATIO_21_9
    else -> AspectRatio.AUTO
}

/** The segment-behaviors fold: the stored map with the INTRO/OUTRO auto-skip flags OR-ed in. */
private fun foldSegmentBehaviors(agg: VideoPlayerAggregate): Map<MediaSegmentType, SegmentBehavior> {
    val base = agg.videoPlayer.segmentBehaviors.toMutableMap()
    if (agg.videoPlayer.videoAutoSkipIntro) {
        base[MediaSegmentType.INTRO] = SegmentBehavior.AUTO_SKIP
    }
    if (agg.videoPlayer.videoAutoSkipOutro) {
        base[MediaSegmentType.OUTRO] = SegmentBehavior.AUTO_SKIP
    }
    return base.toMap()
}

/**
 * The leaf table. Grouped by destination slice, mirroring the seed's former
 * inventory; `projected` rows are the change-time subset, and the four
 * mpv/bitmap render rows carry the engine-config rebuild trigger.
 */
internal val PLAYER_PREF_LEAVES: List<PlayerPrefLeaf> = listOf(

    // ── root ──
    prefLeaf(
        "preferredPlayerType",
        readAggregate = { it.playback.preferredPlayer },
        write = { v -> copy(preferredPlayerType = v) },
    ),

    // ── uiPrefs ──
    prefLeaf("defaultOrientation", { it.videoPlayer.videoDefaultOrientation }, write = { v -> copy(uiPrefs = uiPrefs.copy(defaultOrientation = v)) }),
    prefLeaf("controlsTimeoutMs", { it.videoPlayer.videoControlsTimeoutMs }, write = { v -> copy(uiPrefs = uiPrefs.copy(controlsTimeoutMs = v)) }),
    prefLeaf("skipPromptDurationMs", { it.videoPlayer.skipPromptDurationMs }, write = { v -> copy(uiPrefs = uiPrefs.copy(skipPromptDurationMs = v)) }),
    prefLeaf("hideOsdOnPause", { it.videoPlayer.videoHideOsdOnPause }, write = { v -> copy(uiPrefs = uiPrefs.copy(hideOsdOnPause = v)) }),
    prefLeaf(
        "passOutProtectionHours",
        readAggregate = { it.videoPlayer.videoPassOutProtectionHours },
        readState = { s -> s.uiPrefs.passOutProtectionHours },
        write = { v -> copy(uiPrefs = uiPrefs.copy(passOutProtectionHours = v)) },
    ),
    prefLeaf("trickplayEnabled", { it.videoPlayer.trickplayEnabled }, write = { v -> copy(uiPrefs = uiPrefs.copy(trickplayEnabled = v)) }),
    prefLeaf("trickplayOnSeekGesture", { it.videoPlayer.trickplayOnSeekGesture }, write = { v -> copy(uiPrefs = uiPrefs.copy(trickplayOnSeekGesture = v)) }),
    prefLeaf(
        "showPlaybackMetadata",
        readAggregate = { it.videoPlayer.videoShowPlaybackMetadata },
        readState = { s -> s.uiPrefs.showPlaybackMetadata },
        write = { v -> copy(uiPrefs = uiPrefs.copy(showPlaybackMetadata = v)) },
    ),
    prefLeaf(
        "showClock",
        readAggregate = { it.videoPlayer.showClockInPlayer },
        readState = { s -> s.uiPrefs.showClock },
        write = { v -> copy(uiPrefs = uiPrefs.copy(showClock = v)) },
    ),
    prefLeaf(
        "showTimeRemaining",
        readAggregate = { it.videoPlayer.showTimeRemaining },
        readState = { s -> s.uiPrefs.showTimeRemaining },
        write = { v -> copy(uiPrefs = uiPrefs.copy(showTimeRemaining = v)) },
    ),
    prefLeaf(
        "keepScreenOnDuringVideo",
        readAggregate = { it.playback.keepScreenOnDuringVideo },
        readState = { s -> s.uiPrefs.keepScreenOnDuringVideo },
        write = { v -> copy(uiPrefs = uiPrefs.copy(keepScreenOnDuringVideo = v)) },
    ),
    prefLeaf("streamingQuality", { it.playback.streamingQuality }, write = { v -> copy(uiPrefs = uiPrefs.copy(streamingQuality = v)) }),
    prefLeaf("playbackMode", { it.playback.playbackMode }, write = { v -> copy(uiPrefs = uiPrefs.copy(playbackMode = v)) }),
    prefLeaf(
        "trickplayMode",
        readAggregate = { it.videoPlayer.trickplayMode },
        readState = { s -> s.uiPrefs.trickplayMode },
        write = { v -> copy(uiPrefs = uiPrefs.copy(trickplayMode = v)) },
    ),
    prefLeaf(
        "mpvToneMappingCurve",
        readAggregate = { it.videoPlayer.mpvToneMappingCurve },
        readState = { s -> s.uiPrefs.mpvToneMappingCurve },
        write = { v -> copy(uiPrefs = uiPrefs.copy(mpvToneMappingCurve = v)) },
        rebuildsEngineConfig = true,
    ),
    prefLeaf(
        "mpvDebandMode",
        readAggregate = { it.videoPlayer.mpvDebandMode },
        readState = { s -> s.uiPrefs.mpvDebandMode },
        write = { v -> copy(uiPrefs = uiPrefs.copy(mpvDebandMode = v)) },
        rebuildsEngineConfig = true,
    ),
    prefLeaf(
        "mpvMotionInterpolation",
        readAggregate = { it.videoPlayer.mpvMotionInterpolation },
        readState = { s -> s.uiPrefs.mpvMotionInterpolation },
        write = { v -> copy(uiPrefs = uiPrefs.copy(mpvMotionInterpolation = v)) },
        rebuildsEngineConfig = true,
    ),
    prefLeaf(
        "bitmapSubtitleScaleMode",
        readAggregate = { it.videoPlayer.bitmapSubtitleScaleMode },
        readState = { s -> s.uiPrefs.bitmapSubtitleScaleMode },
        write = { v -> copy(uiPrefs = uiPrefs.copy(bitmapSubtitleScaleMode = v)) },
        rebuildsEngineConfig = true,
    ),
    prefLeaf(
        "mpvCustomButtons",
        readAggregate = { it.videoPlayer.mpvCustomButtons },
        readState = { s -> s.uiPrefs.mpvCustomButtons },
        write = { v -> copy(uiPrefs = uiPrefs.copy(mpvCustomButtons = v)) },
    ),
    prefLeaf(
        "pauseScreensaverMode",
        readAggregate = { it.videoPlayer.pauseScreensaverMode },
        readState = { s -> s.uiPrefs.pauseScreensaverMode },
        write = { v -> copy(uiPrefs = uiPrefs.copy(pauseScreensaverMode = v)) },
    ),
    prefLeaf(
        "pauseScreensaverAfterMinutes",
        readAggregate = { it.videoPlayer.pauseScreensaverAfterMinutes },
        readState = { s -> s.uiPrefs.pauseScreensaverAfterMinutes },
        write = { v -> copy(uiPrefs = uiPrefs.copy(pauseScreensaverAfterMinutes = v)) },
    ),
    prefLeaf(
        "ambientBlurEnabled",
        readAggregate = { it.videoPlayer.ambientBlurEnabled },
        readState = { s -> s.uiPrefs.ambientBlurEnabled },
        write = { v -> copy(uiPrefs = uiPrefs.copy(ambientBlurEnabled = v)) },
    ),
    prefLeaf(
        "pauseDetailsEnabled",
        readAggregate = { it.videoPlayer.pauseDetailsEnabled },
        readState = { s -> s.uiPrefs.pauseDetailsEnabled },
        write = { v -> copy(uiPrefs = uiPrefs.copy(pauseDetailsEnabled = v)) },
    ),
    prefLeaf(
        "nextUpButtonPrerollSec",
        readAggregate = { it.videoPlayer.nextUpButtonPrerollSec },
        readState = { s -> s.uiPrefs.nextUpButtonPrerollSec },
        write = { v -> copy(uiPrefs = uiPrefs.copy(nextUpButtonPrerollSec = v)) },
    ),
    prefLeaf(
        "autoplayEpisodeLimit",
        readAggregate = { it.videoPlayer.autoplayEpisodeLimit },
        readState = { s -> s.uiPrefs.autoplayEpisodeLimit },
        write = { v -> copy(uiPrefs = uiPrefs.copy(autoplayEpisodeLimit = v)) },
    ),
    prefLeaf(
        "showNextEpisodeDetailsAfterFinish",
        readAggregate = { it.videoPlayer.showNextEpisodeDetailsAfterFinish },
        readState = { s -> s.uiPrefs.showNextEpisodeDetailsAfterFinish },
        write = { v -> copy(uiPrefs = uiPrefs.copy(showNextEpisodeDetailsAfterFinish = v)) },
    ),

    // ── gestures ──
    prefLeaf("gestureMode", { it.videoPlayer.videoGestureMode }, write = { v -> copy(gestures = gestures.copy(gestureMode = v)) }),
    prefLeaf("holdSpeedEnabled", { it.videoPlayer.videoHoldSpeedEnabled }, write = { v -> copy(gestures = gestures.copy(holdSpeedEnabled = v)) }),
    prefLeaf("holdSpeedMultiplier", { it.videoPlayer.videoHoldSpeedMultiplier }, write = { v -> copy(gestures = gestures.copy(holdSpeedMultiplier = v)) }),
    prefLeaf("defaultSpeed", { it.videoPlayer.videoDefaultSpeed }, write = { v -> copy(gestures = gestures.copy(defaultSpeed = v)) }),
    prefLeaf("swipeSeekMaxMs", { it.videoPlayer.videoSwipeSeekMaxMs }, write = { v -> copy(gestures = gestures.copy(swipeSeekMaxMs = v)) }),
    prefLeaf("seekDurationMs", { it.videoPlayer.videoSeekDurationMs }, write = { v -> copy(gestures = gestures.copy(seekDurationMs = v)) }),
    prefLeaf("doubleTapHoldSeekEnabled", { it.videoPlayer.videoDoubleTapHoldSeekEnabled }, write = { v -> copy(gestures = gestures.copy(doubleTapHoldSeekEnabled = v)) }),
    prefLeaf("rememberBrightness", { it.videoPlayer.videoRememberBrightness }, write = { v -> copy(gestures = gestures.copy(rememberBrightness = v)) }),
    prefLeaf("brightnessLevel", { it.videoPlayer.videoBrightnessLevel }, write = { v -> copy(gestures = gestures.copy(brightnessLevel = v)) }),
    prefLeaf("gestureIndicatorSide", { it.videoPlayer.videoGestureIndicatorSide }, write = { v -> copy(gestures = gestures.copy(gestureIndicatorSide = v)) }),
    prefLeaf("frameRateMatching", { it.playback.frameRateMatching }, write = { v -> copy(gestures = gestures.copy(frameRateMatching = v)) }),
    prefLeaf("refreshRateMode", { it.playback.refreshRateMode }, write = { v -> copy(gestures = gestures.copy(refreshRateMode = v)) }),
    prefLeaf(
        "inputMap",
        readAggregate = { it.videoPlayer.videoInputBindings },
        readState = { s -> s.gestures.inputMap },
        write = { v -> copy(gestures = gestures.copy(inputMap = v)) },
    ),

    // ── videoFx ──
    prefLeaf(
        "aspectRatio",
        readAggregate = { parseAspectRatioPref(it.videoPlayer.videoDefaultAspectRatio) },
        write = { v -> copy(videoFx = videoFx.copy(aspectRatio = v)) },
    ),
    prefLeaf(
        "tvZoomModePercent",
        readAggregate = { it.videoPlayer.tvZoomModePercent },
        readState = { s -> s.videoFx.tvZoomModePercent },
        write = { v -> copy(videoFx = videoFx.copy(tvZoomModePercent = v)) },
    ),

    // ── segmentState ──
    prefLeaf("segmentBehaviors", ::foldSegmentBehaviors, write = { v -> copy(segmentState = segmentState.copy(segmentBehaviors = v)) }),
    prefLeaf("segmentSkipDelaysMs", { it.videoPlayer.segmentSkipDelaysMs }, write = { v -> copy(segmentState = segmentState.copy(segmentSkipDelaysMs = v)) }),

    // ── episodes ──
    prefLeaf("videoEpisodeBrowserEnabled", { it.videoPlayer.videoEpisodeBrowserEnabled }, write = { v -> copy(episodes = episodes.copy(videoEpisodeBrowserEnabled = v)) }),

    // ── autoplay ──
    prefLeaf("videoAutoplayNext", { it.videoPlayer.videoAutoplayNext }, write = { v -> copy(autoplay = autoplay.copy(videoAutoplayNext = v)) }),
    prefLeaf(
        "autoPlayCountdownSec",
        readAggregate = { it.playback.autoPlayCountdownSec },
        readState = { s -> s.autoplay.autoPlayCountdownSec },
        write = { v -> copy(autoplay = autoplay.copy(autoPlayCountdownSec = v)) },
    ),
)
