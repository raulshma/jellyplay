package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The reader's ONE commit-on-settle slider, extracted from three hand-rolled
 * copies of the same local-drag-state choreography (the chrome brightness
 * row, the paged page slider and the settings sheets' slider rows): the
 * thumb follows the finger while dragging, the persisted value otherwise,
 * and [onCommit] fires exactly once per drag with the settled raw value —
 * dragging must never commit per frame (the page-slider convention).
 *
 * Call sites stay thin: they wrap [CommitSlider] in their own chrome and
 * apply their settle fold — one of the pure functions below ([committedInRange],
 * [committedBrightnessPct], [pagedSliderSeekTarget]) — inside [onCommit], so
 * the value math is pinnable without Compose ([ReaderCommitSliderTest]).
 * [caption] slots the settings rows' live value label (drag position while
 * dragging, persisted value otherwise) under the track; the chrome bars pass
 * none.
 */

/** The commit-on-settle slider core. See the file header for the contract. */
@Composable
internal fun CommitSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    caption: (@Composable (displayedValue: Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val displayed = if (dragging) dragValue else value
    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        Slider(
            value = displayed,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onCommit(dragValue)
            },
            valueRange = valueRange,
            modifier = sliderModifier,
        )
    }
    if (caption == null) {
        slider(modifier)
    } else {
        Column(modifier = modifier) {
            slider(Modifier)
            caption(displayed)
        }
    }
}

/**
 * The settings sheets' shared label + slider + value-caption row (the former
 * CommitSliderRow from ReaderSheets.kt, now riding the shared [CommitSlider]).
 * [onCommit] receives the settled value coerced into [range] exactly once per
 * drag; [valueCaption] renders the live value (drag position while dragging,
 * persisted value otherwise).
 */
@Composable
internal fun CommitSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    valueCaption: @Composable (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        CommitSlider(
            value = value.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onCommit = { raw -> onCommit(committedInRange(raw, range)) },
            caption = { displayed ->
                Text(
                    text = valueCaption(displayed.roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/** The settings rows' settle fold: the settled raw value rounded and clamped into [range]. */
internal fun committedInRange(raw: Float, range: IntRange): Int =
    raw.roundToInt().coerceIn(range.first, range.last)

/**
 * The brightness row's settle fold: rounded and clamped into the 0..100
 * percent band — 100 renders no veil at all ([BrightnessDimOverlay]).
 */
internal fun committedBrightnessPct(raw: Float): Int = raw.roundToInt().coerceIn(0, 100)

/**
 * The paged page slider's settle fold: the 1-based drag position FLOORS (not
 * rounds — the thumb position is a page boundary) and clamps into the book,
 * then lands 0-based for the pager.
 */
internal fun pagedSliderSeekTarget(raw: Float, pageCount: Int): Int =
    raw.toInt().coerceIn(1, pageCount.coerceAtLeast(1)) - 1
