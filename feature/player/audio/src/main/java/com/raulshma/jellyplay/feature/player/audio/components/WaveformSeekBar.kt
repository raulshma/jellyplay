package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A Pixel Player–style sinusoidal waveform seek bar.
 *
 * Draws a continuous sine wave across the bar. The portion before the [progress]
 * point is drawn in [activeColor], the remainder in [inactiveColor]. The wave
 * gently undulates while [isPlaying] is true.
 *
 * @param progress Current playback progress, 0f..1f
 * @param isPlaying Whether playback is active (drives wave animation)
 * @param activeColor Color for the played portion of the wave
 * @param inactiveColor Color for the unplayed portion
 * @param onSeek Called with a new 0f..1f fraction when the user taps or drags
 */
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

    // Wave amplitude: animate to 0 when paused
    val targetAmplitudeRatio = if (isPlaying) 1f else 0f
    var currentAmplitudeRatio by remember { mutableFloatStateOf(targetAmplitudeRatio) }
    currentAmplitudeRatio += (targetAmplitudeRatio - currentAmplitudeRatio) * 0.08f

    val currentPhase = if (isPlaying) phaseShift else 0f

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
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val amplitude = (height * 0.28f) * currentAmplitudeRatio
            val progressX = width * progress.coerceIn(0f, 1f)

            // Wave parameters
            val frequency = 3.5f // ~3.5 full waves across the bar
            val strokeWidth = 3.dp.toPx()
            val steps = (width / 2f).toInt().coerceAtLeast(100)

            // Build the wave path
            val wavePath = Path().apply {
                for (i in 0..steps) {
                    val x = width * i / steps
                    val normalizedX = x / width
                    val y = centerY + amplitude * sin(
                        (normalizedX * frequency * 2 * PI + currentPhase).toFloat()
                    ).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

            val waveStroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            // Draw inactive (unplayed) portion
            clipRect(left = progressX, right = width) {
                drawPath(wavePath, color = inactiveColor, style = waveStroke)
            }

            // Draw active (played) portion
            clipRect(left = 0f, right = progressX) {
                drawPath(wavePath, color = activeColor, style = waveStroke)
            }

            // Draw a small dot at the progress point
            val dotY = centerY + amplitude * sin(
                (progress * frequency * 2 * PI + currentPhase).toFloat()
            ).toFloat()
            drawCircle(
                color = activeColor,
                radius = strokeWidth * 1.8f,
                center = Offset(progressX, dotY),
            )
        }
    }
}
