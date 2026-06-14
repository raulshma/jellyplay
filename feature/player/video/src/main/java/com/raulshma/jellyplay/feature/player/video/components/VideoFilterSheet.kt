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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.engine.VideoEffectsConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFilterSheet(
    currentEffects: VideoEffectsConfig,
    onEffectsChange: (VideoEffectsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    var brightness by remember { mutableFloatStateOf(currentEffects.brightness) }
    var contrast by remember { mutableFloatStateOf(currentEffects.contrast) }
    var saturation by remember { mutableFloatStateOf(currentEffects.saturation) }
    var sharpness by remember { mutableFloatStateOf(currentEffects.sharpness) }

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Video Filters",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(20.dp))

            FilterSlider(
                label = "Brightness",
                value = brightness,
                valueRange = -1f..1f,
                valueLabel = String.format("%+.1f", brightness),
                onValueChange = {
                    brightness = it
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                onReset = {
                    brightness = 0f
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                isTv = isTv,
                focusRequester = focusRequester,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilterSlider(
                label = "Contrast",
                value = contrast,
                valueRange = 0.5f..2f,
                valueLabel = String.format("%.1f", contrast),
                onValueChange = {
                    contrast = it
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                onReset = {
                    contrast = 1f
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                isTv = isTv,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilterSlider(
                label = "Saturation",
                value = saturation,
                valueRange = 0f..3f,
                valueLabel = String.format("%.1f", saturation),
                onValueChange = {
                    saturation = it
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                onReset = {
                    saturation = 1f
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                isTv = isTv,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilterSlider(
                label = "Sharpness",
                value = sharpness,
                valueRange = 0f..1f,
                valueLabel = String.format("%.1f", sharpness),
                onValueChange = {
                    sharpness = it
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                onReset = {
                    sharpness = 0f
                    onEffectsChange(VideoEffectsConfig(brightness, contrast, saturation, sharpness))
                },
                isTv = isTv,
            )
        }
    }
}

@Composable
private fun FilterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                
                val shape = ShapeCache.smoothPill
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .then(resetFocusState.focusModifier)
                        .tvFocusIndicator(resetFocusState, shape)
                        .clickable(onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (isTv) {
            DpadSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                focusRequester = focusRequester,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
