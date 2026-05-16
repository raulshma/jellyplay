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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.player.video.formatDuration

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
    introTimestamps: IntroTimestamps? = null,
    creditTimestamps: CreditTimestamps? = null,
    skipSegmentText: String? = null,
    onSkipSegment: () -> Unit = {},
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
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val tvPlayPauseFocusRequester = remember { FocusRequester() }

    // On TV, auto-focus the play/pause button when controls become visible
    LaunchedEffect(isVisible, isTv) {
        if (isTv && isVisible) {
            tvPlayPauseFocusRequester.requestFocus()
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)) + slideInVertically(animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing)) { -it },
            exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + slideOutVertically(animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.1f),
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
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
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
            enter = fadeIn(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + scaleIn(initialScale = 0.85f, animationSpec = tween(AnimationTokens.QuickDuration, easing = PointToPointEasing)),
            exit = fadeOut(tween(AnimationTokens.MediumDuration, easing = AlphaEasing)) + scaleOut(targetScale = 0.85f, animationSpec = tween(AnimationTokens.DefaultDuration, easing = PointToPointEasing)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
            ) {
                IconButton(
                    onClick = { onSeekBack() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        Icons.Default.FastRewind, "Rewind",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(24.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .then(if (isTv) Modifier.focusRequester(tvPlayPauseFocusRequester) else Modifier),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                IconButton(
                    onClick = { onSeekForward() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        Icons.Default.FastForward, "Forward",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)) + slideInVertically(animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing)) { it },
            exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + slideOutVertically(animationSpec = tween(AnimationTokens.StandardDuration, easing = FancyTransitionEasing)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.7f),
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp),
            ) {
                YouTubeStyleSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    chapters = chapters,
                    introTimestamps = introTimestamps,
                    creditTimestamps = creditTimestamps,
                    onSeek = { fraction ->
                        onSeek(fraction)
                        onSeekPositionChange((fraction * duration).toLong())
                    },
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd,
                )

                AnimatedVisibility(
                    visible = skipSegmentText != null,
                    enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)) + scaleIn(initialScale = 0.8f, animationSpec = tween(AnimationTokens.QuickDuration, easing = PointToPointEasing)),
                    exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + scaleOut(targetScale = 0.8f, animationSpec = tween(AnimationTokens.DefaultDuration, easing = PointToPointEasing)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ShapeCache.smooth12)
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), ShapeCache.smooth12)
                                .clickable(onClick = onSkipSegment)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = skipSegmentText ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerSpeedButton(speed = playbackSpeed, onClick = onSpeedClick)
                        PlayerIconButton(
                            icon = Icons.Default.Audiotrack,
                            contentDescription = "Audio",
                            onClick = onAudioClick,
                        )
                        PlayerIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onSubtitleClick,
                        )
                        if (chapters.isNotEmpty()) {
                            PlayerIconButton(
                                icon = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Chapters",
                                onClick = onChapterClick,
                            )
                        }
                        if (hasEpisodes && episodeBrowserEnabled) {
                            PlayerIconButton(
                                icon = Icons.Default.VideoLibrary,
                                contentDescription = "Episodes",
                                onClick = onEpisodesClick,
                            )
                        }
                        if (isInSyncPlaySession) {
                            PlayerIconButton(
                                icon = Icons.Default.Group,
                                contentDescription = "SyncPlay",
                                onClick = onSyncPlayClick,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        PlayerIconButton(
                            icon = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            onClick = onAspectRatioClick,
                            tint = if (currentAspectRatio != AspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.White,
                        )
                        PlayerIconButton(
                            icon = Icons.Default.Info,
                            contentDescription = "Info",
                            onClick = onInfoClick,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PipButton(onClick = onPipClick)

                        var showOverflow by remember { mutableStateOf(false) }
                        Box {
                            PlayerIconButton(
                                icon = Icons.Default.MoreVert,
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
                                dialogueBoostEnabled = dialogueBoostEnabled,
                                dialogueBoostStrength = dialogueBoostStrength,
                                nightModeEnabled = nightModeEnabled,
                                nightModeStrength = nightModeStrength,
                                audioPassthrough = audioPassthrough,
                                isOcrRunning = isOcrRunning,
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
    Surface(
        shape = ShapeCache.smooth16,
        color = Color.White.copy(alpha = 0.15f),
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
                color = when {
                    isSynced -> Color(0xFF4CAF50)
                    isSyncing -> Color(0xFF2196F3)
                    else -> Color(0xFFFFC107)
                },
                modifier = Modifier.size(7.dp),
            ) {}
            Text(
                text = when {
                    isSynced -> "Synced"
                    isSyncing -> "Syncing"
                    else -> "Buffering"
                },
                color = when {
                    isSynced -> Color(0xFF4CAF50)
                    isSyncing -> Color(0xFF2196F3)
                    else -> Color(0xFFFFC107)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = groupName,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "$participantCount",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun YouTubeStyleSeekBar(
    currentPosition: Long,
    duration: Long,
    chapters: List<ChapterInfo>,
    introTimestamps: IntroTimestamps? = null,
    creditTimestamps: CreditTimestamps? = null,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val progress = if (duration > 0) {
        if (isDragging) dragFraction else currentPosition.toFloat() / duration
    } else 0f

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.White.copy(alpha = 0.2f)
    val trackHeight = 3.dp
    val thumbRadiusDp = if (isPressed) 7.dp else 6.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(duration) {
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
                },
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
                    color = inactiveColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackY),
                    size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight.toPx() / 2f),
                )

                if (duration > 0) {
                    val introTs = introTimestamps
                    if (introTs != null && introTs.hasIntro) {
                        val startFrac = (introTs.introStartTicks / 10_000f) / duration
                        val endFrac = (introTs.introEndTicks / 10_000f) / duration
                        if (startFrac in 0f..1f && endFrac > startFrac) {
                            val segHeightDp = 5.dp
                            val segHeight = segHeightDp.toPx()
                            val segY = (size.height / 2f) - (segHeight / 2f)
                            drawRoundRect(
                                color = Color(0xFF66BB6A).copy(alpha = 0.6f),
                                topLeft = androidx.compose.ui.geometry.Offset(startFrac * trackWidth, segY),
                                size = androidx.compose.ui.geometry.Size((endFrac - startFrac) * trackWidth, segHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(segHeight / 2f),
                            )
                        }
                    }
                    val creditTs = creditTimestamps
                    if (creditTs != null && creditTs.hasCredits) {
                        val startFrac = (creditTs.creditStartTicks / 10_000f) / duration
                        val endFrac = (creditTs.creditEndTicks / 10_000f) / duration
                        if (startFrac in 0f..1f && endFrac > startFrac) {
                            val segHeightDp = 5.dp
                            val segHeight = segHeightDp.toPx()
                            val segY = (size.height / 2f) - (segHeight / 2f)
                            drawRoundRect(
                                color = Color(0xFF42A5F5).copy(alpha = 0.6f),
                                topLeft = androidx.compose.ui.geometry.Offset(startFrac * trackWidth, segY),
                                size = androidx.compose.ui.geometry.Size((endFrac - startFrac) * trackWidth, segHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(segHeight / 2f),
                            )
                        }
                    }
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
                                color = Color.White.copy(alpha = 0.5f),
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
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = androidx.compose.ui.geometry.Offset(thumbCenterX, thumbCenterY),
                )
            }
        }

        if (isDragging) {
            val dragMs = (dragFraction * duration).toLong()
            Text(
                formatDuration(dragMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
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
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    if (duration > 0) formatDuration(duration) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
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
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: EffectStrength,
    audioPassthrough: Boolean,
    isOcrRunning: Boolean,
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
) {
    var showDialogueBoostSubmenu by remember { mutableStateOf(false) }
    var showNightModeSubmenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xE6222222),
        shape = ShapeCache.smooth12,
    ) {
        if (supportsSubtitleStyle) {
            OverflowMenuItem(
                icon = Icons.Default.ClosedCaption,
                label = "Subtitle Style",
                onClick = onSubtitleStyleClick,
            )
        }
        
        if (supportsDialogueBoost) {
            if (showDialogueBoostSubmenu) {
                OverflowMenuItem(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = false },
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (dialogueBoostEnabled && dialogueBoostStrength == strength)
                                    MaterialTheme.colorScheme.primary else Color.White,
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
                                    Icons.Default.Check,
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
                    icon = Icons.Default.Audiotrack,
                    label = if (dialogueBoostEnabled) "Dialogue Boost · ${dialogueBoostStrength.displayName}" else "Dialogue Boost",
                    onClick = { showDialogueBoostSubmenu = true },
                    tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        if (supportsNightMode) {
            if (showNightModeSubmenu) {
                OverflowMenuItem(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Night Mode",
                    onClick = { showNightModeSubmenu = false },
                )
                EffectStrength.entries.forEach { strength ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                strength.displayName,
                                color = if (nightModeEnabled && nightModeStrength == strength)
                                    MaterialTheme.colorScheme.primary else Color.White,
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
                                    Icons.Default.Check,
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
                    icon = Icons.Default.MoreVert,
                    label = if (nightModeEnabled) "Night Mode · ${nightModeStrength.displayName}" else "Night Mode",
                    onClick = { showNightModeSubmenu = true },
                    tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        if (supportsAudioDelay) {
            OverflowMenuItem(
                icon = Icons.Default.MoreVert,
                label = "Audio Delay",
                onClick = onAudioDelayClick,
            )
        }
        OverflowMenuItem(
            icon = Icons.Default.Info,
            label = "Decoder",
            onClick = onDecoderClick,
        )
        if (supportsAudioPassthrough) {
            OverflowMenuItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "Passthrough",
                onClick = onPassthroughClick,
                tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        OverflowMenuItem(
            icon = Icons.Default.MoreVert,
            label = "Download Subs",
            onClick = onSubtitleDownloadClick,
        )
        if (supportsOcr) {
            OverflowMenuItem(
                icon = Icons.Default.Info,
                label = "OCR Subtitle",
                onClick = onOcrClick,
                enabled = !isOcrRunning,
            )
        }
    }
}

@Composable
private fun OverflowMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (enabled) tint else Color.White.copy(alpha = 0.38f),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
