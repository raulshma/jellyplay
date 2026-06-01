package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.SyncStatusColors
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    chapters: List<ChapterInfo>,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: EffectStrength,
    audioPassthrough: Boolean,
    isOcrRunning: Boolean,
    segments: List<MediaSegment> = emptyList(),

    currentAspectRatio: AspectRatio,
    detectedAspectRatio: AspectRatio?,
    isVisible: Boolean,
    supportsSubtitleStyle: Boolean = false,
    supportsDialogueBoost: Boolean = false,
    supportsNightMode: Boolean = false,
    supportsAudioDelay: Boolean = false,
    supportsAudioPassthrough: Boolean = false,
    supportsOcr: Boolean = false,
    hasEpisodes: Boolean = false,
    episodeBrowserEnabled: Boolean = true,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit,
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
    onAudioDelayClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onOcrClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    onEpisodesClick: () -> Unit = {},
    onSyncPlayClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    isInSyncPlaySession: Boolean = false,
    syncPlayGroupName: String? = null,
    syncPlayParticipantCount: Int = 0,
    isSyncPlaySynced: Boolean = false,
    isSyncPlaySyncing: Boolean = false,
    showVideoStats: Boolean = false,
    onVideoStatsClick: () -> Unit = {},
    bufferedPosition: Long = 0L,
    streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    onQualityClick: () -> Unit = {},
    audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    audioNormalizationEnabled: Boolean = false,
    channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    channelMixEnabled: Boolean = false,
    supportsAudioNormalization: Boolean = false,
    supportsChannelMixing: Boolean = false,
    onAudioNormalizationClick: () -> Unit = {},
    onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    onChannelMixClick: () -> Unit = {},
    onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    sleepTimerActive: Boolean = false,
    sleepTimerDisplayText: String = "",
    supportsVideoFilters: Boolean = false,
    videoFiltersActive: Boolean = false,
    onSleepTimerClick: () -> Unit = {},
    onVideoFilterClick: () -> Unit = {},
    onLockClick: () -> Unit = {},
    onControlsFocusChange: (Boolean) -> Unit = {},
    onOverflowMenuChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val tvPlayPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible, isTv) {
        if (isTv && isVisible) {
            tvPlayPauseFocusRequester.requestFocus()
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
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f),
                                Color.Transparent,
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Tabler.Outline.ArrowLeft,
                            "Back",
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
                    if (isInSyncPlaySession) {
                        Spacer(Modifier.width(8.dp))
                        SyncPlayHeaderIndicator(
                            groupName = syncPlayGroupName ?: "Group",
                            participantCount = syncPlayParticipantCount,
                            isSynced = isSyncPlaySynced,
                            isSyncing = isSyncPlaySyncing,
                            onClick = onSyncPlayClick,
                        )
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
                modifier = Modifier.then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
            ) {
                FilledTonalIconButton(
                    onClick = onSeekBack,
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                    shape = IconButtonDefaults.largeRoundShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Tabler.Outline.PlayerTrackPrev, "Rewind",
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(80.dp)
                        .then(if (isTv) Modifier.focusRequester(tvPlayPauseFocusRequester) else Modifier),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                        Icon(
                            if (playing) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            if (playing) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                    shape = IconButtonDefaults.largeRoundShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Tabler.Outline.PlayerTrackNext, "Forward",
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize),
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
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp),
            ) {
                TvControllableSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    chapters = chapters,
                    segments = segments,
                    bufferedPosition = bufferedPosition,
                    trickplayBitmap = tvTrickplayBitmap,
                    onSeek = { fraction ->
                        onSeek(fraction)
                        onSeekPositionChange((fraction * duration).toLong())
                    },
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd,
                    onSeekPositionChange = onSeekPositionChange,
                )



                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerQualityButton(
                            quality = streamingQuality,
                            onClick = onQualityClick,
                        )
                        PlayerSpeedButton(speed = playbackSpeed, onClick = onSpeedClick)
                        PlayerIconButton(
                            icon = Tabler.Outline.Music,
                            contentDescription = "Audio",
                            onClick = onAudioClick,
                        )
                        PlayerIconButton(
                            icon = Tabler.Outline.Subtitles,
                            contentDescription = "Subtitles",
                            onClick = onSubtitleClick,
                        )
                        if (chapters.isNotEmpty()) {
                            PlayerIconButton(
                                icon = Tabler.Outline.List,
                                contentDescription = "Chapters",
                                onClick = onChapterClick,
                            )
                        }
                        if (hasEpisodes && episodeBrowserEnabled) {
                            PlayerIconButton(
                                icon = Tabler.Outline.Video,
                                contentDescription = "Episodes",
                                onClick = onEpisodesClick,
                            )
                        }
                        if (isInSyncPlaySession) {
                            PlayerIconButton(
                                icon = Tabler.Outline.Users,
                                contentDescription = "SyncPlay",
                                onClick = onSyncPlayClick,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        PlayerIconButton(
                            icon = Tabler.Outline.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            onClick = onAspectRatioClick,
                            tint = if (currentAspectRatio != AspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                        PlayerIconButton(
                            icon = Tabler.Outline.InfoCircle,
                            contentDescription = "Info",
                            onClick = onInfoClick,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerIconButton(
                            icon = Tabler.Outline.Lock,
                            contentDescription = "Lock screen",
                            onClick = onLockClick,
                        )
                        PipButton(onClick = onPipClick)

                        var showOverflow by remember { mutableStateOf(false) }
                        LaunchedEffect(showOverflow) { onOverflowMenuChange(showOverflow) }
                        Box {
                            PlayerIconButton(
                                icon = Tabler.Outline.DotsVertical,
                                contentDescription = "More options",
                                onClick = { showOverflow = true },
                            )
                            PlayerOverflowMenu(
                                expanded = showOverflow,
                                onDismiss = { showOverflow = false },
                                supportsSubtitleStyle = supportsSubtitleStyle,
                                supportsDialogueBoost = supportsDialogueBoost,
                                supportsNightMode = supportsNightMode,
                                supportsAudioDelay = supportsAudioDelay,
                                supportsAudioPassthrough = supportsAudioPassthrough,
                                supportsOcr = supportsOcr,
                                supportsAudioNormalization = supportsAudioNormalization,
                                supportsChannelMixing = supportsChannelMixing,
                                dialogueBoostEnabled = dialogueBoostEnabled,
                                dialogueBoostStrength = dialogueBoostStrength,
                                nightModeEnabled = nightModeEnabled,
                                nightModeStrength = nightModeStrength,
                                audioPassthrough = audioPassthrough,
                                isOcrRunning = isOcrRunning,
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
                                onAudioDelayClick = {
                                    showOverflow = false
                                    onAudioDelayClick()
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
                                onOcrClick = {
                                    showOverflow = false
                                    onOcrClick()
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
                                sleepTimerDisplayText = sleepTimerDisplayText,
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
                            )
                        }

                        CastButton()
                    }
                }
            }
        }
    }
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
    currentPosition: Long,
    duration: Long,
    chapters: List<ChapterInfo>,
    segments: List<MediaSegment> = emptyList(),
    bufferedPosition: Long = 0L,
    trickplayBitmap: Bitmap? = null,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit = {},
) {
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

    val tvFocusState = rememberTvFocusState(focusedScale = 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(
                    if (isTv) {
                        Modifier
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, ShapeCache.smooth4)
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
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                                if (duration <= 0) return@onKeyEvent false
                                val seekStep = 10_000f / duration
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        if (!tvSeekStarted) {
                                            tvSeekStarted = true
                                            onSeekStart()
                                        }
                                        tvSeekPosition = (tvSeekPosition + seekStep).coerceAtMost(1f)
                                        onSeek(tvSeekPosition)
                                        onSeekPositionChange((tvSeekPosition * duration).toLong())
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (!tvSeekStarted) {
                                            tvSeekStarted = true
                                            onSeekStart()
                                        }
                                        tvSeekPosition = (tvSeekPosition - seekStep).coerceAtLeast(0f)
                                        onSeek(tvSeekPosition)
                                        onSeekPositionChange((tvSeekPosition * duration).toLong())
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        if (tvSeekStarted) {
                                            tvSeekStarted = false
                                            onSeekEnd()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
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
                                    onSeek(fraction)
                                    isDragging = true

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == downEvent.id }
                                        if (change != null) {
                                            fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            dragFraction = fraction
                                            onSeek(fraction)
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
                            color = Color(segment.type.colorLong).copy(alpha = 0.4f),
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

            if (isTv && isSeekBarFocused && trickplayBitmap != null) {
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

            Text(
                formatDuration(displayMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(currentPosition),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    if (duration > 0) formatDuration(duration) else "--:--",
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

@Composable
private fun PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    supportsSubtitleStyle: Boolean,
    supportsDialogueBoost: Boolean,
    supportsNightMode: Boolean,
    supportsAudioDelay: Boolean,
    supportsAudioPassthrough: Boolean,
    supportsOcr: Boolean,
    supportsAudioNormalization: Boolean,
    supportsChannelMixing: Boolean,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: EffectStrength,
    audioPassthrough: Boolean,
    isOcrRunning: Boolean,
    showVideoStats: Boolean = false,
    audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    audioNormalizationEnabled: Boolean = false,
    channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    channelMixEnabled: Boolean = false,
    sleepTimerActive: Boolean = false,
    sleepTimerDisplayText: String = "",
    onSubtitleStyleClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (EffectStrength) -> Unit,
    onAudioDelayClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    onOcrClick: () -> Unit,
    onVideoStatsClick: () -> Unit = {},
    onAudioNormalizationClick: () -> Unit = {},
    onAudioNormalizationModeChange: (AudioNormalizationMode) -> Unit = {},
    onChannelMixClick: () -> Unit = {},
    onChannelMixModeChange: (ChannelMixMode) -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    supportsVideoFilters: Boolean = false,
    videoFiltersActive: Boolean = false,
    onVideoFilterClick: () -> Unit = {},
) {
    var showDialogueBoostSubmenu by remember { mutableStateOf(false) }
    var showNightModeSubmenu by remember { mutableStateOf(false) }
    var showAudioNormalizationSubmenu by remember { mutableStateOf(false) }
    var showChannelMixSubmenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        if (supportsSubtitleStyle) {
            OverflowMenuItem(
                icon = Tabler.Outline.Subtitles,
                label = "Subtitle Style",
                onClick = onSubtitleStyleClick,
            )
        }
        
        if (supportsDialogueBoost) {
            if (showDialogueBoostSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = false },
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (dialogueBoostEnabled && dialogueBoostStrength == strength)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            if (!dialogueBoostEnabled) onDialogueBoostClick()
                            onDialogueBoostStrengthChange(strength)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (dialogueBoostEnabled && dialogueBoostStrength == strength) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Music,
                    label = if (dialogueBoostEnabled) "Dialogue Boost \u00B7 ${dialogueBoostStrength.displayName}" else "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = true },
                    tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (supportsNightMode) {
            if (showNightModeSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Night Mode",
                    onClick = { showNightModeSubmenu = false },
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (nightModeEnabled && nightModeStrength == strength)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            if (!nightModeEnabled) onNightModeClick()
                            onNightModeStrengthChange(strength)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (nightModeEnabled && nightModeStrength == strength) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.DotsVertical,
                    label = if (nightModeEnabled) "Night Mode \u00B7 ${nightModeStrength.displayName}" else "Night Mode",
                    onClick = { showNightModeSubmenu = true },
                    tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (supportsAudioNormalization) {
            if (showAudioNormalizationSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Audio Normalization",
                    onClick = { showAudioNormalizationSubmenu = false },
                )
                AudioNormalizationMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                mode.displayName,
                                color = if (audioNormalizationEnabled && audioNormalizationMode == mode)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onAudioNormalizationModeChange(mode)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (audioNormalizationEnabled && audioNormalizationMode == mode) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Volume,
                    label = if (audioNormalizationEnabled) "Normalization \u00B7 ${audioNormalizationMode.displayName}" else "Audio Normalization",
                    onClick = { showAudioNormalizationSubmenu = true },
                    tint = if (audioNormalizationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (supportsChannelMixing) {
            if (showChannelMixSubmenu) {
                OverflowMenuItem(
                    icon = Tabler.Outline.ArrowLeft,
                    label = "Channel Mixing",
                    onClick = { showChannelMixSubmenu = false },
                )
                ChannelMixMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                mode.displayName,
                                color = if (channelMixEnabled && channelMixMode == mode)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onChannelMixModeChange(mode)
                            onDismiss()
                        },
                        leadingIcon = {
                            if (channelMixEnabled && channelMixMode == mode) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            } else {
                OverflowMenuItem(
                    icon = Tabler.Outline.Music,
                    label = if (channelMixEnabled) "Channel Mix \u00B7 ${channelMixMode.displayName}" else "Channel Mixing",
                    onClick = { showChannelMixSubmenu = true },
                    tint = if (channelMixEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (supportsAudioDelay) {
            OverflowMenuItem(
                icon = Tabler.Outline.DotsVertical,
                label = "Audio Delay",
                onClick = onAudioDelayClick,
            )
        }
        OverflowMenuItem(
            icon = Tabler.Outline.InfoCircle,
            label = "Decoder",
            onClick = onDecoderClick,
        )
        if (supportsAudioPassthrough) {
            OverflowMenuItem(
                icon = Tabler.Outline.Volume,
                label = "Passthrough",
                onClick = onPassthroughClick,
                tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        OverflowMenuItem(
            icon = Tabler.Outline.DotsVertical,
            label = "Download Subs",
            onClick = onSubtitleDownloadClick,
        )
        if (supportsOcr) {
            OverflowMenuItem(
                icon = Tabler.Outline.InfoCircle,
                label = "OCR Subtitle",
                onClick = onOcrClick,
                enabled = !isOcrRunning,
            )
        }
        OverflowMenuItem(
            icon = Tabler.Outline.InfoCircle,
            label = if (showVideoStats) "Stats for Nerds \u00B7 On" else "Stats for Nerds",
            onClick = onVideoStatsClick,
            tint = if (showVideoStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        OverflowMenuItem(
            icon = Tabler.Outline.Stopwatch,
            label = if (sleepTimerActive) "Sleep Timer \u00B7 $sleepTimerDisplayText" else "Sleep Timer",
            onClick = onSleepTimerClick,
            tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (supportsVideoFilters) {
            OverflowMenuItem(
                icon = Tabler.Outline.ColorSwatch,
                label = if (videoFiltersActive) "Video Filters \u00B7 On" else "Video Filters",
                onClick = onVideoFilterClick,
                tint = if (videoFiltersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun OverflowMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val effectiveTint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) effectiveTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) effectiveTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
