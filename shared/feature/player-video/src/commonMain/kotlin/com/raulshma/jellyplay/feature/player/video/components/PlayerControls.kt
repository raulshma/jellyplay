package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import com.raulshma.jellyplay.feature.player.video.PlatformBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.SyncStatusColors
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.harness.harnessClickTarget
import com.raulshma.jellyplay.feature.player.video.PlatformCastButton
import com.raulshma.jellyplay.feature.player.video.SeekBarBufferBands
import com.raulshma.jellyplay.feature.player.video.rememberIs24HourFormat
import com.raulshma.jellyplay.feature.player.video.rememberIsPortraitOrientation
import com.raulshma.jellyplay.feature.player.video.state.rememberTvSeekController
import com.raulshma.jellyplay.feature.player.video.state.tvSeekStepFraction
import com.raulshma.jellyplay.core.ui.animation.horizontalFadingEdges
import com.raulshma.jellyplay.core.ui.player.PlayerIconButton
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsExit
import com.raulshma.jellyplay.core.ui.player.playerBottomScrim
import com.raulshma.jellyplay.core.ui.player.playerPlayButtonEnter
import com.raulshma.jellyplay.core.ui.player.playerPlayButtonExit
import com.raulshma.jellyplay.core.ui.player.playerSeekbarDpSpec
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
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_aspect_ratio_label
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_audio
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_back
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_chapters
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_current_time
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ends_at
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_episodes
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_group
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_info
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_lock_screen
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_more_options
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_next_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pause
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_previous_episode
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_rotate_screen
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_syncplay


















import com.raulshma.jellyplay.feature.player.video.AbRepeatState
import com.raulshma.jellyplay.feature.player.video.PlayerSheet
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.foundation.layout.offset
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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

/**
 * The primary media controls that appear in the bottom control bar: quality,
 * speed, audio, subtitles, chapters, episodes, SyncPlay, aspect ratio and info.
 * Extracted so the portrait (single scrolling row) and landscape/TV (scrolling
 * left + fixed right) layouts can host the same set without duplicating it.
 */
@Composable
private fun PrimaryMediaControls(
    supportsLiveQualitySwitch: Boolean,
    streamingQuality: StreamingQuality,
    playbackSpeed: Float,
    // Pure sheet openers funnel through this (see PlayerControls' KDoc);
    // onSubtitleClick stays separate because it flags the hub's tab state.
    openSheet: (PlayerSheet) -> Unit,
    onSubtitleClick: () -> Unit,
    chapters: List<ChapterInfo>,
    hasEpisodes: Boolean,
    episodeBrowserEnabled: Boolean,
    isInSyncPlaySession: Boolean,
    currentAspectRatio: AspectRatio,
) {
    if (supportsLiveQualitySwitch) {
        PlayerQualityButton(
            quality = streamingQuality,
            onClick = { openSheet(PlayerSheet.Quality) },
        )
    }
    PlayerSpeedButton(speed = playbackSpeed, onClick = { openSheet(PlayerSheet.Speed) })
    PlayerIconButton(
        icon = Tabler.Outline.Music,
        contentDescription = stringResource(Res.string.player_video_audio),
        onClick = { openSheet(PlayerSheet.Audio) },
    )
    PlayerIconButton(
        icon = Tabler.Outline.Subtitles,
        contentDescription = stringResource(Res.string.player_video_subtitles),
        onClick = onSubtitleClick,
        // e2e: click-reach target (harness-gated no-op) — see HarnessClickBridge.
        modifier = Modifier.harnessClickTarget("player-subtitles-trigger"),
    )
    if (chapters.isNotEmpty()) {
        PlayerIconButton(
            icon = Tabler.Outline.List,
            contentDescription = stringResource(Res.string.player_video_chapters),
            onClick = { openSheet(PlayerSheet.Chapter) },
        )
    }
    if (hasEpisodes && episodeBrowserEnabled) {
        PlayerIconButton(
            icon = Tabler.Outline.ListNumbers,
            contentDescription = stringResource(Res.string.player_video_episodes),
            onClick = { openSheet(PlayerSheet.Episodes) },
        )
    }
    if (isInSyncPlaySession) {
        PlayerIconButton(
            icon = Tabler.Outline.Users,
            contentDescription = stringResource(Res.string.player_video_syncplay),
            onClick = { openSheet(PlayerSheet.SyncPlay) },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    PlayerIconButton(
        icon = Tabler.Outline.AspectRatio,
        contentDescription = stringResource(Res.string.player_video_aspect_ratio_label),
        onClick = { openSheet(PlayerSheet.AspectRatio) },
        tint = if (currentAspectRatio != AspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
    PlayerIconButton(
        icon = Tabler.Outline.InfoCircle,
        contentDescription = stringResource(Res.string.player_video_info),
        onClick = { openSheet(PlayerSheet.PlaybackInfo) },
    )
}

@Composable
private fun SyncPlayHeaderIndicator(
    groupName: String,
    participantCount: Int,
    isSynced: Boolean,
    isSyncing: Boolean = false,
    onClick: () -> Unit,
) {
    val statusColor = when {
        isSynced -> SyncStatusColors.synced
        isSyncing -> SyncStatusColors.syncing
        else -> SyncStatusColors.else_
    }

    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor,
                modifier = Modifier.size(7.dp),
            ) {}
            Text(
                text = when {
                    isSynced -> "Synced"
                    isSyncing -> "Syncing"
                    else -> "Buffering"
                },
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = groupName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "$participantCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TvControllableSeekBar(
    // High-frequency streams collected at this leaf so only the seek bar
    // recomposes at 4 Hz, not the whole PlayerControls body. Mirrors the
    // VideoStatsOverlay pattern. The previous design collected these in
    // PlayerControls' root, which invalidated a ~670-line, 110-parameter
    // composable on every position tick and was a primary driver of the
    // MPV playback ANR (Davey! / skipped frames / input-dispatch timeout).
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    chapters: List<ChapterInfo>,
    segments: List<MediaSegment> = emptyList(),
    // each range shades its own band (multi-range buffered model);
    // collected at this leaf like currentPositionFlow so only the seek bar
    // recomposes when the engine republishes ranges. Empty = no shading.
    bufferedRangesFlow: StateFlow<List<LongRange>> = MutableStateFlow(emptyList()),
    trickplayBitmap: PlatformBitmap? = null,
    playbackSpeed: Float = 1.0f,
    showTimeRemaining: Boolean = false,
    // Plain values (not streams): A/B points change only on user action, never
    // per position tick, so passing them here keeps the leaf-collection rule
    // intact while letting the canvas visualize the window. Point presence
    // drives drawing — A alone shows its tick, both points show the region.
    // `setEnabled(false)` wipes points, so a disabled window never renders.
    abRepeatStartMs: Long? = null,
    abRepeatEndMs: Long? = null,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit = {},
    tvFocusRequester: FocusRequester = remember { FocusRequester() },
    tvUpFocusRequester: FocusRequester? = null,
    tvDownFocusRequester: FocusRequester? = null,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    // the band math (range→fraction coercion + degenerate pruning) is
    // the pure [SeekBarBufferBands] ladder — the Canvas below only loops over
    // the result.
    val bufferedRanges by bufferedRangesFlow.collectAsStateWithLifecycle()
    val bufferedBands = remember(bufferedRanges, duration) {
        SeekBarBufferBands.bands(bufferedRanges, duration)
    }
    val isTv = LocalTvMode.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    // The seek state machine (focus seeding, D-pad accumulate-and-clamp,
    // drag-vs-tv-vs-live priority, the seek-callback choreography) lives in
    // TvSeekController; this composable keeps only drawing and event wiring.
    val seekController = rememberTvSeekController(
        onSeekStart = onSeekStart,
        onSeekPreview = onSeekPositionChange,
        onSeekEnd = onSeekEnd,
    )
    // DurationChanged: duration is a plain recomposition input — assigning it
    // in the body keeps every handler's duration exactly as fresh as the
    // former inline lambdas that captured the parameter per recomposition.
    seekController.durationMs = duration
    val isDragging by seekController.isDragging.collectAsStateWithLifecycle()
    val dragFraction by seekController.dragFraction.collectAsStateWithLifecycle()
    val isSeekBarFocused by seekController.isFocused.collectAsStateWithLifecycle()
    val tvSeekPosition by seekController.tvSeekPosition.collectAsStateWithLifecycle()

    val progress = seekController.progress(currentPosition)

    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val isActive = isPressed || isDragging || (isTv && isSeekBarFocused)
    val trackHeight by animateDpAsState(
        targetValue = if (isActive) 5.dp else 3.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "trackH",
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (isActive) 7.dp else 5.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "thumbR",
    )

    val chapterMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    // A/B repeat window: translucent region on the track + primary edge ticks.
    val abRepeatRegionColor = activeColor.copy(alpha = 0.28f)
    val abRepeatMarkerColor = activeColor

    // Precompute the per-segment draw color once per segment list. The seek bar
    // Canvas redraws on every position/buffered tick (~4 Hz) and previously
    // allocated a fresh Color(segment.type.colorLong) per segment per frame;
    // the segment colors only change when the segment list itself changes.
    val segmentColors = remember(segments) {
        segments.associate { it to Color(it.type.colorLong) }
    }

    val tvFocusState = rememberTvFocusState(focusedScale = 1f)

    // The 30s-TV / 10s-touch seek-step divergence — the policy lives on
    // TvSeekController.tvSeekStepFraction (jvmTest-pinned); the controller's
    // D-pad handlers are clamped by this fraction per tick.
    val seekStep = tvSeekStepFraction(isTv, duration)

    // Position-derived labels memoized by second to cut formatDuration allocations
    // during the 4 Hz position tick (most recomposes only move the playhead).
    val currentPositionText = remember(currentPosition / 1000) { formatDuration(currentPosition) }
    val durationText = remember(duration / 1000) { formatDuration(duration) }
    val remainingText = remember(duration, currentPosition / 1000, showTimeRemaining) {
        if (duration > 0 && showTimeRemaining) {
            val remainingMs = (duration - currentPosition).coerceAtLeast(0)
            "-" + formatDuration(remainingMs)
        } else null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isTv && isSeekBarFocused && trickplayBitmap != null) {
            val displayMs = (tvSeekPosition * duration).toLong()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                TrickplayOverlay(
                    bitmap = trickplayBitmap,
                    positionMs = displayMs,
                    durationMs = duration,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                // a11y: the Canvas-drawn seekbar previously carried no
                // semantics, so TalkBack ignored the primary scrub control
                // entirely. Expose it as a Role.Slider with a ProgressBarRangeInfo
                // (announces "% of duration") and a SetProgress action so
                // accessibility services can both read and move the position.
                .semantics {
                    // No explicit Role.Slider/Role.ProgressBar (neither value is
                    // present in this Compose BOM). The setProgress action and
                    // progressBarRangeInfo together make TalkBack announce the
                    // control as an adjustable progress element.
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                        progress.coerceIn(0f, 1f),
                        0f..1f,
                    )
                    if (duration > 0) {
                        setProgress { target ->
                            val clamped = target.coerceIn(0f, 1f)
                            onSeekStart()
                            onSeekPositionChange((clamped * duration).toLong())
                            onSeekEnd()
                            true
                        }
                    }
                }
                .then(
                    if (isTv) {
                        Modifier
                            .focusRequester(tvFocusRequester)
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, ShapeCache.smooth4)
                            .then(
                                if (tvUpFocusRequester != null || tvDownFocusRequester != null) {
                                    Modifier.focusProperties {
                                        tvUpFocusRequester?.let { up = it }
                                        tvDownFocusRequester?.let { down = it }
                                    }
                                } else Modifier
                            )
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // Seed only on the gain transition — a
                                    // repeated focused callback must not
                                    // clobber an in-flight seek.
                                    if (!seekController.isFocused.value) {
                                        seekController.onFocusGained(currentPosition)
                                    }
                                } else {
                                    // Commits any unflushed D-pad seek.
                                    seekController.onFocusLost()
                                }
                            }
                            .focusable()
                            .onDpadKey(
                                onRight = { seekController.onDpadTick(direction = +1, step = seekStep) },
                                onLeft = { seekController.onDpadTick(direction = -1, step = seekStep) },
                                onSelect = {
                                    seekController.flush()
                                    true
                                },
                            )
                    } else {
                        Modifier.pointerInput(duration) {
                            if (duration <= 0) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val downEvent = awaitFirstDown()
                                    downEvent.consume()
                                    var fraction = (downEvent.position.x / size.width).coerceIn(0f, 1f)
                                    seekController.onDragStart(fraction)

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == downEvent.id }
                                        if (change != null) {
                                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            seekController.onDragTo(fraction)
                                            change.consume()
                                        }
                                    } while (change?.pressed == true)

                                    seekController.onDragEnd()
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            ) {
                val trackY = (size.height / 2f) - (trackHeight.toPx() / 2f)
                val trackWidth = size.width

                drawRoundRect(
                    color = trackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

                if (duration > 0) {
                    segments.forEach { segment ->
                        if (!segment.hasSegment) return@forEach
                        val startFrac = (segment.startTicks / 10_000f) / duration
                        val endFrac = (segment.endTicks / 10_000f) / duration
                        if (startFrac !in 0f..1f || endFrac <= startFrac) return@forEach
                        val segHeight = 6.dp.toPx()
                        val segY = (size.height / 2f) - (segHeight / 2f)
                        drawRoundRect(
                            color = (segmentColors[segment] ?: Color.Black).copy(alpha = 0.4f),
                            topLeft = androidx.compose.ui.geometry.Offset(startFrac * trackWidth, segY),
                            size = androidx.compose.ui.geometry.Size((endFrac - startFrac) * trackWidth, segHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(segHeight / 2f),
                        )
                    }
                }

                // EACH buffered range shades its own band — the
                // generalization of the former single 0..bufferedFraction
                // draw. Bands come from the pure SeekBarBufferBands ladder;
                // gaps between ranges stay unshaded (a forward seek drops the
                // old window while the back-buffer survives behind the
                // playhead).
                if (bufferedBands.isNotEmpty()) {
                    val bufferColor = activeColor.copy(alpha = 0.25f)
                    bufferedBands.forEach { band ->
                        drawRoundRect(
                            color = bufferColor,
                            topLeft = androidx.compose.ui.geometry.Offset(band.startFraction * trackWidth, trackY),
                            size = androidx.compose.ui.geometry.Size(
                                (band.endFraction - band.startFraction) * trackWidth,
                                trackHeight.toPx(),
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                        )
                    }
                }

                drawRoundRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth * progress, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

                // A/B repeat window: the A tick appears as soon as A is
                // captured; completing the window (A < B) adds the shaded
                // region and the B tick. Toggling A/B repeat off wipes the
                // points, so the markers vanish with it.
                val abStart = abRepeatStartMs
                val abEnd = abRepeatEndMs
                if (duration > 0) {
                    val abMarkerHeight = 10.dp.toPx()
                    val abMarkerWidth = 2.dp.toPx()
                    if (abStart != null && abEnd != null && abStart < abEnd) {
                        val abStartFrac = (abStart.toFloat() / duration).coerceIn(0f, 1f)
                        val abEndFrac = (abEnd.toFloat() / duration).coerceIn(0f, 1f)
                        if (abEndFrac > abStartFrac) {
                            drawRoundRect(
                                color = abRepeatRegionColor,
                                topLeft = androidx.compose.ui.geometry.Offset(abStartFrac * trackWidth, trackY),
                                size = androidx.compose.ui.geometry.Size(
                                    (abEndFrac - abStartFrac) * trackWidth,
                                    trackHeight.toPx(),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                            )
                            listOf(abStartFrac, abEndFrac).forEach { frac ->
                                drawRoundRect(
                                    color = abRepeatMarkerColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        frac * trackWidth - abMarkerWidth / 2f,
                                        trackY + trackHeight.toPx() / 2f - abMarkerHeight / 2f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(abMarkerWidth, abMarkerHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                                )
                            }
                        }
                    } else if (abStart != null) {
                        val abStartFrac = (abStart.toFloat() / duration).coerceIn(0f, 1f)
                        drawRoundRect(
                            color = abRepeatMarkerColor,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                abStartFrac * trackWidth - abMarkerWidth / 2f,
                                trackY + trackHeight.toPx() / 2f - abMarkerHeight / 2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(abMarkerWidth, abMarkerHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                        )
                    }
                }

                if (chapters.isNotEmpty() && duration > 0) {
                    chapters.forEach { chapter ->
                        val chapterFraction = (chapter.startPositionTicks / 10_000f) / duration
                        if (chapterFraction in 0.01f..0.99f) {
                            val markerX = chapterFraction * trackWidth
                            val markerHeight = 7.dp.toPx()
                            val markerWidth = 2.dp.toPx()
                            drawRoundRect(
                                color = chapterMarkerColor,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    markerX - markerWidth / 2f,
                                    trackY + trackHeight.toPx() / 2f - markerHeight / 2f,
                                ),
                                size = androidx.compose.ui.geometry.Size(markerWidth, markerHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                }

                val thumbRadius = thumbRadiusDp.toPx()
                val thumbCenterX = progress * trackWidth
                val thumbCenterY = size.height / 2f

                if (isActive) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.2f),
                        radius = thumbRadius * 2.2f,
                        center = androidx.compose.ui.geometry.Offset(thumbCenterX, thumbCenterY),
                    )
                }

                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = androidx.compose.ui.geometry.Offset(thumbCenterX, thumbCenterY),
                )
            }
        }

        if (isDragging || (isTv && isSeekBarFocused)) {
            val displayMs = if (isDragging) (dragFraction * duration).toLong() else (tvSeekPosition * duration).toLong()

            Row(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(displayMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentPositionText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                val endLabel = if (duration > 0) {
                    remainingText ?: durationText
                } else {
                    "--:--"
                }
                Text(
                    endLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Renders the "Ends at HH:mm" header label. Collects [currentPositionFlow]
 * here at the leaf so the 4 Hz position tick recomposes only this tiny
 * composable, not the entire PlayerControls body. `duration` and
 * `playbackSpeed` are stable/low-churn scalars, safe to pass by value.
 */
@Composable
private fun EndsAtLabel(
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    playbackSpeed: Float,
    controlsVisible: Boolean,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    val remainingMs = (duration - currentPosition).coerceAtLeast(0)
    val realRemainingMs = if (playbackSpeed > 0f) (remainingMs / playbackSpeed).toLong() else remainingMs
    val endsAt = rememberEndsAtTime(realRemainingMs, controlsVisible)
    Text(
        text = stringResource(Res.string.player_video_ends_at, endsAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}

@Composable
private fun rememberEndsAtTime(remainingMs: Long, controlsVisible: Boolean): String {
    val is24Hour = rememberIs24HourFormat()
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var currentSystemTime by remember { mutableStateOf(System.currentTimeMillis()) }
    // Only poll while the controls (and therefore the "ends-at" label) are
    // visible, and align the delay to the wall-clock minute boundary — the
    // label only needs minute precision, so sub-minute updates are invisible.
    // Was an unconditional 5 s poll for the lifetime of PlayerControls.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) return@LaunchedEffect
        while (true) {
            currentSystemTime = System.currentTimeMillis()
            val msToNextMinute = 60_000L - (currentSystemTime % 60_000L)
            kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1_000L))
        }
    }
    return remember((currentSystemTime + remainingMs) / 60_000L, formatter) {
        val endsAtDate = Date(currentSystemTime + remainingMs)
        formatter.format(endsAtDate)
    }
}
