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
import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.res.stringResource
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
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.foundation.layout.offset
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot
import com.raulshma.jellyplay.feature.player.video.TrackOption
import com.raulshma.jellyplay.core.designsystem.theme.HdrColors
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    // High-frequency playback streams collected here so the seek bar /
    // time labels recompose at 4 Hz without invalidating the whole screen.
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    bufferedPositionFlow: StateFlow<Long>,
    videoStatsFlow: StateFlow<EngineVideoStats>,
    playbackSpeed: Float,
    chapters: List<ChapterInfo>,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: EffectStrength,
    audioPassthrough: Boolean,
    segments: List<MediaSegment> = emptyList(),
    resumePositionMs: Long = 0L,

    currentAspectRatio: AspectRatio,
    detectedAspectRatio: AspectRatio?,
    isVisible: Boolean,
    supportsSubtitleStyle: Boolean = false,
    supportsDialogueBoost: Boolean = false,
    supportsNightMode: Boolean = false,
    supportsAudioDelay: Boolean = false,
    supportsSubtitleDelay: Boolean = false,
    supportsAudioPassthrough: Boolean = false,
    hasEpisodes: Boolean = false,
    episodeBrowserEnabled: Boolean = true,
    onPlayPause: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit,
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onPreviousEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    tvTrickplayBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSubtitleStyleClick: () -> Unit,
    onChapterClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (EffectStrength) -> Unit,
    onAVSyncClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    onEpisodesClick: () -> Unit = {},
    onSyncPlayClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    onMuteClick: () -> Unit = {},
    isMuted: Boolean = false,
    isInSyncPlaySession: Boolean = false,
    syncPlayGroupName: String? = null,
    syncPlayParticipantCount: Int = 0,
    isSyncPlaySynced: Boolean = false,
    isSyncPlaySyncing: Boolean = false,
    showVideoStats: Boolean = false,
    onVideoStatsClick: () -> Unit = {},
    streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    playbackMode: PlaybackMode = PlaybackMode.AUTO,
    onQualityClick: () -> Unit = {},
    onPlaybackModeClick: () -> Unit = {},
    audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    audioNormalizationEnabled: Boolean = false,
    channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    channelMixEnabled: Boolean = false,
    supportsAudioNormalization: Boolean = false,
    supportsChannelMixing: Boolean = false,
    supportsLiveQualitySwitch: Boolean = true,
    onAudioNormalizationClick: () -> Unit = {},
    onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    onChannelMixClick: () -> Unit = {},
    onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    sleepTimerActive: Boolean = false,
    sleepTimerEndOfEpisode: Boolean = false,
    sleepTimerRemainingFlow: StateFlow<Long> = MutableStateFlow(0L),
    supportsVideoFilters: Boolean = false,
    videoFiltersActive: Boolean = false,
    onSleepTimerClick: () -> Unit = {},
    onVideoFilterClick: () -> Unit = {},
    supportsScreenshot: Boolean = false,
    onScreenshotClick: () -> Unit = {},
    abRepeatActive: Boolean = false,
    onAbRepeatToggle: () -> Unit = {},
    onAbRepeatSetA: () -> Unit = {},
    onAbRepeatSetB: () -> Unit = {},
    onAbRepeatClear: () -> Unit = {},
    audioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
    onLockClick: () -> Unit = {},
    onControlsFocusChange: (Boolean) -> Unit = {},
    onOverflowMenuChange: (Boolean) -> Unit = {},
    castManager: CastManager? = null,
    playMethod: String = "Direct Play",
    isDirectPlayForced: Boolean = false,
    hdrType: String? = null,
    mediaStreams: List<MediaStream> = emptyList(),
    audioTracks: List<TrackOption> = emptyList(),
    isConnectionMetered: Boolean = false,
    showPlaybackMetadata: Boolean = true,
    showClock: Boolean = false,
    showTimeRemaining: Boolean = false,
    onToggleOrientation: () -> Unit = {},
    tvSkipSegmentFocusRequester: FocusRequester? = null,
    tvNextEpisodeFocusRequester: FocusRequester? = null,
    isSkipSegmentVisible: Boolean = false,
    isNextEpisodeVisible: Boolean = false,
    onControlRowScrolled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // High-frequency position/buffered streams are collected in the leaf
    // composables that actually need them (TvControllableSeekBar and
    // EndsAtLabel), NOT here. Collecting at the root invalidated this entire
    // ~670-line body on every 4 Hz position tick and was a primary driver of
    // the MPV playback ANR. videoStats is projected through a derivedStateOf
    // below so only the low-churn codec/HDR slice reaches PlaybackMetadataRow;
    // sleepTimerRemainingMs changes at most once per second.
    val videoStats by videoStatsFlow.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by sleepTimerRemainingFlow.collectAsStateWithLifecycle()
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
                videoHdrType = videoStats.videoHdrType,
                audioCodec = videoStats.audioCodec,
                audioChannels = videoStats.audioChannels,
            )
        }
    }

    val isTv = LocalTvMode.current
    val isPortrait = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_PORTRAIT
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
            .collect { onControlRowScrolled() }
    }

    // Overflow menu open/close state. Hoisted to the PlayerControls scope (rather
    // than the local button Box) so the in-window panel can be hosted in the root
    // Box below, where it has room for a full-size dismiss interceptor and can be
    // anchored to TopEnd. Rendering it in-window (instead of a DropdownMenu popup)
    // keeps it in the player's immersive window so the system bars never appear.
    var showOverflow by remember { mutableStateOf(false) }
    LaunchedEffect(showOverflow) { onOverflowMenuChange(showOverflow) }

    LaunchedEffect(isVisible, isTv) {
        if (isTv && isVisible) {
            tvPlayPauseFocusRequester.tryRequestFocus()
        }
    }

    Box(
        modifier = modifier
            .onFocusChanged { focusState ->
                if (isTv) {
                    onControlsFocusChange(focusState.hasFocus)
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
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .then(tvBackFocusState.focusModifier)
                            .tvFocusIndicator(tvBackFocusState, IconButtonDefaults.smallRoundShape),
                    ) {
                        Icon(
                            Tabler.Outline.ArrowLeft,
                            stringResource(R.string.player_video_back),
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
                                        contentDescription = stringResource(R.string.player_video_current_time),
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
                    if (isInSyncPlaySession) {
                        Spacer(Modifier.width(8.dp))
                        SyncPlayHeaderIndicator(
                            groupName = syncPlayGroupName ?: stringResource(R.string.player_video_group),
                            participantCount = syncPlayParticipantCount,
                            isSynced = isSyncPlaySynced,
                            isSyncing = isSyncPlaySyncing,
                            onClick = onSyncPlayClick,
                        )
                    }
                    if (castManager != null) {
                        Spacer(Modifier.width(8.dp))
                        CastButton(castManager = castManager)
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
                    onClick = onPreviousEpisode,
                    enabled = hasPreviousEpisode,
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
                        Tabler.Outline.PlayerSkipBack, stringResource(R.string.player_video_previous_episode),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }

                val tvPlayPauseFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FilledIconButton(
                    onClick = onPlayPause,
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
                        if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                        if (isPlaying) stringResource(R.string.player_video_pause) else stringResource(R.string.player_video_play),
                        modifier = Modifier.size(40.dp),
                    )
                }

                val tvNextEpisodeFocusState = rememberTvFocusState(focusedScale = 1.08f)
                FilledTonalIconButton(
                    onClick = onNextEpisode,
                    enabled = hasNextEpisode,
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
                        Tabler.Outline.PlayerSkipForward, stringResource(R.string.player_video_next_episode),
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
                if (showPlaybackMetadata) {
                    PlaybackMetadataRow(
                        playMethod = playMethod,
                        isDirectPlayForced = isDirectPlayForced,
                        hdrType = hdrType,
                        mediaStreams = mediaStreams,
                        videoStats = playbackMetadata,
                        audioTracks = audioTracks,
                        isConnectionMetered = isConnectionMetered,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                TvControllableSeekBar(
                    currentPositionFlow = currentPositionFlow,
                    duration = duration,
                    chapters = chapters,
                    segments = segments,
                    bufferedPositionFlow = bufferedPositionFlow,
                    trickplayBitmap = tvTrickplayBitmap,
                    playbackSpeed = playbackSpeed,
                    showTimeRemaining = showTimeRemaining,
                    resumePositionMs = resumePositionMs,
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd,
                    onSeekPositionChange = onSeekPositionChange,
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
                            supportsLiveQualitySwitch = supportsLiveQualitySwitch,
                            streamingQuality = streamingQuality,
                            onQualityClick = onQualityClick,
                            playbackSpeed = playbackSpeed,
                            onSpeedClick = onSpeedClick,
                            onAudioClick = onAudioClick,
                            onSubtitleClick = onSubtitleClick,
                            chapters = chapters,
                            onChapterClick = onChapterClick,
                            hasEpisodes = hasEpisodes,
                            episodeBrowserEnabled = episodeBrowserEnabled,
                            onEpisodesClick = onEpisodesClick,
                            isInSyncPlaySession = isInSyncPlaySession,
                            onSyncPlayClick = onSyncPlayClick,
                            currentAspectRatio = currentAspectRatio,
                            onAspectRatioClick = onAspectRatioClick,
                            onInfoClick = onInfoClick,
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
                                contentDescription = stringResource(R.string.player_video_lock_screen),
                                onClick = onLockClick,
                            )
                            MuteButton(isMuted = isMuted, onClick = onMuteClick)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PipButton(onClick = onPipClick)
                            PlayerIconButton(
                                icon = Tabler.Outline.Rotate,
                                contentDescription = stringResource(R.string.player_video_rotate_screen),
                                onClick = onToggleOrientation,
                            )
                            PlayerIconButton(
                                icon = Tabler.Outline.DotsVertical,
                                contentDescription = stringResource(R.string.player_video_more_options),
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
                                    contentDescription = stringResource(R.string.player_video_lock_screen),
                                    onClick = onLockClick,
                                )
                            }
                            if (!isTv) {
                                MuteButton(isMuted = isMuted, onClick = onMuteClick)
                            }
                            if (!isTv) {
                                PipButton(onClick = onPipClick)
                            }
                            if (!isTv) {
                                PlayerIconButton(
                                    icon = Tabler.Outline.Rotate,
                                    contentDescription = stringResource(R.string.player_video_rotate_screen),
                                    onClick = onToggleOrientation,
                                )
                            }

                            PlayerIconButton(
                                icon = Tabler.Outline.DotsVertical,
                                contentDescription = stringResource(R.string.player_video_more_options),
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
            supportsSubtitleStyle = supportsSubtitleStyle,
            supportsDialogueBoost = supportsDialogueBoost,
            supportsNightMode = supportsNightMode,
            supportsAudioDelay = supportsAudioDelay,
            supportsSubtitleDelay = supportsSubtitleDelay,
            supportsAudioPassthrough = supportsAudioPassthrough,
            supportsAudioNormalization = supportsAudioNormalization,
            supportsChannelMixing = supportsChannelMixing,
            dialogueBoostEnabled = dialogueBoostEnabled,
            dialogueBoostStrength = dialogueBoostStrength,
            nightModeEnabled = nightModeEnabled,
            nightModeStrength = nightModeStrength,
            audioPassthrough = audioPassthrough,
            showVideoStats = showVideoStats,
            audioNormalizationMode = audioNormalizationMode,
            audioNormalizationEnabled = audioNormalizationEnabled,
            channelMixMode = channelMixMode,
            channelMixEnabled = channelMixEnabled,
            onSubtitleStyleClick = {
                showOverflow = false
                onSubtitleStyleClick()
            },
            onDialogueBoostClick = {
                showOverflow = false
                onDialogueBoostClick()
            },
            onDialogueBoostStrengthChange = onDialogueBoostStrengthChange,
            onNightModeClick = {
                showOverflow = false
                onNightModeClick()
            },
            onNightModeStrengthChange = onNightModeStrengthChange,
            onAVSyncClick = {
                showOverflow = false
                onAVSyncClick()
            },
            playbackMode = playbackMode,
            onPlaybackModeClick = {
                showOverflow = false
                onPlaybackModeClick()
            },
            onDecoderClick = {
                showOverflow = false
                onDecoderClick()
            },
            onPassthroughClick = {
                showOverflow = false
                onPassthroughClick()
            },
            onSubtitleDownloadClick = {
                showOverflow = false
                onSubtitleDownloadClick()
            },
            onVideoStatsClick = {
                showOverflow = false
                onVideoStatsClick()
            },
            onAudioNormalizationClick = {
                showOverflow = false
                onAudioNormalizationClick()
            },
            onAudioNormalizationModeChange = {
                showOverflow = false
                onAudioNormalizationModeChange(it)
            },
            onChannelMixClick = {
                showOverflow = false
                onChannelMixClick()
            },
            onChannelMixModeChange = {
                showOverflow = false
                onChannelMixModeChange(it)
            },
            sleepTimerActive = sleepTimerActive,
            sleepTimerDisplayText = if (sleepTimerEndOfEpisode) "End of episode" else formatDuration(sleepTimerRemainingMs),
            onSleepTimerClick = {
                showOverflow = false
                onSleepTimerClick()
            },
            supportsVideoFilters = supportsVideoFilters,
            videoFiltersActive = videoFiltersActive,
            onVideoFilterClick = {
                showOverflow = false
                onVideoFilterClick()
            },
            supportsScreenshot = supportsScreenshot,
            onScreenshotClick = {
                showOverflow = false
                onScreenshotClick()
            },
            abRepeatActive = abRepeatActive,
            onAbRepeatToggle = {
                showOverflow = false
                onAbRepeatToggle()
            },
            onAbRepeatSetA = {
                showOverflow = false
                onAbRepeatSetA()
            },
            onAbRepeatSetB = {
                showOverflow = false
                onAbRepeatSetB()
            },
            onAbRepeatClear = {
                showOverflow = false
                onAbRepeatClear()
            },
            audioOnly = audioOnly,
            onToggleAudioOnly = {
                showOverflow = false
                onToggleAudioOnly()
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
    onQualityClick: () -> Unit,
    playbackSpeed: Float,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    chapters: List<ChapterInfo>,
    onChapterClick: () -> Unit,
    hasEpisodes: Boolean,
    episodeBrowserEnabled: Boolean,
    onEpisodesClick: () -> Unit,
    isInSyncPlaySession: Boolean,
    onSyncPlayClick: () -> Unit,
    currentAspectRatio: AspectRatio,
    onAspectRatioClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    if (supportsLiveQualitySwitch) {
        PlayerQualityButton(
            quality = streamingQuality,
            onClick = onQualityClick,
        )
    }
    PlayerSpeedButton(speed = playbackSpeed, onClick = onSpeedClick)
    PlayerIconButton(
        icon = Tabler.Outline.Music,
        contentDescription = stringResource(R.string.player_video_audio),
        onClick = onAudioClick,
    )
    PlayerIconButton(
        icon = Tabler.Outline.Subtitles,
        contentDescription = stringResource(R.string.player_video_subtitles),
        onClick = onSubtitleClick,
    )
    if (chapters.isNotEmpty()) {
        PlayerIconButton(
            icon = Tabler.Outline.List,
            contentDescription = stringResource(R.string.player_video_chapters),
            onClick = onChapterClick,
        )
    }
    if (hasEpisodes && episodeBrowserEnabled) {
        PlayerIconButton(
            icon = Tabler.Outline.ListNumbers,
            contentDescription = stringResource(R.string.player_video_episodes),
            onClick = onEpisodesClick,
        )
    }
    if (isInSyncPlaySession) {
        PlayerIconButton(
            icon = Tabler.Outline.Users,
            contentDescription = stringResource(R.string.player_video_syncplay),
            onClick = onSyncPlayClick,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    PlayerIconButton(
        icon = Tabler.Outline.AspectRatio,
        contentDescription = stringResource(R.string.player_video_aspect_ratio_label),
        onClick = onAspectRatioClick,
        tint = if (currentAspectRatio != AspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
    PlayerIconButton(
        icon = Tabler.Outline.InfoCircle,
        contentDescription = stringResource(R.string.player_video_info),
        onClick = onInfoClick,
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
    bufferedPositionFlow: StateFlow<Long> = MutableStateFlow(0L),
    trickplayBitmap: Bitmap? = null,
    playbackSpeed: Float = 1.0f,
    showTimeRemaining: Boolean = false,
    resumePositionMs: Long = 0L,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit = {},
    tvFocusRequester: FocusRequester = remember { FocusRequester() },
    tvUpFocusRequester: FocusRequester? = null,
    tvDownFocusRequester: FocusRequester? = null,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    val bufferedPosition by bufferedPositionFlow.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSeekBarFocused by remember { mutableStateOf(false) }
    var tvSeekPosition by remember { mutableFloatStateOf(0f) }
    var tvSeekStarted by remember { mutableStateOf(false) }

    val progress = if (duration > 0) {
        if (isDragging) dragFraction
        else if (isTv && isSeekBarFocused) tvSeekPosition
        else currentPosition.toFloat() / duration
    } else 0f

    val bufferedFraction = if (duration > 0) {
        (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

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
    // Resume-position marker: tertiary stands apart from progress/chapter markers.
    val resumeMarkerColor = MaterialTheme.colorScheme.tertiary

    // Precompute the per-segment draw color once per segment list. The seek bar
    // Canvas redraws on every position/buffered tick (~4 Hz) and previously
    // allocated a fresh Color(segment.type.colorLong) per segment per frame;
    // the segment colors only change when the segment list itself changes.
    val segmentColors = remember(segments) {
        segments.associate { it to Color(it.type.colorLong) }
    }

    val tvFocusState = rememberTvFocusState(focusedScale = 1f)

    val seekStep = if (isTv) 30_000f / duration else 10_000f / duration

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
                                val wasFocused = isSeekBarFocused
                                isSeekBarFocused = focusState.isFocused
                                if (focusState.isFocused && !wasFocused) {
                                    tvSeekPosition = if (duration > 0) currentPosition.toFloat() / duration else 0f
                                    tvSeekStarted = false
                                }
                                if (!focusState.isFocused && tvSeekStarted) {
                                    tvSeekStarted = false
                                    onSeekEnd()
                                }
                            }
                            .focusable()
                            .onDpadKey(
                                onRight = {
                                    if (duration <= 0) return@onDpadKey false
                                    if (!tvSeekStarted) {
                                        tvSeekStarted = true
                                        onSeekStart()
                                    }
                                    tvSeekPosition = (tvSeekPosition + seekStep).coerceAtMost(1f)
                                    onSeekPositionChange((tvSeekPosition * duration).toLong())
                                    true
                                },
                                onLeft = {
                                    if (duration <= 0) return@onDpadKey false
                                    if (!tvSeekStarted) {
                                        tvSeekStarted = true
                                        onSeekStart()
                                    }
                                    tvSeekPosition = (tvSeekPosition - seekStep).coerceAtLeast(0f)
                                    onSeekPositionChange((tvSeekPosition * duration).toLong())
                                    true
                                },
                                onSelect = {
                                    if (tvSeekStarted) {
                                        tvSeekStarted = false
                                        onSeekEnd()
                                    }
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
                                    onSeekStart()
                                    var fraction = (downEvent.position.x / size.width).coerceIn(0f, 1f)
                                    dragFraction = fraction
                                    onSeekPositionChange((fraction * duration).toLong())
                                    isDragging = true

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == downEvent.id }
                                        if (change != null) {
                                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            dragFraction = fraction
                                            onSeekPositionChange((fraction * duration).toLong())
                                            change.consume()
                                        }
                                    } while (change?.pressed == true)

                                    isDragging = false
                                    onSeekEnd()
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

                if (bufferedFraction > 0f) {
                    val bufferColor = activeColor.copy(alpha = 0.25f)
                    drawRoundRect(
                        color = bufferColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                        size = androidx.compose.ui.geometry.Size(trackWidth * bufferedFraction, trackHeight.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                    )
                }

                drawRoundRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth * progress, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

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

                if (resumePositionMs > 0 && duration > 0) {
                    val resumeFraction = (resumePositionMs.toFloat() / duration).coerceIn(0.01f, 0.99f)
                    val resumeX = resumeFraction * trackWidth
                    val resumeMarkerHeight = 10.dp.toPx()
                    val resumeMarkerWidth = 2.dp.toPx()
                    drawRoundRect(
                        color = resumeMarkerColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            resumeX - resumeMarkerWidth / 2f,
                            trackY + trackHeight.toPx() / 2f - resumeMarkerHeight / 2f,
                        ),
                        size = androidx.compose.ui.geometry.Size(resumeMarkerWidth, resumeMarkerHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                    )
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
        text = stringResource(R.string.player_video_ends_at, endsAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}

@Composable
private fun rememberEndsAtTime(remainingMs: Long, controlsVisible: Boolean): String {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
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
    return remember(currentSystemTime, remainingMs, formatter) {
        val endsAtDate = Date(currentSystemTime + remainingMs)
        formatter.format(endsAtDate)
    }
}
