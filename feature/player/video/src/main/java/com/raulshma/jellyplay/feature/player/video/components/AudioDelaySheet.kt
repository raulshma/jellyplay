package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ModalBottomSheet
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
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDelaySheet(
    currentDelayMs: Long,
    onDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var delayMs by remember { mutableLongStateOf(currentDelayMs) }
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
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
                "Audio Delay",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(20.dp))

            val delaySec = delayMs / 1000.0
            val label = when {
                delayMs == 0L -> "0.0s"
                delayMs > 0 -> "+${"%.1f".format(delaySec)}s (audio late)"
                else -> "${"%.1f".format(delaySec)}s (audio early)"
            }
            Text(
                label,
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
                    val decFocus = rememberTvFocusState()
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .then(decFocus.focusModifier)
                            .tvFocusIndicator(decFocus, CircleShape)
                            .clickable {
                                val newDelay = (delayMs - 100L).coerceIn(-5000L, 5000L)
                                delayMs = newDelay
                                onDelayChange(newDelay)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Tabler.Outline.Minus, contentDescription = "Decrease", tint = Color.White)
                    }
                }

                if (isTv) {
                    DpadSlider(
                        value = delayMs.toFloat(),
                        onValueChange = { delayMs = (it / 50f).roundToLong() * 50 },
                        onValueChangeFinished = { onDelayChange(delayMs) },
                        valueRange = -5000f..5000f,
                        steps = 199,
                        dpadStep = 100f,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    androidx.compose.material3.Slider(
                        value = delayMs.toFloat(),
                        onValueChange = { delayMs = (it / 50f).roundToLong() * 50 },
                        onValueChangeFinished = { onDelayChange(delayMs) },
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
                    val incFocus = rememberTvFocusState()
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .then(incFocus.focusModifier)
                            .tvFocusIndicator(incFocus, CircleShape)
                            .clickable {
                                val newDelay = (delayMs + 100L).coerceIn(-5000L, 5000L)
                                delayMs = newDelay
                                onDelayChange(newDelay)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Tabler.Outline.Plus, contentDescription = "Increase", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Negative values make audio play earlier. Positive values delay audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
