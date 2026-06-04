package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_FREQUENCY = 6.0f

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun WaveformSeekBar(
    progress: Float,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }
    var phaseShift by remember { mutableFloatStateOf(0f) }
    val isAnimating = isPlaying || isDragging
    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanoTime ->
                phaseShift = ((nanoTime / 1_000_000f) % 2000f) / 2000f * (2 * PI).toFloat()
            }
        }
    }

    val targetAmplitudeRatio = if (isAnimating) 1f else 0f
    val currentAmplitudeRatio by animateFloatAsState(
        targetValue = targetAmplitudeRatio,
        animationSpec = tween(durationMillis = 500),
        label = "amplitudeRatio",
    )

    val currentPhase = if (isAnimating) phaseShift else 0f

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } }
    val waveStroke = remember(strokeWidthPx) {
        Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(onSeek) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        val initialFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = initialFraction
                        onSeek(initialFraction)

                        drag(down.id) { change ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onSeek(fraction)
                        }
                        isDragging = false
                    }
                }
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val steps = (width / 2f).toInt().coerceAtLeast(100)
                    val wavePath = Path()

                    onDrawBehind {
                        val amplitude = (height * 0.15f) * currentAmplitudeRatio
                        val progressX = width * progress.coerceIn(0f, 1f)

                        wavePath.reset()
                        for (i in 0..steps) {
                            val x = width * i / steps
                            val normalizedX = x / width
                            val y = centerY + amplitude * sin(
                                (normalizedX * WAVE_FREQUENCY * 2 * PI + currentPhase).toFloat()
                            ).toFloat()
                            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        }

                        clipRect(left = progressX, right = width) {
                            drawPath(wavePath, color = inactiveColor, style = waveStroke)
                        }
                        clipRect(left = 0f, right = progressX) {
                            drawPath(wavePath, color = activeColor, style = waveStroke)
                        }

                        val dotY = centerY + amplitude * sin(
                            (progress.coerceIn(0f, 1f) * WAVE_FREQUENCY * 2 * PI + currentPhase).toFloat()
                        ).toFloat()
                        drawCircle(
                            color = activeColor,
                            radius = strokeWidthPx * 1.8f,
                            center = Offset(progressX, dotY),
                        )
                    }
                },
        )

        if (isDragging && durationMs > 0) {
            val tooltipTime = formatTime((dragFraction * durationMs).toLong())
            val tooltipOffsetFraction = dragFraction.coerceIn(0.05f, 0.95f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = tooltipTime,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    modifier = Modifier
                        .offset(x = with(density) { (tooltipOffsetFraction * 100f).dp - 25.dp })
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
