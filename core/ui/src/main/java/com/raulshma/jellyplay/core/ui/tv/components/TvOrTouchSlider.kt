package com.raulshma.jellyplay.core.ui.tv.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/**
 * Dual-input slider that renders a [DpadSlider] on TV form factors and a plain
 * touch [Slider] elsewhere. Collapses the repeated
 * `if (isTv) DpadSlider(...) else Slider(...)` block that was copy-pasted across
 * the player sheets (`SubtitleStyleControls` × 6, `AVSyncSheet`, `VideoFilterSheet`).
 *
 * The two underlying composables take the same core parameters; the only
 * TV-specific addition is [dpadStep], which governs the per-press nudge when the
 * user scrolls with the D-pad. Callers pass [onValueChangeFinished] to opt into
 * commit-on-release semantics (the touch Slider path uses it; the DpadSlider
 * path invokes it after each D-pad step so a commit lands per nudge).
 *
 * Default colors match [SliderDefaults.colors] (primary thumb + track); sites
 * that gate the color on an `enabled`/`applyCustomStyle` flag pass their own
 * [colors].
 *
 * @param isTv drives the D-pad vs touch branch. Caller hoists this (typically
 *  from `LocalIsTvContext.current` or an injected layout sense) so this
 *  composable stays free of context reads and stays preview/test-friendly.
 * @param dpadStep nudge size for D-pad left/right presses. Ignored on touch.
 * @param focusRequester optional TV focus order hint; forwarded to [DpadSlider]
 *  only (touch Slider ignores it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvOrTouchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    isTv: Boolean,
    enabled: Boolean = true,
    steps: Int = 0,
    dpadStep: Float = (valueRange.endInclusive - valueRange.start) / 20f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
    ),
    focusRequester: FocusRequester? = null,
) {
    if (isTv) {
        DpadSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
            dpadStep = dpadStep,
            enabled = enabled,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            focusRequester = focusRequester,
        )
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
            enabled = enabled,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished ?: {},
            colors = colors,
        )
    }
}
