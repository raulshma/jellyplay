package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
internal fun GestureOverlay(
    seekDirection: Int,
    seekOffsetMs: Long,
    brightnessValue: Float,
    volumeValue: Float,
    gesturesEnabled: Boolean,
    swipeSeekMaxMs: Long,
    onSeekGesture: (Long) -> Unit,
    onBrightnessGesture: (Float) -> Unit,
    onVolumeGesture: (Float) -> Unit,
    onClearOverlays: () -> Unit,
) {
    val currentOnSeekGesture by rememberUpdatedState(onSeekGesture)
    val currentOnBrightnessGesture by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolumeGesture by rememberUpdatedState(onVolumeGesture)
    val currentOnClearOverlays by rememberUpdatedState(onClearOverlays)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (gesturesEnabled) Modifier.pointerInput(swipeSeekMaxMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var decided = false
                        var isHorizontal = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY
                            if (!decided && (abs(totalDx) > 50 || abs(totalDy) > 50)) {
                                decided = true
                                isHorizontal = abs(totalDx) > abs(totalDy)
                            }
                            if (decided) {
                                if (isHorizontal) {
                                    val seekDeltaMs = ((totalDx / size.width) * swipeSeekMaxMs).toLong()
                                    currentOnSeekGesture(seekDeltaMs)
                                } else {
                                    val halfWidth = size.width / 2f
                                    val dy = change.position.y - change.previousPosition.y
                                    val delta = -(dy / size.height) * 0.5f
                                    if (change.position.x > halfWidth) {
                                        currentOnVolumeGesture(delta)
                                    } else {
                                        currentOnBrightnessGesture(delta)
                                    }
                                }
                                change.consume()
                            }
                        } while (true)
                        currentOnClearOverlays()
                    }
                } else Modifier
            ),
    ) {
        if (seekDirection != 0 && seekOffsetMs > 0) {
            val isLeft = seekDirection < 0
            SeekCircleOverlay(
                isLeft = isLeft,
                seekOffsetMs = seekOffsetMs,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 100.dp),
            )
        }

        if (brightnessValue >= 0f) {
            EdgeBarOverlay(
                value = brightnessValue,
                icon = Icons.Default.BrightnessHigh,
                label = "${(brightnessValue * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            )
        }

        if (volumeValue >= 0f) {
            EdgeBarOverlay(
                value = volumeValue,
                icon = if (volumeValue == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = "${(volumeValue * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp),
            )
        }
    }
}

@Composable
private fun SeekCircleOverlay(
    isLeft: Boolean,
    seekOffsetMs: Long,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isLeft, seekOffsetMs) {
        val startTime = withFrameMillis { it }
        val duration = 700L
        while (true) {
            val elapsed = withFrameMillis { it } - startTime
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            if (elapsed >= duration) break
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(150)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLeft) Modifier.padding(end = 200.dp)
                    else Modifier.padding(start = 200.dp)
                ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape),
                ) {
                    val strokeWidth = 3.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth * 2
                    val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth, strokeWidth)
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360 * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (isLeft) Icons.Default.Replay10 else Icons.Default.Forward10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = "${seekOffsetMs / 1000}s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeBarOverlay(
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.BottomCenter),
                )
            }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
