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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ChapterInfo
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
    nightModeEnabled: Boolean,
    audioPassthrough: Boolean,
    isCasting: Boolean,
    isOcrRunning: Boolean,
    currentAspectRatio: AspectRatio,
    detectedAspectRatio: AspectRatio?,
    isVisible: Boolean,
    supportsSubtitleStyle: Boolean = false,
    supportsDialogueBoost: Boolean = false,
    supportsNightMode: Boolean = false,
    supportsAudioDelay: Boolean = false,
    supportsAudioPassthrough: Boolean = false,
    supportsOcr: Boolean = false,
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
    onSecondarySubtitleClick: () -> Unit,
    onChapterClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onNightModeClick: () -> Unit,
    onAudioDelayClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onCastClick: () -> Unit,
    onOcrClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
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
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onSeekBack() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious, "Rewind",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(24.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
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
                        Icons.Default.SkipNext, "Forward",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
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
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            ) {
                YouTubeStyleSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    chapters = chapters,
                    onSeek = { fraction ->
                        onSeek(fraction)
                        onSeekPositionChange((fraction * duration).toLong())
                    },
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd,
                )

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
                                nightModeEnabled = nightModeEnabled,
                                audioPassthrough = audioPassthrough,
                                isOcrRunning = isOcrRunning,
                                onSubtitleStyleClick = {
                                    showOverflow = false
                                    onSubtitleStyleClick()
                                },
                                onSecondarySubtitleClick = {
                                    showOverflow = false
                                    onSecondarySubtitleClick()
                                },
                                onDialogueBoostClick = {
                                    showOverflow = false
                                    onDialogueBoostClick()
                                },
                                onNightModeClick = {
                                    showOverflow = false
                                    onNightModeClick()
                                },
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

                        CastButton(isCasting = isCasting, onCast = onCastClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeStyleSeekBar(
    currentPosition: Long,
    duration: Long,
    chapters: List<ChapterInfo>,
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
                    detectTapGestures(
                        onPress = { offset ->
                            onSeekStart()
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onSeek(fraction)
                            isDragging = true
                            val released = tryAwaitRelease()
                            if (released) {
                                isDragging = false
                                onSeekEnd()
                            }
                        },
                    )
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
    nightModeEnabled: Boolean,
    audioPassthrough: Boolean,
    isOcrRunning: Boolean,
    onSubtitleStyleClick: () -> Unit,
    onSecondarySubtitleClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onNightModeClick: () -> Unit,
    onAudioDelayClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onPassthroughClick: () -> Unit,
    onSubtitleDownloadClick: () -> Unit,
    onOcrClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xE6222222),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (supportsSubtitleStyle) {
            OverflowMenuItem(
                icon = Icons.Default.ClosedCaption,
                label = "Subtitle Style",
                onClick = onSubtitleStyleClick,
            )
        }
        OverflowMenuItem(
            icon = Icons.Default.ClosedCaption,
            label = "Dual Subtitles",
            onClick = onSecondarySubtitleClick,
        )
        if (supportsDialogueBoost) {
            OverflowMenuItem(
                icon = Icons.Default.Audiotrack,
                label = "Dialogue Boost",
                onClick = onDialogueBoostClick,
                tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        if (supportsNightMode) {
            OverflowMenuItem(
                icon = Icons.Default.MoreVert,
                label = "Night Mode",
                onClick = onNightModeClick,
                tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
            )
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
