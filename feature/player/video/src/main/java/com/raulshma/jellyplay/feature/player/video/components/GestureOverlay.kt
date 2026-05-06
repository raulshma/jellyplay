package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (gesturesEnabled) Modifier.pointerInput(swipeSeekMaxMs) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onDragEnd = { onClearOverlays() },
                        onDragCancel = { onClearOverlays() },
                        onHorizontalDrag = { _, dragAmount ->
                            if (abs(dragAmount) > 20) {
                                val seekDelta = ((dragAmount / size.width) * swipeSeekMaxMs).toLong()
                                onSeekGesture(seekDelta)
                            }
                        },
                    )
                } else Modifier
            )
            .then(
                if (gesturesEnabled) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onDragEnd = { onClearOverlays() },
                        onDragCancel = { onClearOverlays() },
                        onVerticalDrag = { change, dragAmount ->
                            if (abs(dragAmount) > 10) {
                                val halfWidth = size.width / 2f
                                if (change.position.x > halfWidth) {
                                    val delta = -(dragAmount / size.height) * 0.5f
                                    onVolumeGesture(delta)
                                } else {
                                    val delta = -(dragAmount / size.height) * 0.5f
                                    onBrightnessGesture(delta)
                                }
                            }
                        },
                    )
                } else Modifier
            ),
    ) {
        if (seekDirection != 0 && seekOffsetMs > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = if (seekDirection < 0) Icons.Default.SkipPrevious else Icons.Default.SkipNext
                        Icon(icon, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (seekDirection < 0) "-" else "+"}${seekOffsetMs / 1000}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        if (brightnessValue >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("\u2600", fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${(brightnessValue * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (volumeValue >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${(volumeValue * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
