package com.raulshma.jellyplay.core.ui.tv.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DpadSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    dpadStep: Float = (valueRange.endInclusive - valueRange.start) / 20f,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
    ),
    focusRequester: FocusRequester? = null,
    indicatorShape: Shape = androidx.compose.ui.graphics.RectangleShape,
) {
    val focusState = rememberTvFocusState()

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, indicatorShape)
            .onDpadKey(
                onLeft = {
                    if (enabled) {
                        onValueChange((value - abs(dpadStep)).coerceIn(valueRange))
                        true
                    } else false
                },
                onRight = {
                    if (enabled) {
                        onValueChange((value + abs(dpadStep)).coerceIn(valueRange))
                        true
                    } else false
                },
            ),
    )
}
