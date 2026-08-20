package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrickplayInfo
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
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.LyricsLine

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
    val overview: String = "",
    val people: List<PersonInfo> = emptyList(),
    val artworkUrl: String? = null,
    val lyricsLines: List<LyricsLine> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val playMethod: String = "",
    /** Raw server transcode reasons for the current stream; empty when
     *  direct playing. Formatted for display at the call site via
     *  [com.raulshma.jellyplay.core.ui.player.TranscodeReasonsFormatter]. */
    val transcodeReasons: List<String> = emptyList(),
    val isDirectPlayForced: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val streamUrl: String? = null,
    val preferredPlayerType: PlayerType = PlayerType.EXO_PLAYER,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val seekDurationMs: Long = 10_000L,
    val defaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val controlsTimeoutMs: Long = 5_000L,
    val passOutProtectionHours: Int = 0,
    val gesturesEnabled: Boolean = true,
    val holdSpeedEnabled: Boolean = true,
    val holdSpeedMultiplier: Float = 2.0f,
    val isHoldSpeedActive: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val swipeSeekMaxMs: Long = 120_000L,
    val rememberBrightness: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val gestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: com.raulshma.jellyplay.core.model.RefreshRateMode = com.raulshma.jellyplay.core.model.RefreshRateMode.OFF,
    /**
     * Raw segment data + per-type behaviors. Formerly flat fields
     * (`segments` / `segmentBehaviors`); now a stored slice.
     */
    val segmentState: SegmentState = SegmentState(),
    val seriesId: String? = null,
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
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val trickplayInfo: TrickplayInfo? = null,
    val bufferedPosition: Long = 0L,
    val showVideoStats: Boolean = false,
    val videoStats: EngineVideoStats = EngineVideoStats(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val adaptiveBitrateEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val showPlaybackErrorDialog: Boolean = false,
    val isScreenLocked: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    /**
     * Presence flag for the player-lock PIN. The hash itself never
     * leaves the VM/prefs — surfacing it through per-frame UiState churned
     * state identity on PIN change and exposed the hash to equals/hashCode/
     * toString (log risk). Callers only ever gate on presence.
     */
    val hasPin: Boolean = false,
    val showPlaybackMetadata: Boolean = true,
    val showClock: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val keepScreenOnDuringVideo: Boolean = true,
    val cinemaIntroState: CinemaIntroUiState? = null,
    val isMuted: Boolean = false,
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
    // New code should read these (e.g. `state.media.isLive`); the flat fields
    // remain on the root for incremental call-site migration and will be
    // deprecated once all consumers move over.
    //
    // These are derived `val`s (computed per access) rather than stored
    // properties, so the data class's equality/hashCode still reflect the flat
    // fields — the source of truth during the transition.
    //
    // The sleep-timer, track, subtitle-workflow, audio-effects and SyncPlay
    // group-display slices used to have projections here too; they now live in
    // their owning controllers (`SleepTimerController.state`,
    // `TrackSelectionHelper.state`, `SubtitleManager.state`,
    // `VideoEffectsController.state`, `SyncPlayBridge.state`).

    val media: MediaContentState
        get() = MediaContentState(
            overview = overview, people = people, artworkUrl = artworkUrl,
            lyricsLines = lyricsLines, streamUrl = streamUrl,
            currentMediaSource = currentMediaSource, mediaStreams = mediaStreams,
            playMethod = playMethod, isDirectPlayForced = isDirectPlayForced,
            seriesId = seriesId,
        )

    val gestures: GesturePrefsState
        get() = GesturePrefsState(
            gesturesEnabled = gesturesEnabled,
            holdSpeedEnabled = holdSpeedEnabled,
            holdSpeedMultiplier = holdSpeedMultiplier,
            isHoldSpeedActive = isHoldSpeedActive,
            defaultSpeed = defaultSpeed, swipeSeekMaxMs = swipeSeekMaxMs,
            seekDurationMs = seekDurationMs, rememberBrightness = rememberBrightness,
            brightnessLevel = brightnessLevel, gestureIndicatorSide = gestureIndicatorSide,
            frameRateMatching = frameRateMatching,
            refreshRateMode = refreshRateMode,
        )

    val uiPrefs: PlayerUiPrefsState
        get() = PlayerUiPrefsState(
            controlsTimeoutMs = controlsTimeoutMs,
            defaultOrientation = defaultOrientation,
            passOutProtectionHours = passOutProtectionHours,
            showVideoStats = showVideoStats,
            showPlaybackMetadata = showPlaybackMetadata,
            showClock = showClock, showTimeRemaining = showTimeRemaining,
            keepScreenOnDuringVideo = keepScreenOnDuringVideo,
            usePinForPlayerLock = usePinForPlayerLock, hasPin = hasPin,
            trickplayEnabled = trickplayEnabled,
            trickplayOnSeekGesture = trickplayOnSeekGesture,
            trickplayInfo = trickplayInfo,
            streamingQuality = streamingQuality,
            adaptiveBitrateEnabled = adaptiveBitrateEnabled,
            playbackMode = playbackMode,
        )

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
        seriesId = seriesId,
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

    val hdrType: String?
        get() {
            val videoStream = mediaStreams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
            val range = videoStream.videoRange ?: return null
            return if (range.equals("SDR", ignoreCase = true)) null else range
        }

    val videoFrameRate: Float?
        get() = mediaStreams.firstOrNull { it.type == StreamType.VIDEO }?.realFrameRate
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
