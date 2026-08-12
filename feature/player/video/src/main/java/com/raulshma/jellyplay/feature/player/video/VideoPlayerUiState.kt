package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import com.raulshma.jellyplay.feature.player.video.state.AutoplayState
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.MediaContentState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.SleepTimerState
import com.raulshma.jellyplay.feature.player.video.state.SubtitleState
import com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState
import com.raulshma.jellyplay.feature.player.video.state.TrackState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
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

@Immutable
data class VideoPlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<TrackOption> = emptyList(),
    val hasAudioOverride: Boolean = false,
    val hasSubtitleOverride: Boolean = false,
    /** A per-series audio-language preference exists for the current series. */
    val hasSeriesAudioPref: Boolean = false,
    /** A per-series subtitle preference (language or "off") exists for the current series. */
    val hasSeriesSubtitlePref: Boolean = false,
    /** A per-series "subtitles off" preference exists for the current series. */
    val hasSeriesSubtitleOffPref: Boolean = false,
    /** A per-series dialogue-boost preference exists for the current series. */
    val hasSeriesDialogueBoostPref: Boolean = false,
    val overview: String = "",
    val people: List<PersonInfo> = emptyList(),
    val artworkUrl: String? = null,
    val lyricsLines: List<LyricsLine> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val aspectRatio: AspectRatio = AspectRatio.AUTO,
    val detectedAspectRatio: AspectRatio? = null,
    val playMethod: String = "",
    val isDirectPlayForced: Boolean = false,
    val subtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioPassthrough: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val remoteSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isLoadingRemoteSubtitles: Boolean = false,
    // Subtitle Manager: language cultures for the upload/search
    // dropdowns, and the language-scoped search results. Kept separate from
    // [remoteSubtitles] (Download tab) so the two lists never clobber each
    // other across tab switches.
    val subtitleCultures: List<CultureInfo> = emptyList(),
    val searchedSubtitles: List<RemoteSubtitleInfo> = emptyList(),
    val isSearchingSubtitles: Boolean = false,
    val hasSearchedSubtitles: Boolean = false,
    /** Non-null when the last subtitle search failed (network/server). Lets the
     *  Search tab distinguish a failure from a genuine empty result. */
    val subtitleSearchError: String? = null,
    /** Per-subtitle-id download status for the "Get Subtitles" sheet (Download +
     *  Search tabs). The sheet stays open after a pick and renders a per-row
     *  spinner / ✓-Downloaded / "taking a while" / failed state keyed by this map,
     *  instead of the former single global boolean that closed the panel on pick.
     *  See [SubtitleManager.downloadSubtitle] and [SubtitleDownloadStatus]. */
    val downloadingSubtitles: Map<String, SubtitleDownloadStatus> = emptyMap(),
    val isUploadingSubtitle: Boolean = false,
    val defaultSearchLanguage: String = "eng",
    /** Merged cross-provider search results (Jellyfin + Wyzie + OpenSubtitles).
     *  Populated by [SubtitleManager.searchAllProviders]; rendered in the Search
     *  tab with provider filter chips. Kept alongside [searchedSubtitles] (the
     *  legacy Jellyfin-only list) so the two code paths don't interfere. */
    val providerSearchResults: List<com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult> = emptyList(),
    /** Per-provider failure message from the last multi-provider search; surfaced
     *  as a chip on the affected provider's filter so one bad key is visible. */
    val providerSearchErrors: Map<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind, String> = emptyMap(),
    /** Providers the user has configured (Jellyfin always present). Drives chip
     *  visibility in the Search tab. */
    val configuredSubtitleProviders: Set<com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind> = emptySet(),
    val syncPlayGroupName: String? = null,
    val syncPlayParticipantCount: Int = 0,
    val isSyncPlaySynced: Boolean = false,
    val isSyncPlaySyncing: Boolean = false,
    val syncPlayRepeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
    val syncPlayShuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
    val nextEpisode: JellyfinMediaItem? = null,
    val previousEpisode: JellyfinMediaItem? = null,
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
    val audioDelayMs: Long = 0L,
    val segments: List<MediaSegment> = emptyList(),
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
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
    val seriesSeasons: List<JellyfinMediaItem> = emptyList(),
    val seasonEpisodes: List<JellyfinMediaItem> = emptyList(),
    val currentSeasonId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val bufferedPosition: Long = 0L,
    val showVideoStats: Boolean = false,
    val videoStats: EngineVideoStats = EngineVideoStats(),
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val adaptiveBitrateEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val showPlaybackErrorDialog: Boolean = false,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val sleepTimerActive: Boolean = false,
    val sleepTimerEndOfEpisode: Boolean = false,
    val sleepTimerLastUsedDurationMs: Long = 0L,
    /** A/B repeat window (G2). Inert unless [AbRepeatState.isActive]. */
    val abRepeat: AbRepeatState = AbRepeatState(),
    val videoEffects: VideoEffectsConfig = VideoEffectsConfig(),
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
    val tvZoomModePercent: Float = 0f,
    val keepScreenOnDuringVideo: Boolean = true,
    val cinemaIntroState: CinemaIntroUiState? = null,
    val isMuted: Boolean = false,
    val videoAutoplayNext: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val autoplayCancelled: Boolean = false,
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

    val media: MediaContentState
        get() = MediaContentState(
            overview = overview, people = people, artworkUrl = artworkUrl,
            lyricsLines = lyricsLines, streamUrl = streamUrl,
            currentMediaSource = currentMediaSource, mediaStreams = mediaStreams,
            playMethod = playMethod, isDirectPlayForced = isDirectPlayForced,
            seriesId = seriesId,
        )

    val tracks: TrackState
        get() = TrackState(
            audioTracks = audioTracks, subtitleTracks = subtitleTracks,
            hasAudioOverride = hasAudioOverride, hasSubtitleOverride = hasSubtitleOverride,
            hasSeriesAudioPref = hasSeriesAudioPref,
            hasSeriesSubtitlePref = hasSeriesSubtitlePref,
            hasSeriesSubtitleOffPref = hasSeriesSubtitleOffPref,
            hasSeriesDialogueBoostPref = hasSeriesDialogueBoostPref,
        )

    val subtitles: SubtitleState
        get() = SubtitleState(
            subtitleStyle = subtitleStyle, remoteSubtitles = remoteSubtitles,
            subtitleCultures = subtitleCultures, searchedSubtitles = searchedSubtitles,
            isSearchingSubtitles = isSearchingSubtitles,
            hasSearchedSubtitles = hasSearchedSubtitles,
            subtitleSearchError = subtitleSearchError,
            isUploadingSubtitle = isUploadingSubtitle,
            isLoadingRemoteSubtitles = isLoadingRemoteSubtitles,
            defaultSearchLanguage = defaultSearchLanguage,
            downloadingSubtitles = downloadingSubtitles,
            providerSearchResults = providerSearchResults,
            providerSearchErrors = providerSearchErrors,
            configuredSubtitleProviders = configuredSubtitleProviders,
        )

    val effects: AudioEffectsState
        get() = AudioEffectsState(
            dialogueBoostEnabled = dialogueBoostEnabled,
            dialogueBoostStrength = dialogueBoostStrength,
            nightModeEnabled = nightModeEnabled, nightModeStrength = nightModeStrength,
            audioPassthrough = audioPassthrough, decoderMode = decoderMode,
            audioNormalizationMode = audioNormalizationMode,
            audioNormalizationEnabled = audioNormalizationEnabled,
            channelMixMode = channelMixMode, channelMixEnabled = channelMixEnabled,
            bassBoostEnabled = bassBoostEnabled, bassBoostStrength = bassBoostStrength,
            virtualizerEnabled = virtualizerEnabled,
            virtualizerStrength = virtualizerStrength,
            reverbPreset = reverbPreset, audioDelayMs = audioDelayMs,
        )

    val videoFx: VideoFxState
        get() = VideoFxState(
            videoEffects = videoEffects, aspectRatio = aspectRatio,
            detectedAspectRatio = detectedAspectRatio,
            tvZoomModePercent = tvZoomModePercent,
        )

    val segmentState: SegmentState
        get() = SegmentState(segments = segments, segmentBehaviors = segmentBehaviors)

    val episodes: EpisodeBrowserState
        get() = EpisodeBrowserState(
            nextEpisode = nextEpisode, seriesSeasons = seriesSeasons,
            seasonEpisodes = seasonEpisodes, currentSeasonId = currentSeasonId,
            isLoadingEpisodes = isLoadingEpisodes,
            videoEpisodeBrowserEnabled = videoEpisodeBrowserEnabled,
        )

    val autoplay: AutoplayState
        get() = AutoplayState(
            videoAutoplayNext = videoAutoplayNext,
            autoPlayCountdownSec = autoPlayCountdownSec,
            autoplayCancelled = autoplayCancelled,
        )

    val syncPlay: SyncPlayUiState
        get() = SyncPlayUiState(
            isInSyncPlaySession = isInSyncPlaySession,
            syncPlayGroupName = syncPlayGroupName,
            syncPlayParticipantCount = syncPlayParticipantCount,
            isSyncPlaySynced = isSyncPlaySynced,
            isSyncPlaySyncing = isSyncPlaySyncing,
            syncPlayRepeatMode = syncPlayRepeatMode,
            syncPlayShuffleMode = syncPlayShuffleMode,
        )

    val sleepTimer: SleepTimerState
        get() = SleepTimerState(
            sleepTimerActive = sleepTimerActive,
            sleepTimerEndOfEpisode = sleepTimerEndOfEpisode,
            sleepTimerLastUsedDurationMs = sleepTimerLastUsedDurationMs,
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
     * engine position WITHOUT copying this 95-field state (which is the
     * highest-frequency avoidable allocation on the playback path).
     */
    fun computeActiveSegment(positionMs: Long): MediaSegment? =
        SegmentCalculator.computeActiveSegment(toSegmentInput(), positionMs)

    private fun toSegmentInput() = SegmentCalculatorInput(
        segments = segments,
        chapters = chapters,
        segmentBehaviors = segmentBehaviors,
        durationMs = duration,
        autoplayCancelled = autoplayCancelled,
        isInSyncPlaySession = isInSyncPlaySession,
        hasNextEpisode = nextEpisode != null,
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
