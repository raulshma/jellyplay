package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import com.raulshma.jellyplay.feature.player.video.state.AutoplayState
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState

/**
 * Lightweight description of an in-progress pre-roll intro for Cinema Mode.
 * Surfaced to the UI to render a "Skip Intro" affordance.
 */
@Immutable
data class CinemaIntroUiState(
    val title: String,
    val currentIndex: Int,
    val totalCount: Int,
)

/**
 * Narrow, low-frequency view of the segment / up-next overlays.
 *
 * Derived on the ViewModel by combining the high-frequency position flow with
 * the (low-frequency) [VideoPlayerUiState]. It only re-emits when one of these
 * values actually changes, so collecting it at the root of [VideoPlayerScreen]
 * does NOT trigger the 4 Hz recomposition driven by playback position ticks —
 * the position itself is now delivered through dedicated StateFlows and read
     * only inside the composables that render it.
 */
@Immutable
data class SegmentOverlayState(
    val activeSegment: MediaSegment? = null,
    val activeSegmentBehavior: SegmentBehavior = SegmentBehavior.IGNORE,
    val isInIntro: Boolean = false,
    val isInCredits: Boolean = false,
    val shouldShowUpNext: Boolean = false,
)

/**
 * Session + prefs-mirror state for the video player.
 *
 * The sleep-timer, track, subtitle-workflow, audio-effects and SyncPlay
 * group-display slices used to live here as flat fields; they are now owned by
 * their controllers and exposed as `StateFlow`s on the ViewModel. What remains is
 * session state (item identity, transport, engine, surface flags, the
 * subtitle-preview trio, per-item resolver-driven dialogue boost) plus the
 * preferences mirror written by [SettingsProjector].
 */
@Immutable
data class VideoPlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /**
     * True during the initial media load (detail fetch + engine init), before
     * the seek bar's position/duration are seeded. The screen renders a
     * full-screen loading overlay while this is true so the bar's first paint
     * is already at the correct (resume) fraction — no 0→resume flicker.
     * Lifted once the load completes; [isBuffering] covers mid-playback stalls.
     */
    val isInitializing: Boolean = true,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val chapters: List<ChapterInfo> = emptyList(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val preferredPlayerType: PlayerType = PlayerType.EXO_PLAYER,
    /**
     * Player UI / system preferences: control visibility, orientation,
     * pass-out, trickplay, streaming quality/mode and lock/PIN. Formerly flat
     * fields (`defaultOrientation` / `controlsTimeoutMs` /
     * `passOutProtectionHours` / `showVideoStats` / `showPlaybackMetadata` /
     * `showClock` / `showTimeRemaining` / `keepScreenOnDuringVideo` /
     * `usePinForPlayerLock` / `hasPin` / `trickplayEnabled` /
     * `trickplayOnSeekGesture` / `trickplayInfo` / `streamingQuality` /
     * `adaptiveBitrateEnabled` / `playbackMode`); now a stored slice. The
     * error fields and [preferredPlayerType] deliberately stay flat.
     */
    val uiPrefs: PlayerUiPrefsState = PlayerUiPrefsState(),
    /**
     * Gesture / hold-speed / seek-window / brightness / frame-rate prefs.
     * Formerly flat fields (`gesturesEnabled` / `holdSpeedEnabled` /
     * `holdSpeedMultiplier` / `isHoldSpeedActive` / `defaultSpeed` /
     * `swipeSeekMaxMs` / `seekDurationMs` / `rememberBrightness` /
     * `brightnessLevel` / `gestureIndicatorSide` / `frameRateMatching` /
     * `refreshRateMode`); now a stored slice.
     */
    val gestures: GesturePrefsState = GesturePrefsState(),
    /**
     * Raw segment data + per-type behaviors. Formerly flat fields
     * (`segments` / `segmentBehaviors`); now a stored slice.
     */
    val segmentState: SegmentState = SegmentState(),
    val isInSyncPlaySession: Boolean = false,
    val engineCapabilities: EngineCapabilities = EngineCapabilities(),
    /**
     * Parsed cue list for the active subtitle track, for the G10 subtitle-sync
     * preview. Sourced from either an external text track (full track, all
     * engines, bidirectional) or, as a fallback for embedded subs, the engine's
     * live `currentCues` accumulation (played range only). Null when neither
     * source has cues (image subs, unsupported engines).
     */
    val subtitlePreviewCues: List<com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue>? = null,
    /**
     * Which source populated [subtitlePreviewCues], so the preview UI can show
     * the right hint (external = full track; embedded = played range only).
     */
    val subtitlePreviewSource: SubtitlePreviewSource = SubtitlePreviewSource.NONE,
    /**
     * Whether the AV-sync sheet (the only consumer of [subtitlePreviewCues]) is
     * open. The ViewModel uses this to gate pushing embedded-subtitle cues into
     * `subtitlePreviewCues` — there's no point copying the wide UI state on
     * every onCues tick when the preview isn't visible. Toggled by the screen
     * as the sheet opens/dismisses; `loadActiveSubtitleCues` re-syncs on open.
     */
    val previewSheetVisible: Boolean = false,
    val playerError: String? = null,
    /**
     * Structured retryability verdict paired with [playerError], propagated
     * from [com.raulshma.jellyplay.feature.player.video.engine.EngineError.retryable].
     * When `false` the error is fatal on this engine (decoder/DRM) and the
     * error dialog should offer switch-engine, not same-engine retry. The
     * bare `playerError: String` is retained as the rendered message; this
     * flag is the structured signal the legacy String channel dropped.
     */
    val playerErrorRetryable: Boolean = false,
    val bufferedPosition: Long = 0L,
    val videoStats: EngineVideoStats = EngineVideoStats(),
    val showPlaybackErrorDialog: Boolean = false,
    val isScreenLocked: Boolean = false,
    val cinemaIntroState: CinemaIntroUiState? = null,
    val isMuted: Boolean = false,
    /**
     * Media-content metadata: what's playing (overview, cast, artwork,
     * lyrics), how (play method, direct-play flag, transcode reasons), the
     * raw source/stream model, and the series pointer. Formerly flat fields
     * (`overview` / `people` / `artworkUrl` / `lyricsLines` / `streamUrl` /
     * `currentMediaSource` / `mediaStreams` / `playMethod` /
     * `isDirectPlayForced` / `seriesId` / `transcodeReasons`); now a stored
     * slice. [chapters] deliberately stays flat — it feeds both the media
     * display and the segment calculation.
     */
    val media: MediaContentState = MediaContentState(),
    /**
     * Auto-play-next-episode settings + the user's cancellation of the current
     * countdown. Formerly flat fields (`videoAutoplayNext` /
     * `autoPlayCountdownSec` / `autoplayCancelled`); now a stored slice.
     */
    val autoplay: AutoplayState = AutoplayState(),
    /**
     * Video filter / aspect / zoom settings. Formerly flat fields
     * (`videoEffects` / `aspectRatio` / `detectedAspectRatio` /
     * `tvZoomModePercent`); now a stored slice.
     */
    val videoFx: VideoFxState = VideoFxState(),
    /**
     * Series/season/episode browser state: adjacent-episode pointers, the
     * season/episode lists backing the episode-picker sheet, and the browser
     * feature toggle. Formerly flat fields (`nextEpisode` / `previousEpisode` /
     * `seriesSeasons` / `seasonEpisodes` / `currentSeasonId` /
     * `isLoadingEpisodes` / `videoEpisodeBrowserEnabled`); now a stored slice.
     */
    val episodes: EpisodeBrowserState = EpisodeBrowserState(),
    /**
     * Whether the active network is metered (cellular or metered Wi-Fi).
     * Surfaced so the playback metadata can show why a quality cap is being
     * applied (see AdaptiveBitrateManager.MAX_BITRATE_METERED) — a metered link
     * silently transcodes high-bitrate sources even on AUTO quality.
     */
    val isConnectionMetered: Boolean = false,
    /**
     * In-player "Audio only" toggle: hides the video surface (see the
     * AndroidView mount in VideoPlayerScreen) while keeping playback alive via
     * the existing media session. ExoPlayer audio is independent of the surface,
     * so collapsing the view does not interrupt the engine. Purely a UI/surface
     * gate — does NOT touch the engine or PlayerLifecycleManager.
     */
    val audioOnly: Boolean = false,
) {

    // ── Cohesive sub-state projections ────────────────────────────────────
    // Each groups the flat constructor fields by concern.
    // New code should read these (e.g. `state.gestures.brightnessLevel`); the
    // flat fields remain on the root for incremental call-site migration and
    // will be deprecated once all consumers move over.
    //
    // These are derived `val`s (computed per access) rather than stored
    // properties, so the data class's equality/hashCode still reflect the flat
    // fields — the source of truth during the transition.
    //
    // The media slice (`state.media.…`) used to be a projection here too; it
    // is now a STORED constructor field. The gestures and uiPrefs slices
    // likewise migrated from projections to stored fields. The sleep-timer,
    // track, subtitle-workflow, audio-effects and SyncPlay group-display
    // slices used to have projections here as well; they now live in their
    // owning controllers (`SleepTimerController.state`,
    // `TrackSelectionHelper.state`, `SubtitleManager.state`,
    // `VideoEffectsController.state`, `SyncPlayBridge.state`).

    fun behaviorForType(type: MediaSegmentType): SegmentBehavior =
        SegmentCalculator.behaviorForType(toSegmentInput(), type)

    /**
     * Pure (side-effect-free) derivation of the active segment at
     * [currentPosition]. Previously this method mutated `@Transient var`
     * cache fields declared on this `@Immutable` data class — that broke
     * Compose's stability contract (the caches were invisible to
     * `equals`/`hashCode`, so a cache-only change could suppress state
     * propagation, and mutation happened mid-composition via the
     * [activeSegment] getter). The cache optimisation was micro at best
     * (typical segment/chapter lists are tiny) and the callers already read
     * [activeSegment] once per recomposition into a local `val`, so the
     * recomputation cost is negligible. Kept as a `fun` (not a `val`) so it
     * is only evaluated when actually needed.
     */
    fun computeActiveSegment(): MediaSegment? =
        SegmentCalculator.computeActiveSegment(toSegmentInput(), currentPosition)

    /**
     * Position-explicit overload. Lets high-frequency callers (e.g. the
     * position-tick auto-skip check) compute the active segment for a raw
     * engine position WITHOUT copying this state bag (which is the
     * highest-frequency avoidable allocation on the playback path).
     */
    fun computeActiveSegment(positionMs: Long): MediaSegment? =
        SegmentCalculator.computeActiveSegment(toSegmentInput(), positionMs)

    private fun toSegmentInput() = SegmentCalculatorInput(
        segments = segmentState.segments,
        chapters = chapters,
        segmentBehaviors = segmentState.segmentBehaviors,
        durationMs = duration,
        autoplayCancelled = autoplay.autoplayCancelled,
        isInSyncPlaySession = isInSyncPlaySession,
        hasNextEpisode = episodes.nextEpisode != null,
        seriesId = media.seriesId,
    )

    val activeSegment: MediaSegment?
        get() = computeActiveSegment()

    val isInIntro: Boolean
        get() = isInSegmentType(MediaSegmentType.INTRO)

    val isInCredits: Boolean
        get() = isInSegmentType(MediaSegmentType.OUTRO)

    fun isInSegmentType(type: MediaSegmentType): Boolean =
        SegmentCalculator.isInSegmentType(toSegmentInput(), currentPosition, type)

    val introSegmentEndTicks: Long?
        get() = segmentEndTicksForType(MediaSegmentType.INTRO)

    val creditSegmentEndTicks: Long?
        get() = segmentEndTicksForType(MediaSegmentType.OUTRO)

    fun segmentEndTicksForType(type: MediaSegmentType): Long? =
        SegmentCalculator.segmentEndTicksForType(toSegmentInput(), currentPosition, type)

    fun segmentEndTicks(segment: MediaSegment): Long? =
        SegmentCalculator.segmentEndTicks(toSegmentInput(), segment)

    val shouldShowUpNext: Boolean
        get() = SegmentCalculator.shouldShowUpNext(toSegmentInput(), currentPosition)

    val isOutroNearEnd: Boolean
        get() = SegmentCalculator.isOutroNearEnd(toSegmentInput(), currentPosition)

    // Derived media display values: delegated to the stored slice (the single
    // derivation home — see MediaContentState.hdrType / videoFrameRate).
    val hdrType: String?
        get() = media.hdrType

    val videoFrameRate: Float?
        get() = media.videoFrameRate
}

/**
 * Origin of the subtitle-sync preview's cue list. [EXTERNAL] is the full parsed
 * track (bidirectional offset preview); [EMBEDDED] is the engine's live
 * accumulation (played range only); [NONE] means no cues are available.
 */
enum class SubtitlePreviewSource {
    NONE,
    EXTERNAL,
    EMBEDDED,
}
