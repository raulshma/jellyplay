package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.yearRangePresets
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_year_from
import com.raulshma.jellyplay.feature.library.generated.resources.library_year_to

/**
 * From/To year range selector for the library year-range filter sheets. Two
 * thumb sliders (one per bound) anchored to the same 1920→now domain as the
 * decade presets ([yearRangePresets]); each tick is one year. The bounds
 * cross-clamp each other so the range never inverts (From ≤ To).
 *
 * [current] seeds the slider positions from the active year selection
 * (min..max, or the full domain when nothing is selected). The change is
 * committed via [onRangeChange] on release only (commit-on-release), so a drag
 * doesn't fire a filter query per tick.
 *
 * Coordination with the decade presets: the slider and the preset chips feed
 * the same underlying year set, so committing a slider range **replaces** the
 * selection (it doesn't union onto toggled presets), and toggling a preset
 * after a custom range unions onto it. To start a fresh custom range after
 * mixing presets, tap "Any" first — this is the single clear-state affordance
 * both controls share.
 */
@Composable
fun CustomYearRangeSelector(
    current: Collection<Int>,
    onRangeChange: (IntRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = remember { yearRangePresets() }
    val domainStart = presets.first().years.first
    val domainEnd = presets.last().years.last
    val seedStart = current.minOrNull()?.coerceIn(domainStart, domainEnd) ?: domainStart
    val seedEnd = current.maxOrNull()?.coerceIn(domainStart, domainEnd) ?: domainEnd

    var from by remember(current) { mutableFloatStateOf(seedStart.toFloat()) }
    var to by remember(current) { mutableFloatStateOf(seedEnd.toFloat()) }
    val isTv = LocalTvMode.current

    // Full-domain track for both sliders (avoids degenerate zero-width ranges
    // at the extremes); the bounds are kept apart by clamping on change.
    val domain = domainStart.toFloat()..domainEnd.toFloat()
    val steps = domainEnd - domainStart - 1

    Column(modifier = modifier) {
        CustomYearRangeSlider(
            label = stringResource(Res.string.library_year_from, from.toInt()),
            icon = Tabler.Outline.CalendarPlus,
            value = from,
            onValueChange = { from = it.coerceAtMost(to - 1).coerceIn(domain) },
            onValueChangeFinished = {
                val range = from.toInt()..to.toInt()
                if (range.last >= range.first) onRangeChange(range)
            },
            domain = domain,
            steps = steps,
            isTv = isTv,
        )
        CustomYearRangeSlider(
            label = stringResource(Res.string.library_year_to, to.toInt()),
            icon = Tabler.Outline.Calendar,
            value = to,
            onValueChange = { to = it.coerceAtLeast(from + 1).coerceIn(domain) },
            onValueChangeFinished = {
                val range = from.toInt()..to.toInt()
                if (range.last >= range.first) onRangeChange(range)
            },
            domain = domain,
            steps = steps,
            isTv = isTv,
        )
    }
}

@Composable
private fun CustomYearRangeSlider(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    domain: ClosedFloatingPointRange<Float>,
    steps: Int,
    isTv: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp).size(width = 92.dp, height = 24.dp),
        )
        TvOrTouchSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = domain,
            isTv = isTv,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f),
        )
    }
}
