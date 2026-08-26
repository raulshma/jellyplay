package com.raulshma.jellyplay.feature.player.live.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.player.playerSeekbarDpSpec
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.handleDPadKeyEvents
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_badge
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_go_to_live

private const val DPAD_SEEK_STEP_MS = 10_000L

/**
 * Live-aware seek bar for DVR-window timeshift.
 *
 * - When `durationMs <= 0` (pure-live): nothing is rendered.
 * - When `durationMs > 0` (DVR window): a thin custom-drawn slider bound to
 *   [positionMs]..[durationMs] plus a "Go to live" button when behind the
 *   live edge, or a red "LIVE" label when at the edge.
 *
 * Drawn on a [Canvas] (not the M3 `Slider`) so the track is thin — 3 dp idle,
 * 5 dp while pressed/focused — matching the VOD [VideoPlayer] seek bar and
 * avoiding the chunky default Material3 Expressive track. The slider is
 * local-state-driven so a drag doesn't fight the 500 ms position ticker;
 * [onSeek] fires on drag end / tap / d-pad nudge. On TV, left/right d-pad
 * keys step ±10 s without losing focus to the chrome behind it.
 */
@Composable
fun LiveSeekBar(
    positionMs: Long,
    durationMs: Long,
    isAtLiveEdge: Boolean,
    onSeek: (Long) -> Unit,
    onSeekToLiveEdge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L) return // pure live — no seek bar
    var sliderValue by remember(positionMs, durationMs) {
        mutableFloatStateOf(positionMs.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }
    val isTv = LocalTvMode.current

    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val isActive = isDragging
    val trackHeight by animateDpAsState(
        targetValue = if (isActive) 5.dp else 3.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "liveTrackH",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isActive) 7.dp else 5.dp,
        animationSpec = playerSeekbarDpSpec(),
        label = "liveThumbR",
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .then(if (isTv) Modifier.focusable() else Modifier)
                .then(
                    if (isTv) Modifier.handleDPadKeyEvents(
                        onLeft = {
                            onSeek((sliderValue.toLong() - DPAD_SEEK_STEP_MS).coerceAtLeast(0L))
                        },
                        onRight = {
                            onSeek((sliderValue.toLong() + DPAD_SEEK_STEP_MS)
                                .coerceAtMost(durationMs))
                        },
                    ) else Modifier,
                )
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val target = (offset.x / size.width * durationMs)
                            .coerceIn(0f, durationMs.toFloat())
                        sliderValue = target
                        onSeek(target.toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            sliderValue = (offset.x / size.width * durationMs)
                                .coerceIn(0f, durationMs.toFloat())
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(sliderValue.toLong())
                        },
                        onDragCancel = { isDragging = false },
                    ) { change, _ ->
                        sliderValue = (change.position.x / size.width * durationMs)
                            .coerceIn(0f, durationMs.toFloat())
                    }
                },
        ) {
            val trackWidth = size.width
            val trackPx = trackHeight.toPx()
            val trackY = (size.height / 2f) - (trackPx / 2f)
            val corner = CornerRadius(trackPx / 2f)
            val progress = (sliderValue / durationMs).coerceIn(0f, 1f)

            // Inactive track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, trackY),
                size = Size(trackWidth, trackPx),
                cornerRadius = corner,
            )
            // Active (played) track
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, trackY),
                size = Size(trackWidth * progress, trackPx),
                cornerRadius = corner,
            )
            // Thumb
            val thumbCenterX = progress * trackWidth
            val thumbCenterY = size.height / 2f
            if (isActive) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.2f),
                    radius = thumbRadius.toPx() * 2.2f,
                    center = Offset(thumbCenterX, thumbCenterY),
                )
            }
            drawCircle(
                color = activeColor,
                radius = thumbRadius.toPx(),
                center = Offset(thumbCenterX, thumbCenterY),
            )
        }
        if (!isAtLiveEdge) {
            androidx.compose.material3.Button(onClick = onSeekToLiveEdge) {
                Text(stringResource(Res.string.live_go_to_live))
            }
        } else {
            Text(
                stringResource(Res.string.live_badge),
                color = Color.Red,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
