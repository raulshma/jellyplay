package com.raulshma.jellyplay.components

import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider
import kotlinx.coroutines.flow.StateFlow

/**
 * The cast seek slider shared by the Play On mini bar and the companion
 * screen — ONE home for the leaf collection + drag arbitration so the two
 * surfaces cannot drift. Collects the per-tick cast position/duration
 * streams only here, at the leaf slider that renders them, so the ~1 Hz
 * position tick invalidates just this component — never the rows/hosts
 * above it (same leaf-collection rule as the video player's
 * ChapterPickerBinder).
 *
 * Drag-vs-server-push arbitration, mirroring TvControllableSeekBar
 * (PlayerControls): while the thumb is down the local mirror wins — a
 * position tick must not fight the drag — and the commit fires on release,
 * after which the thumb resumes following the server position. Unkeyed
 * `remember`: a `remember(positionMs)` + LaunchedEffect pair reallocates
 * the state and restarts a coroutine on every tick.
 */
@Composable
internal fun PlayOnCastSeekSlider(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onSeek: (Long) -> Unit,
    colors: SliderColors,
    modifier: Modifier = Modifier,
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val durationMs by durationMsFlow.collectAsStateWithLifecycle()
    val range = 0f..durationMs.toFloat().coerceAtLeast(1f)

    var dragPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    DpadSlider(
        value = (if (isDragging) dragPosition else positionMs.toFloat()).coerceIn(range),
        onValueChange = {
            isDragging = true
            dragPosition = it
        },
        onValueChangeFinished = {
            onSeek(dragPosition.toLong())
            isDragging = false
        },
        valueRange = range,
        colors = colors,
        modifier = modifier,
    )
}

/**
 * The cast volume slider — the volume twin of [PlayOnCastSeekSlider], ONE
 * home shared by the mini bar and the companion screen so the two surfaces
 * cannot drift (only their colors differ). The flow is collected only at
 * this leaf; volume rides the same play-state push as the position tick.
 * No drag arbitration: volume commits continuously (`onValueChange` is the
 * commit) and the server volume simply follows.
 */
@Composable
internal fun PlayOnCastVolumeSlider(
    volumeFlow: StateFlow<Float>,
    onVolume: (Float) -> Unit,
    colors: SliderColors,
    modifier: Modifier = Modifier,
) {
    val volume by volumeFlow.collectAsStateWithLifecycle()
    DpadSlider(
        value = volume,
        onValueChange = onVolume,
        valueRange = 0f..1f,
        colors = colors,
        modifier = modifier,
    )
}
