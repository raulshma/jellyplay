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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackSpeedLadder
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.player_speed_slider_label
import com.raulshma.jellyplay.core.ui.generated.resources.player_speed_value
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import org.jetbrains.compose.resources.stringResource

/**
 * Shared playback-speed slider with label. Delegates to [TvOrTouchSlider] so it
 * works for both touch and D-pad/TV navigation. Used by the audio and video
 * speed-picker sheets.
 *
 * The slider geometry (range / step / D-pad step) reads the shared
 * [PlaybackSpeedLadder] — core:model owns the speed ladder, this composable
 * only renders it.
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
        // formatFixed(…, 2) in Kotlin: compose-resources can't substitute %f
        // placeholders, so the resource carries a %1$s string (issue #158).
        text = stringResource(Res.string.player_speed_slider_label) +
            "  " + stringResource(Res.string.player_speed_value, formatFixed(sliderValue.toDouble(), 2)),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    TvOrTouchSlider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        valueRange = PlaybackSpeedLadder.range,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        isTv = isTv,
        steps = PlaybackSpeedLadder.stepCount,
        dpadStep = PlaybackSpeedLadder.stepSize,
        onValueChangeFinished = { onSelect(sliderValue) },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
