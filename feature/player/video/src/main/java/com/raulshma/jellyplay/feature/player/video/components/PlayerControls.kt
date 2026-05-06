package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.formatDuration

@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    hasChapters: Boolean,
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
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
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
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onSeekBack() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious, "Rewind",
                        tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.width(32.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Spacer(Modifier.width(32.dp))
                IconButton(
                    onClick = { onSeekForward() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.SkipNext, "Forward",
                        tint = Color.White, modifier = Modifier.size(32.dp)
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
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.85f),
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(currentPosition),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    Text(
                        if (duration > 0) formatDuration(duration) else "--:--",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { fraction ->
                        onSeek(fraction)
                        onSeekPositionChange((fraction * duration).toLong())
                    },
                    onValueChangeFinished = {
                        onSeekEnd()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabeledSpeedButton(onClick = onSpeedClick, speed = playbackSpeed)
                    LabeledControlButton(onClick = onAudioClick, icon = Icons.Default.Audiotrack, label = "Audio")
                    LabeledControlButton(onClick = onSubtitleClick, icon = Icons.Default.ClosedCaption, label = "Subs")
                    if (supportsSubtitleStyle) {
                        LabeledControlButton(onClick = onSubtitleStyleClick, icon = Icons.Default.Settings, label = "Style", iconModifier = Modifier.size(20.dp))
                    }
                    LabeledControlButton(onClick = onSecondarySubtitleClick, icon = Icons.Default.ClosedCaptionOff, label = "Dual Subs")
                    if (hasChapters) {
                        LabeledControlButton(onClick = onChapterClick, icon = Icons.AutoMirrored.Filled.List, label = "Chapters")
                    }
                    LabeledControlButton(
                        onClick = onAspectRatioClick,
                        icon = Icons.Default.AspectRatio,
                        label = if (currentAspectRatio == AspectRatio.AUTO && detectedAspectRatio != null) {
                            "Auto"
                        } else {
                            currentAspectRatio.displayName
                        },
                        tint = if (currentAspectRatio != AspectRatio.FIT) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                    LabeledControlButton(onClick = onInfoClick, icon = Icons.Default.Info, label = "Info")
                    if (supportsDialogueBoost) {
                        LabeledControlButton(
                            onClick = onDialogueBoostClick,
                            icon = Icons.Default.RecordVoiceOver,
                            label = "Boost",
                            tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (supportsNightMode) {
                        LabeledControlButton(
                            onClick = onNightModeClick,
                            icon = Icons.Default.Nightlight,
                            label = "Night",
                            tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (supportsAudioDelay) {
                        LabeledControlButton(onClick = onAudioDelayClick, icon = Icons.Default.GraphicEq, label = "Delay")
                    }
                    LabeledControlButton(onClick = onDecoderClick, icon = Icons.Default.Monitor, label = "Decoder")
                    if (supportsAudioPassthrough) {
                        LabeledControlButton(
                            onClick = onPassthroughClick,
                            icon = Icons.Default.SurroundSound,
                            label = "Passthrough",
                            tint = if (audioPassthrough) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    LabeledControlButton(onClick = onSubtitleDownloadClick, icon = Icons.Default.Download, label = "Download")
                    CastButton(isCasting = isCasting, onCast = onCastClick)
                    if (supportsOcr) {
                        LabeledControlButton(
                            onClick = onOcrClick,
                            icon = Icons.Default.Info,
                            label = "OCR",
                            enabled = !isOcrRunning,
                            iconModifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
