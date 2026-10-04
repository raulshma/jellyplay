package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.feature.player.video.PlatformBitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.PlatformCastButton
import com.raulshma.jellyplay.feature.player.video.rememberIsPortraitOrientation
import com.raulshma.jellyplay.core.ui.animation.horizontalFadingEdges
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsExit
import com.raulshma.jellyplay.core.ui.player.playerBottomScrim
import com.raulshma.jellyplay.core.ui.player.playerPlayButtonEnter
import com.raulshma.jellyplay.core.ui.player.playerPlayButtonExit
import com.raulshma.jellyplay.core.ui.player.playerTopControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerTopControlsExit
import com.raulshma.jellyplay.core.ui.player.playerTopScrim
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.components.rememberWallClockTimeString
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_back
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_current_time
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_group
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_lock_screen
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_more_options
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_next_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pause
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_previous_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_rotate_screen


















import com.raulshma.jellyplay.feature.player.video.AbRepeatState
import com.raulshma.jellyplay.feature.player.video.PlayerSheet
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot
import com.raulshma.jellyplay.feature.player.video.TrackOption


/**
 * Effects-family bundle for [PlayerControls] (the VideoEffectsController
 * slice): the dialogue-boost / night-mode / passthrough / normalization /
 * channel-mix values plus their callbacks ride one `@Immutable` carrier, so
 * the controls tree's skippability compares one per-concern equality instead
 * of eighteen flat leaves and the call wall remembers ONE lambda-bearing
 * bundle (the openSheet funnel's rationale, extended to this family).
 */
@Immutable
internal data class PlayerEffectsControls(
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.NONE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.NONE,
    val audioPassthrough: Boolean = false,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val onDialogueBoostClick: () -> Unit = {},
    val onDialogueBoostStrengthChange: (EffectStrength) -> Unit = {},
    val onNightModeClick: () -> Unit = {},
    val onNightModeStrengthChange: (EffectStrength) -> Unit = {},
    val onPassthroughClick: () -> Unit = {},
    val onAudioNormalizationClick: () -> Unit = {},
    val onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    val onChannelMixClick: () -> Unit = {},
    val onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
)

/**
 * Sleep-timer display bundle for [PlayerControls]: the trio the overflow
 * menu's sleep-timer row reads. The click itself routes through the
 * [PlayerControls] `openSheet` funnel (PlayerSheet.SleepTimer), so this is
 * values-only — equality-skippable with no remembered lambdas.
 */
@Immutable
internal data class SleepTimerControls(
    val active: Boolean = false,
    val endOfEpisode: Boolean = false,
    val remainingFlow: StateFlow<Long> = MutableStateFlow(0L),
)

/**
 * SyncPlay header/bottom-bar indicator bundle for [PlayerControls]: the
 * quintet the header pill and the primary-row SyncPlay button render. Like
 * [SleepTimerControls], values-only — clicks go through `openSheet`.
 */
@Immutable
internal data class SyncPlayIndicator(
    val inSession: Boolean = false,
    val groupName: String? = null,
    val participantCount: Int = 0,
    val isSynced: Boolean = false,
    val isSyncing: Boolean = false,
)

/**
 * Transport bundle for [PlayerControls]: the play/pause pair, the seek triple
 * the seek bar dispatches, the episode-stepping pair (+ enablement), the
 * seek-bar trickplay scrub preview, and the mute toggle — the playback
 * transport surface the center cluster and the seek bar render.
 */
@Immutable
internal data class TransportControls(
    val isPlaying: Boolean = false,
    val onPlayPause: () -> Unit = {},
    val onSeekStart: () -> Unit = {},
    val onSeekEnd: () -> Unit = {},
    val onSeekPositionChange: (Long) -> Unit = {},
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val onPreviousEpisode: () -> Unit = {},
    val onNextEpisode: () -> Unit = {},
    val tvTrickplayBitmap: PlatformBitmap? = null,
    val isMuted: Boolean = false,
    val onMuteClick: () -> Unit = {},
)

/**
 * Chrome-interaction bundle for [PlayerControls]: the screen-observed
 * interactions with the controls surface itself (control-row scroll → auto-hide
 * reset, focus tracking, overflow open/close mirroring) plus the chrome
 * actions that are neither transport nor sheet openers (back, lock, PiP,
 * orientation toggle).
 */
@Immutable
internal data class GestureControls(
    val onBack: () -> Unit = {},
    val onLockClick: () -> Unit = {},
    val onPipClick: () -> Unit = {},
    val onToggleOrientation: () -> Unit = {},
    val onControlRowScrolled: () -> Unit = {},
    val onControlsFocusChange: (Boolean) -> Unit = {},
    val onOverflowMenuChange: (Boolean) -> Unit = {},
)

/**
 * Sheet/overflow bundle for [PlayerControls]: the `openSheet` funnel plus every
 * dedicated opener that carries side effects beyond the sheet id (the
 * subtitle-hub pair, the subtitle-delay overlay, the render sheet), the
 * overflow panel's toggle/state wiring (video stats, video filters,
 * screenshot, A/B repeat actions, audio-only, incognito mark-and-exit,
 * render/deinterlace), and the Episodes entry's gates. Everything here either
 * opens a [PlayerSheet]-shaped surface or is dispatched from inside one.
 *
 * The [openSheet] funnel used to be the whole story: every control that opens
 * a PlayerSheet and nothing else routes through it, because ~13 separate
 * no-arg `on*Click` params each had to be remembered at the call site or
 * PlayerControls' skippability broke (a fresh lambda per recomposition forced
 * the ~1500-line controls tree to recompose on every position tick) — one
 * remembered `(PlayerSheet) -> Unit` restores that guarantee structurally
 * (now one remembered [SheetControls] carries it). Openers with side effects
 * beyond the sheet id (the subtitle hub's reset-first flag) stay dedicated
 * members.
 */
@Immutable
internal data class SheetControls(
    val openSheet: (PlayerSheet) -> Unit = {},
    val onSubtitleClick: () -> Unit = {},
    val onSubtitleHubClick: () -> Unit = {},
    /**
     * The playback-metadata row's mpv-config chip: the hub must land on its
     * Style tab (the full ownership notice), so the screen sets the
     * initial-tab intent before opening — beyond what [openSheet] carries.
     */
    val onMpvConfigNoticeClick: () -> Unit = {},
    /**
     * The subtitle-visibility toggle (CC button's long-press): off
     * remembers the last non-Off track in-session, on silently restores it.
     * Short-press keeps opening the hub.
     */
    val onSubtitleToggle: () -> Unit = {},
    val onSubtitleDelayClick: () -> Unit = {},
    val hasEpisodes: Boolean = false,
    val episodeBrowserEnabled: Boolean = true,
    val showVideoStats: Boolean = false,
    val onVideoStatsClick: () -> Unit = {},
    val videoFiltersActive: Boolean = false,
    val onScreenshotClick: () -> Unit = {},
    val onAbRepeatToggle: () -> Unit = {},
    val onAbRepeatSetA: () -> Unit = {},
    val onAbRepeatSetB: () -> Unit = {},
    val onAbRepeatClear: () -> Unit = {},
    val audioOnly: Boolean = false,
    val onToggleAudioOnly: () -> Unit = {},
    val incognitoModeEnabled: Boolean = false,
    val onMarkWatchedAndSkip: () -> Unit = {},
    val onMarkUnwatchedAndQuit: () -> Unit = {},
    val supportsRenderPanel: Boolean = false,
    val onRenderClick: () -> Unit = {},
    val supportsDeinterlace: Boolean = false,
    val deinterlaceMode: com.raulshma.jellyplay.core.model.DeinterlaceMode? = null,
    val onDeinterlaceCycle: () -> Unit = {},
    /** Opens the Version sheet (multi-version items only — the overflow menu gates on the track fact). */
    val onVersionClick: () -> Unit = {},
)

/**
 * Track/metadata bundle for [PlayerControls]: the stream/track facts the
 * playback-metadata row and the quality/audio sheet entries render — stream
 * list, audio tracks, play method (+ forced-direct flag), HDR type, metered
 * connection, subtitle delay, streaming quality, playback mode, the row's
 * visibility toggle.
 */
@Immutable
internal data class TrackControls(
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    val playMethod: String = "Direct Play",
    val isDirectPlayForced: Boolean = false,
    val hdrType: String? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val audioTracks: List<TrackOption> = emptyList(),
    val isConnectionMetered: Boolean = false,
    val subtitleDelayMs: Long = 0L,
    val showPlaybackMetadata: Boolean = true,
    /** The playing item exposes more than one version (media source). */
    val hasMultipleVersions: Boolean = false,
    /**
     * The engine's custom-mpv-config subtitle ownership is active (user-owned
     * `sub-*` keys): the metadata row renders its warning chip, clicking
     * through to the subtitle hub's full notice.
     */
    val mpvConfigNoticeActive: Boolean = false,
)

@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    // High-frequency playback streams collected here so the seek bar /
    // time labels recompose at 4 Hz without invalidating the whole screen.
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    // the range-level buffered surface that drives the seek bar's
    // shaded bands (replaces the single scalar band). Kept defaulted so
    // previews/tests render without one; empty = no shading.
    bufferedRangesFlow: StateFlow<List<LongRange>> = MutableStateFlow(emptyList()),
    videoStatsFlow: StateFlow<EngineVideoStats>,
    playbackSpeed: Float,
    chapters: List<ChapterInfo>,
    effectsControls: PlayerEffectsControls = PlayerEffectsControls(),
    segments: List<MediaSegment> = emptyList(),
    // The four callback/value families (the [PlayerEffectsControls] idiom):
    // transport (play/seek/episodes/trickplay/mute), chrome gestures &
    // interaction reporting, sheet/overflow wiring, and track/metadata facts.
    transport: TransportControls = TransportControls(),
    gestures: GestureControls = GestureControls(),
    sheets: SheetControls = SheetControls(),
    tracks: TrackControls = TrackControls(),
    currentAspectRatio: AspectRatio,
    detectedAspectRatio: AspectRatio?,
    isVisible: Boolean,
    // Passed whole (the SubtitleStyleControls capabilities precedent): the
    // per-engine gates ride the @Immutable EngineCapabilities data class
    // instead of twelve supportsX booleans flattened at every call site.
    capabilities: EngineCapabilities = EngineCapabilities(),
    syncPlay: SyncPlayIndicator = SyncPlayIndicator(),
    sleepTimer: SleepTimerControls = SleepTimerControls(),
    abRepeat: AbRepeatState = AbRepeatState(),
    // Opaque cast-manager handle (seam): the Android host passes the
    // legacy discovery/connect CastManager; desktop passes null and the cast
    // button hides. See VideoPlayerViewModel.platformCastManager.
    castManager: Any? = null,
    showClock: Boolean = false,
    showTimeRemaining: Boolean = false,
    tvSkipSegmentFocusRequester: FocusRequester? = null,
    tvNextEpisodeFocusRequester: FocusRequester? = null,
    isSkipSegmentVisible: Boolean = false,
    isNextEpisodeVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // High-frequency position/buffered streams are collected in the leaf
    // composables that actually need them (TvControllableSeekBar and
    // EndsAtLabel), NOT here. Collecting at the root invalidated this entire
    // ~670-line body on every 4 Hz position tick and was a primary driver of
    // the MPV playback ANR. videoStats is projected through a derivedStateOf
    // below so only the low-churn codec/HDR slice reaches PlaybackMetadataRow;
    // sleepTimerRemainingMs is collected inside PlayerOverflowMenu, its only reader.
    val videoStats by videoStatsFlow.collectAsStateWithLifecycle()
    // Project only the static codec/HDR/audio-channel slice that
    // PlaybackMetadataRow reads. The full EngineVideoStats stream also carries
    // high-churn fields (droppedFrames, bufferedPositionMs, videoBitrate,
    // estimatedBandwidthBps) that tick multiple times per second; without this
    // projection PlaybackMetadataRow would recompose on every tick even though
    // its displayed fields almost never change.
    val playbackMetadata by remember {
        derivedStateOf {
            PlaybackMetadataSnapshot(
                videoCodec = videoStats.videoCodec,
                videoResolution = videoStats.videoResolution,
                videoHdrType = videoStats.videoHdrType,
                audioCodec = videoStats.audioCodec,
                audioChannels = videoStats.audioChannels,
            )
        }
    }

    val isTv = LocalTvMode.current
    val isPortrait = rememberIsPortraitOrientation()
    // In portrait every control lives in one horizontally scrollable row, with
    // PiP, Rotate and the More (⋮) menu pinned at the right edge. Landscape/TV
    // keep the asymmetric layout: a scrolling primary row + fixed right cluster.
    val splitBottomControlsEvenly = !isTv && isPortrait
    // Hoisted scroll state so the fade indicator and horizontalScroll share
    // the same position and stay in sync.
    val bottomLeftScrollState = rememberScrollState()
    val tvPlayPauseFocusRequester = remember { FocusRequester() }
    val tvBackFocusRequester = remember { FocusRequester() }
    val tvSeekbarFocusRequester = remember { FocusRequester() }
    val tvBottomButtonsFocusRequester = remember { FocusRequester() }
    val tvBackFocusState = rememberTvFocusState(focusedScale = 1.08f)

    LaunchedEffect(bottomLeftScrollState) {
        snapshotFlow { bottomLeftScrollState.isScrollInProgress }
            .filter { it }
            .collect { gestures.onControlRowScrolled() }
    }

    // Overflow menu open/close state. Hoisted to the PlayerControls scope (rather
    // than the local button Box) so the in-window panel can be hosted in the root
    // Box below, where it has room for a full-size dismiss interceptor and can be
    // anchored to TopEnd. Rendering it in-window (instead of a DropdownMenu popup)
    // keeps it in the player's immersive window so the system bars never appear.
    var showOverflow by remember { mutableStateOf(false) }
    LaunchedEffect(showOverflow) { gestures.onOverflowMenuChange(showOverflow) }

    LaunchedEffect(isVisible, isTv) {
        if (isTv && isVisible) {
            tvPlayPauseFocusRequester.tryRequestFocus()
        }
    }

    Box(
        modifier = modifier
            .onFocusChanged { focusState ->
                if (isTv) {
                    gestures.onControlsFocusChange(focusState.hasFocus)
                }
            }
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = playerTopControlsEnter(),
            exit = playerTopControlsExit(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (isTv) Modifier.focusRequester(tvBackFocusRequester) else Modifier
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .ifElse(isTv, Modifier.tvFocusRestorer())
                    .then(
                        if (isTv) {
                            Modifier.focusProperties {
                                down = tvPlayPauseFocusRequester
                            }
                        } else Modifier
                    )
                    .playerTopScrim()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = gestures.onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .then(tvBackFocusState.focusModifier)
                            .tvFocusIndicator(tvBackFocusState, IconButtonDefaults.smallRoundShape),
                    ) {
                        Icon(
                            Tabler.Outline.ArrowLeft,
                            stringResource(Res.string.player_video_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val showEndsAt = duration > 0
                    if (showClock || showEndsAt) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            if (showClock) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Tabler.Outline.Clock,
                                        contentDescription = stringResource(Res.string.player_video_current_time),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = rememberWallClockTimeString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            if (showClock && showEndsAt) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            if (showEndsAt) {
                                EndsAtLabel(
                                    currentPositionFlow = currentPositionFlow,
                                    duration = duration,
                                    playbackSpeed = playbackSpeed,
                                    controlsVisible = isVisible,
                                )
                            }
                        }
                    }
                    if (syncPlay.inSession) {
                        Spacer(Modifier.width(8.dp))
                        SyncPlayHeaderIndicator(
                            groupName = syncPlay.groupName ?: stringResource(Res.string.player_video_group),
                            participantCount = syncPlay.participantCount,
                            isSynced = syncPlay.isSynced,
                            isSyncing = syncPlay.isSyncing,
                            onClick = { sheets.openSheet(PlayerSheet.SyncPlay) },
                        )
                    }
                    if (castManager != null) {
                        Spacer(Modifier.width(8.dp))
                        PlatformCastButton(castManager = castManager)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = playerPlayButtonEnter(),
            exit = playerPlayButtonExit(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .ifElse(isTv, Modifier.tvFocusRestorer())
                    .then(
                        if (isTv) {
                            Modifier.focusProperties {
                                up = tvBackFocusRequester
                                down = tvSeekbarFocusRequester
                            }
                        } else Modifier
                    ),
            ) {
                val tvPreviousEpisodeFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FilledTonalIconButton(
                    onClick = transport.onPreviousEpisode,
                    enabled = transport.hasPreviousEpisode,
                    modifier = Modifier
                        .size(IconButtonDefaults.mediumContainerSize())
                        .then(tvPreviousEpisodeFocusState.focusModifier)
                        .tvFocusIndicator(tvPreviousEpisodeFocusState, IconButtonDefaults.largeRoundShape),
                    shape = IconButtonDefaults.largeRoundShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Tabler.Outline.PlayerSkipBack, stringResource(Res.string.player_video_previous_episode),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }

                val tvPlayPauseFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FilledIconButton(
                    onClick = transport.onPlayPause,
                    modifier = Modifier
                        .size(80.dp)
                        .then(tvPlayPauseFocusState.focusModifier)
                        .tvFocusIndicator(tvPlayPauseFocusState, CircleShape)
                        .ifElse(isTv, Modifier.focusRequester(tvPlayPauseFocusRequester)),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (transport.isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                        if (transport.isPlaying) stringResource(Res.string.player_video_pause) else stringResource(Res.string.player_video_play),
                        modifier = Modifier.size(40.dp),
                    )
                }

                val tvNextEpisodeFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FilledTonalIconButton(
                    onClick = transport.onNextEpisode,
                    enabled = transport.hasNextEpisode,
                    modifier = Modifier
                        .size(IconButtonDefaults.mediumContainerSize())
                        .then(tvNextEpisodeFocusState.focusModifier)
                        .tvFocusIndicator(tvNextEpisodeFocusState, IconButtonDefaults.largeRoundShape),
                    shape = IconButtonDefaults.largeRoundShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Tabler.Outline.PlayerSkipForward, stringResource(Res.string.player_video_next_episode),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = playerBottomControlsEnter(),
            exit = playerBottomControlsExit(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .playerBottomScrim()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp),
            ) {
                if (tracks.showPlaybackMetadata) {
                    PlaybackMetadataRow(
                        playMethod = tracks.playMethod,
                        isDirectPlayForced = tracks.isDirectPlayForced,
                        hdrType = tracks.hdrType,
                        mediaStreams = tracks.mediaStreams,
                        videoStats = playbackMetadata,
                        audioTracks = tracks.audioTracks,
                        isConnectionMetered = tracks.isConnectionMetered,
                        subtitleDelayMs = tracks.subtitleDelayMs,
                        onSubtitleDelayClick = sheets.onSubtitleDelayClick,
                        onPlayMethodClick = { sheets.openSheet(PlayerSheet.PlaybackMode) },
                        mpvConfigNoticeActive = tracks.mpvConfigNoticeActive,
                        onMpvConfigNoticeClick = sheets.onMpvConfigNoticeClick,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                TvControllableSeekBar(
                    currentPositionFlow = currentPositionFlow,
                    duration = duration,
                    chapters = chapters,
                    segments = segments,
                    bufferedRangesFlow = bufferedRangesFlow,
                    trickplayBitmap = transport.tvTrickplayBitmap,
                    playbackSpeed = playbackSpeed,
                    showTimeRemaining = showTimeRemaining,
                    abRepeatStartMs = abRepeat.aMs,
                    abRepeatEndMs = abRepeat.bMs,
                    onSeekStart = transport.onSeekStart,
                    onSeekEnd = transport.onSeekEnd,
                    onSeekPositionChange = transport.onSeekPositionChange,
                    tvFocusRequester = tvSeekbarFocusRequester,
                    tvUpFocusRequester = tvPlayPauseFocusRequester,
                    tvDownFocusRequester = tvBottomButtonsFocusRequester,
                )



                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .ifElse(isTv, Modifier.tvFocusRestorer())
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRequester(tvBottomButtonsFocusRequester)
                                    .focusProperties {
                                        up = tvSeekbarFocusRequester
                                    }
                            } else Modifier
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The primary control cluster (quality / speed / audio / subtitle
                    // / chapters / episodes / syncplay / aspect / info) is identical
                    // in both layouts — hoist it once so the 12-arg call lives in a
                    // single place. The portrait vs landscape branches differ only
                    // in which Row wraps it and which right-cluster buttons pin.
                    val primaryControls: @Composable () -> Unit = {
                        PrimaryMediaControls(
                            supportsLiveQualitySwitch = capabilities.supportsLiveQualitySwitch,
                            streamingQuality = tracks.streamingQuality,
                            playbackSpeed = playbackSpeed,
                            openSheet = sheets.openSheet,
                            onSubtitleClick = sheets.onSubtitleClick,
                            onSubtitleToggle = sheets.onSubtitleToggle,
                            chapters = chapters,
                            hasEpisodes = sheets.hasEpisodes,
                            episodeBrowserEnabled = sheets.episodeBrowserEnabled,
                            isInSyncPlaySession = syncPlay.inSession,
                            currentAspectRatio = currentAspectRatio,
                        )
                    }
                    if (splitBottomControlsEvenly) {
                        // PORTRAIT: one horizontally scrollable row holds every
                        // control; PiP, Rotate and the More (⋮) menu stay pinned at
                        // the right edge so the "exit portrait" and overflow actions
                        // can never scroll out of view.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(bottomLeftScrollState)
                                .horizontalFadingEdges(bottomLeftScrollState, 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            primaryControls()
                            PlayerIconButton(
                                icon = Tabler.Outline.Lock,
                                contentDescription = stringResource(Res.string.player_video_lock_screen),
                                onClick = gestures.onLockClick,
                            )
                            MuteButton(isMuted = transport.isMuted, onClick = transport.onMuteClick)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PipButton(onClick = gestures.onPipClick)
                            PlayerIconButton(
                                icon = Tabler.Outline.Rotate,
                                contentDescription = stringResource(Res.string.player_video_rotate_screen),
                                onClick = gestures.onToggleOrientation,
                            )
                            PlayerIconButton(
                                icon = Tabler.Outline.DotsVertical,
                                contentDescription = stringResource(Res.string.player_video_more_options),
                                onClick = { showOverflow = true },
                            )
                        }
                    } else {
                        // LANDSCAPE / TV: asymmetric layout — a scrolling primary
                        // row on the left and a fixed cluster (Lock, Mute, PiP,
                        // Rotate, More) on the right.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!isTv) {
                                        Modifier
                                            .horizontalScroll(bottomLeftScrollState)
                                            .horizontalFadingEdges(bottomLeftScrollState, 12.dp)
                                    } else Modifier
                                ),
                            horizontalArrangement = if (isTv) Arrangement.spacedBy(2.dp) else Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            primaryControls()
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isTv) {
                                PlayerIconButton(
                                    icon = Tabler.Outline.Lock,
                                    contentDescription = stringResource(Res.string.player_video_lock_screen),
                                    onClick = gestures.onLockClick,
                                )
                            }
                            if (!isTv) {
                                MuteButton(isMuted = transport.isMuted, onClick = transport.onMuteClick)
                            }
                            if (!isTv) {
                                PipButton(onClick = gestures.onPipClick)
                            }
                            if (!isTv) {
                                PlayerIconButton(
                                    icon = Tabler.Outline.Rotate,
                                    contentDescription = stringResource(Res.string.player_video_rotate_screen),
                                    onClick = gestures.onToggleOrientation,
                                )
                            }

                            PlayerIconButton(
                                icon = Tabler.Outline.DotsVertical,
                                contentDescription = stringResource(Res.string.player_video_more_options),
                                onClick = { showOverflow = true },
                                modifier = Modifier.then(
                                    if (isTv) {
                                        Modifier.focusProperties {
                                            right = when {
                                                isNextEpisodeVisible && tvNextEpisodeFocusRequester != null -> tvNextEpisodeFocusRequester
                                                isSkipSegmentVisible && tvSkipSegmentFocusRequester != null -> tvSkipSegmentFocusRequester
                                                else -> FocusRequester.Default
                                            }
                                        }
                                    } else Modifier
                                )
                            )
                        }
                    }
                }
            }
        }

        // In-window overflow panel (hosted in the root Box so the dismiss
        // interceptor can cover the full area and the panel anchors to TopEnd).
        // Rendered in-window — not a DropdownMenu popup — so it inherits the
        // player's immersive mode and the system bars never appear.
        PlayerOverflowMenu(
            expanded = showOverflow,
            onDismiss = { showOverflow = false },
            supportsSubtitleStyle = capabilities.supportsSubtitleStyle,
            supportsDialogueBoost = capabilities.supportsDialogueBoost,
            supportsNightMode = capabilities.supportsNightMode,
            supportsAudioDelay = capabilities.supportsAudioDelay,
            supportsSubtitleDelay = capabilities.supportsSubtitleDelay,
            supportsAudioPassthrough = capabilities.supportsAudioPassthrough,
            supportsAudioNormalization = capabilities.supportsAudioNormalization,
            supportsChannelMixing = capabilities.supportsChannelMixing,
            dialogueBoostEnabled = effectsControls.dialogueBoostEnabled,
            dialogueBoostStrength = effectsControls.dialogueBoostStrength,
            nightModeEnabled = effectsControls.nightModeEnabled,
            nightModeStrength = effectsControls.nightModeStrength,
            audioPassthrough = effectsControls.audioPassthrough,
            showVideoStats = sheets.showVideoStats,
            audioNormalizationMode = effectsControls.audioNormalizationMode,
            audioNormalizationEnabled = effectsControls.audioNormalizationEnabled,
            channelMixMode = effectsControls.channelMixMode,
            channelMixEnabled = effectsControls.channelMixEnabled,
            onSubtitleHubClick = {
                showOverflow = false
                sheets.onSubtitleHubClick()
            },
            onDialogueBoostClick = {
                showOverflow = false
                effectsControls.onDialogueBoostClick()
            },
            onDialogueBoostStrengthChange = effectsControls.onDialogueBoostStrengthChange,
            onNightModeClick = {
                showOverflow = false
                effectsControls.onNightModeClick()
            },
            onNightModeStrengthChange = effectsControls.onNightModeStrengthChange,
            onAVSyncClick = {
                showOverflow = false
                sheets.openSheet(PlayerSheet.AVSync)
            },
            playbackMode = tracks.playbackMode,
            onPlaybackModeClick = {
                showOverflow = false
                sheets.openSheet(PlayerSheet.PlaybackMode)
            },
            hasMultipleVersions = tracks.hasMultipleVersions,
            onVersionClick = {
                showOverflow = false
                sheets.onVersionClick()
            },
            onDecoderClick = {
                showOverflow = false
                sheets.openSheet(PlayerSheet.Decoder)
            },
            onPassthroughClick = {
                showOverflow = false
                effectsControls.onPassthroughClick()
            },
            onVideoStatsClick = {
                showOverflow = false
                sheets.onVideoStatsClick()
            },
            onAudioNormalizationClick = {
                showOverflow = false
                effectsControls.onAudioNormalizationClick()
            },
            onAudioNormalizationModeChange = {
                showOverflow = false
                effectsControls.onAudioNormalizationModeChange(it)
            },
            onChannelMixClick = {
                showOverflow = false
                effectsControls.onChannelMixClick()
            },
            onChannelMixModeChange = {
                showOverflow = false
                effectsControls.onChannelMixModeChange(it)
            },
            sleepTimerActive = sleepTimer.active,
            sleepTimerEndOfEpisode = sleepTimer.endOfEpisode,
            sleepTimerRemainingFlow = sleepTimer.remainingFlow,
            onSleepTimerClick = {
                showOverflow = false
                sheets.openSheet(PlayerSheet.SleepTimer)
            },
            supportsVideoFilters = capabilities.supportsVideoFilters,
            videoFiltersActive = sheets.videoFiltersActive,
            onVideoFilterClick = {
                showOverflow = false
                sheets.openSheet(PlayerSheet.VideoFilter)
            },
            supportsScreenshot = capabilities.supportsScreenshot,
            onScreenshotClick = {
                showOverflow = false
                sheets.onScreenshotClick()
            },
            abRepeat = abRepeat,
            onAbRepeatToggle = {
                showOverflow = false
                sheets.onAbRepeatToggle()
            },
            onAbRepeatSetA = {
                showOverflow = false
                sheets.onAbRepeatSetA()
            },
            onAbRepeatSetB = {
                showOverflow = false
                sheets.onAbRepeatSetB()
            },
            onAbRepeatClear = {
                showOverflow = false
                sheets.onAbRepeatClear()
            },
            audioOnly = sheets.audioOnly,
            onToggleAudioOnly = {
                showOverflow = false
                sheets.onToggleAudioOnly()
            },
            hasNextEpisode = transport.hasNextEpisode,
            incognitoModeEnabled = sheets.incognitoModeEnabled,
            onMarkWatchedAndSkip = {
                showOverflow = false
                sheets.onMarkWatchedAndSkip()
            },
            onMarkUnwatchedAndQuit = {
                showOverflow = false
                sheets.onMarkUnwatchedAndQuit()
            },
            supportsRenderPanel = sheets.supportsRenderPanel,
            onRenderClick = {
                showOverflow = false
                sheets.onRenderClick()
            },
            supportsDeinterlace = sheets.supportsDeinterlace,
            deinterlaceMode = sheets.deinterlaceMode,
            // The cycle closes the panel: the item's label reads the session
            // mode, and reopening is the cheap way to re-derive it fresh.
            onDeinterlaceCycle = {
                showOverflow = false
                sheets.onDeinterlaceCycle()
            },
        )
    }
}


// ── Section-host map (the VideoPlayerScreenOverlays.kt split precedent) ───
// The controls tree's private siblings live in sibling files under
// components/; declarations are unchanged except `private` → `internal`:
//  - PrimaryMediaControls.kt — the bottom bar's primary control cluster
//    (quality / speed / audio / subtitles / chapters / episodes / SyncPlay /
//    aspect / info), shared by the portrait and landscape/TV layouts.
//  - TvControllableSeekBar.kt — the scrubber + trickplay preview (band
//    shading, segment/chapter/A-B markers, D-pad + drag seek wiring, the
//    time labels).
//  - PlayerControlsHeader.kt — the header row's SyncPlay indicator pill and
//    the "Ends at" label (+ its private wall-clock helper).
// The overflow host itself (PlayerOverflowMenu) was already its own file;
// its call site stays in the root composable above. The seven @Immutable
// bundle carriers below the imports stay HERE — they are the root
// composable's parameter vocabulary (VideoPlayerScreen constructs them at
// the call site), not section hosts.
