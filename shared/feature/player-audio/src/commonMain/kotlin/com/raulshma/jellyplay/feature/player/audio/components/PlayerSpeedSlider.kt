package com.raulshma.jellyplay.feature.player.audio.components

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
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.player_speed_slider_label
import com.raulshma.jellyplay.core.ui.generated.resources.player_speed_value
import org.jetbrains.compose.resources.stringResource

private val SPEED_RANGE = 0.25f..2.0f
private const val SPEED_STEPS = 34 // ((2.0 - 0.25) / 0.05) - 1
private const val DPAD_STEP = 0.05f

/**
 * Playback-speed slider with label (wave 7A conveyor): byte-same body as the
 * legacy `:core:ui` `com.raulshma.jellyplay.core.ui.player.SpeedSlider` the
 * speed-picker sheet used to import — that composable is still legacy-module
 * only (the video player's sheet keeps using it there), so this module-local
 * copy rides the shared TvOrTouchSlider + the public core-ui string accessors
 * (CoreUiRes alias, livetv RecordingsScreen precedent). Collapses back into
 * one shared component when the remaining player modules flip.
 */
@Composable
internal fun PlayerSpeedSlider(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    val isTv = LocalTvMode.current
    Text(
        text = stringResource(CoreUiRes.string.player_speed_slider_label) +
            "  " + stringResource(CoreUiRes.string.player_speed_value, sliderValue),
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
