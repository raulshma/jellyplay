package com.raulshma.jellyplay.core.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.R as CoreUiR

private val SPEED_RANGE = 0.25f..2.0f
private const val SPEED_STEPS = 34 // ((2.0 - 0.25) / 0.05) - 1
private const val DPAD_STEP = 0.05f

/**
 * Shared playback-speed slider with label. Delegates to [TvOrTouchSlider] so it
 * works for both touch and D-pad/TV navigation. Used by the audio and video
 * speed-picker sheets.
 */
@Composable
fun SpeedSlider(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    val isTv = LocalTvMode.current
    Text(
        text = stringResource(CoreUiR.string.player_speed_slider_label) +
            "  " + stringResource(CoreUiR.string.player_speed_value, sliderValue),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    TvOrTouchSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = SPEED_RANGE,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        isTv = isTv,
        steps = SPEED_STEPS,
        dpadStep = DPAD_STEP,
        onValueChangeFinished = { onSelect(sliderValue) },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
