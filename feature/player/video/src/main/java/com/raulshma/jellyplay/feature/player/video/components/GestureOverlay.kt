package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun GestureOverlay(
    seekDirection: Int,
    seekOffsetMs: Long,
    brightnessValue: Float,
    volumeValue: Float,
    gesturesEnabled: Boolean,
    swipeSeekMaxMs: Long,
    showControls: Boolean,
    onSeekGesture: (Long) -> Unit,
    onBrightnessGesture: (Float) -> Unit,
    onVolumeGesture: (Float) -> Unit,
    onClearOverlays: () -> Unit,
    onEdgeSwipe: () -> Unit,
) {
    val currentOnSeekGesture by rememberUpdatedState(onSeekGesture)
    val currentOnBrightnessGesture by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolumeGesture by rememberUpdatedState(onVolumeGesture)
    val currentOnClearOverlays by rememberUpdatedState(onClearOverlays)
    val currentOnEdgeSwipe by rememberUpdatedState(onEdgeSwipe)

    val edgeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (gesturesEnabled) Modifier.pointerInput(swipeSeekMaxMs, showControls, edgeThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var decided = false
                        var isHorizontal = false
                        var isEdgeSwipeGesture = false
                        var edgeSwipeConsumed = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY
                            if (!decided && (abs(totalDx) > 50 || abs(totalDy) > 50)) {
                                decided = true
                                isHorizontal = abs(totalDx) > abs(totalDy)
                                isEdgeSwipeGesture = isHorizontal &&
                                    (startX < edgeThresholdPx || startX > size.width - edgeThresholdPx)
                            }
                            if (decided) {
                                if (isEdgeSwipeGesture) {
                                    if (!edgeSwipeConsumed) {
                                        edgeSwipeConsumed = true
                                        currentOnEdgeSwipe()
                                    }
                                } else if (isHorizontal) {
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (brightnessValue >= 0f) {
            EdgeBarOverlay(
                value = brightnessValue,
                icon = Tabler.Outline.BrightnessUp,
                label = "${(brightnessValue * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp),
            )
        }

        if (volumeValue >= 0f) {
            EdgeBarOverlay(
                value = volumeValue,
                icon = if (volumeValue == 0f) Tabler.Outline.VolumeOff else Tabler.Outline.Volume,
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val circleSize = 90.dp

    AnimatedVisibility(
        visible = true,
        enter = playerGestureFeedbackEnter(),
        exit = playerGestureFeedbackExit(),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val screenWidthPx = constraints.maxWidth
            val screenHeightPx = constraints.maxHeight
            val density = androidx.compose.ui.platform.LocalDensity.current
            val circleSizePx = with(density) { circleSize.toPx() }
            val xOffsetPx = if (isLeft) {
                screenWidthPx * 0.15f - circleSizePx / 2f
            } else {
                screenWidthPx * 0.85f - circleSizePx / 2f
            }
            val yOffsetPx = screenHeightPx / 2f - circleSizePx / 2f
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(
                        x = with(density) { xOffsetPx.toDp() },
                        y = with(density) { yOffsetPx.toDp() },
                    )
                    .size(circleSize)
                    .clip(ShapeCache.smooth24)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), ShapeCache.smooth24),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(circleSize),
                ) {
                    val strokeWidth = 3.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth * 2
                    val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth, strokeWidth)
                    drawArc(
                        color = primaryColor,
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
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (isLeft) Tabler.Outline.PlayerTrackPrev else Tabler.Outline.PlayerTrackNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "${seekOffsetMs / 1000}s",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
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
        enter = playerEdgeBarEnter(),
        exit = playerEdgeBarExit(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(160.dp)
                    .clip(ShapeCache.smoothPill)
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .clip(ShapeCache.smoothPill)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                )
                            )
                        )
                        .align(Alignment.BottomCenter),
                )
            }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
}
