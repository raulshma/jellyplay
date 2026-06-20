package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Refresh
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AVSyncSheet(
    currentAudioDelayMs: Long,
    currentSubtitleDelayMs: Long,
    onAudioDelayChange: (Long) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var audioDelayMs by remember { mutableLongStateOf(currentAudioDelayMs) }
    var subtitleDelayMs by remember { mutableLongStateOf(currentSubtitleDelayMs) }
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("av-sync")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "A/V Sync",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(20.dp))

            DelayRow(
                label = "Audio Delay",
                delayMs = audioDelayMs,
                focusRequester = focusRequester,
                isTv = isTv,
                helperText = "Negative: audio earlier. Positive: audio later.",
                onValueChange = { audioDelayMs = it; onAudioDelayChange(it) },
            )

            Spacer(Modifier.height(24.dp))

            DelayRow(
                label = "Subtitle Delay",
                delayMs = subtitleDelayMs,
                focusRequester = null,
                isTv = isTv,
                helperText = "Negative: subtitles earlier. Positive: subtitles later.",
                onValueChange = { subtitleDelayMs = it; onSubtitleDelayChange(it) },
            )

            Spacer(Modifier.height(20.dp))

            val resetFocus = rememberTvFocusState()
            val anyOffset = audioDelayMs != 0L || subtitleDelayMs != 0L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .then(resetFocus.focusModifier)
                        .tvFocusIndicator(resetFocus, CircleShape)
                        .clickable(enabled = anyOffset) {
                            audioDelayMs = 0L
                            subtitleDelayMs = 0L
                            onAudioDelayChange(0L)
                            onSubtitleDelayChange(0L)
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Refresh,
                            contentDescription = null,
                            tint = if (anyOffset) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Reset both",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (anyOffset) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayRow(
    label: String,
    delayMs: Long,
    focusRequester: FocusRequester?,
    isTv: Boolean,
    helperText: String,
    onValueChange: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))

        val delaySec = delayMs / 1000.0
        val valueLabel = when {
            delayMs == 0L -> "0.0s"
            delayMs > 0 -> "+${"%.1f".format(delaySec)}s"
            else -> "${"%.1f".format(delaySec)}s"
        }
        Text(
            valueLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isTv) {
                DelayStepper(icon = Tabler.Outline.Minus, description = "Decrease $label") {
                    val newDelay = (delayMs - 100L).coerceIn(-5000L, 5000L)
                    onValueChange(newDelay)
                }
            }

            if (isTv) {
                DpadSlider(
                    value = delayMs.toFloat(),
                    onValueChange = { onValueChange((it / 50f).roundToLong() * 50) },
                    valueRange = -5000f..5000f,
                    steps = 199,
                    dpadStep = 100f,
                    focusRequester = focusRequester,
                    modifier = Modifier.weight(1f),
                )
            } else {
                androidx.compose.material3.Slider(
                    value = delayMs.toFloat(),
                    onValueChange = { onValueChange((it / 50f).roundToLong() * 50) },
                    valueRange = -5000f..5000f,
                    steps = 199,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            if (isTv) {
                DelayStepper(icon = Tabler.Outline.Plus, description = "Increase $label") {
                    val newDelay = (delayMs + 100L).coerceIn(-5000L, 5000L)
                    onValueChange(newDelay)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DelayStepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}
