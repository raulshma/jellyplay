package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_FREQUENCY = 3.5f

@Composable
fun WaveformSeekBar(
    progress: Float,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhaseShift",
    )

    val targetAmplitudeRatio = if (isPlaying) 1f else 0f
    val currentAmplitudeRatio by animateFloatAsState(
        targetValue = targetAmplitudeRatio,
        animationSpec = tween(durationMillis = 500),
        label = "amplitudeRatio",
    )

    val currentPhase = if (isPlaying) phaseShift else 0f

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 3.dp.toPx() } }
    val waveStroke = remember(strokeWidthPx) {
        Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .drawWithCache {
                val width = size.width
                val height = size.height
                val centerY = height / 2f
                val steps = (width / 2f).toInt().coerceAtLeast(100)
                val wavePath = Path()

                onDrawBehind {
                    val amplitude = (height * 0.28f) * currentAmplitudeRatio
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
}
