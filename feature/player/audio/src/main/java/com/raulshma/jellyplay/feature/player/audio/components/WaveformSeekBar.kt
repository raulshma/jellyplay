package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.compose.foundation.focusable

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
    // Pause freezes the wave at its last phase rather than snapping back to 0,
    // so the paused waveform keeps the shape the user was just hearing.
    val phaseState = remember { mutableFloatStateOf(0f) }
    val isAnimating = isPlaying || isDragging
    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanoTime ->
                phaseState.floatValue = ((nanoTime / 1_000_000f) % 2000f) / 2000f * (2 * PI).toFloat()
            }
        }
    }

    // Keep the wave's vertical shape (amplitude) at full whether playing or
    // paused; only the horizontal motion (phase) freezes on pause. Previously
    // the amplitude collapsed to 0 on pause, flattening the wave into a line.
    val currentAmplitudeRatio by animateFloatAsState(
        targetValue = 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "amplitudeRatio",
    )

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } }
    val waveStroke = remember(strokeWidthPx) {
        Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    }

    val focusState = rememberTvFocusState()

    Box(
        modifier = modifier
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .focusable()
            .onDpadKey(
                onLeft = {
                    onSeek((progress - 0.05f).coerceIn(0f, 1f))
                    true
                },
                onRight = {
                    onSeek((progress + 0.05f).coerceIn(0f, 1f))
                    true
                }
            )
    ) {
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
                        // While dragging, follow the finger immediately — the reported
                        // playback position only catches up once the media controller
                        // echoes the new seek, which makes the bar feel unresponsive.
                        val displayFraction = if (isDragging) dragFraction else progress
                        val clamped = displayFraction.coerceIn(0f, 1f)
                        val amplitude = (height * 0.15f) * currentAmplitudeRatio
                        val progressX = width * clamped

                        wavePath.reset()
                        for (i in 0..steps) {
                            val x = width * i / steps
                            val normalizedX = x / width
                            val y = centerY + amplitude * sin(
                                (normalizedX * WAVE_FREQUENCY * 2 * PI + phaseState.floatValue).toFloat()
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
                            (clamped * WAVE_FREQUENCY * 2 * PI + phaseState.floatValue).toFloat()
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
            // Measure the tooltip's real width so it can be centered exactly over
            // the drag position — the previous fixed 50.dp guess drifted on any
            // label whose width differed (e.g. "1:23" vs "10:45").
            var tooltipWidthPx by remember { mutableFloatStateOf(0f) }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val dragX = maxWidthPx * dragFraction
                // With TopStart alignment the offset is from the left edge, so
                // this left-edge value centers the tooltip over the drag point.
                // Clamp so it never spills past either edge of the bar.
                val tooltipX = (dragX - tooltipWidthPx / 2f)
                    .coerceIn(0f, (maxWidthPx - tooltipWidthPx).coerceAtLeast(0f))
                Text(
                    text = tooltipTime,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    modifier = Modifier
                        .offset(x = with(LocalDensity.current) { tooltipX.toDp() })
                        .onGloballyPositioned { tooltipWidthPx = it.size.width.toFloat() }
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            ShapeCache.smooth4,
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
