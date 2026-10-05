package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.player.SpeedSlider
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Gauge

/**
 * The playback-speed rungs this sheet's chips offer — also the CYCLE_SPEED
 * input action's ladder (next rung up, wrapping to 1x), so a rebound key
 * steps the same speeds the sheet shows.
 */
internal val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedPickerSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheetScaffold(
        title = "Playback Speed",
        icon = Tabler.Outline.Gauge,
        onDismiss = onDismiss,
    ) { isTv, focusRequester ->
        if (isTv) {
            LazyColumn(modifier = Modifier.verticalWrapAround()) {
                items(
                    count = SPEED_OPTIONS.size,
                    key = { SPEED_OPTIONS[it] },
                    contentType = { "speed" },
                ) { i ->
                    val speed = SPEED_OPTIONS[i]
                    val isSelected = speed == currentSpeed
                    val isFirstOrSelected = isSelected || (SPEED_OPTIONS.none { it == currentSpeed } && speed == 1.0f)
                    TvFocusableOptionRow(
                        selected = isSelected,
                        onClick = { onSelect(speed); onDismiss() },
                        focusRequesterModifier = Modifier.ifElse(isFirstOrSelected, Modifier.focusRequester(focusRequester)),
                        trailingCheck = true,
                    ) {
                        TvOptionRowLabel(
                            text = if (speed == 1.0f) "Normal (1x)" else "${speed}x",
                            selected = isSelected,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SpeedSlider(
                currentSpeed = currentSpeed,
                onSelect = onSelect,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    val isSelected = speed == currentSpeed
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(speed); onDismiss() },
                        label = {
                            Text(
                                if (speed == 1.0f) "1x" else "${speed}x",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smoothPill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            enabled = true,
                            selected = isSelected,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SpeedSlider(
                currentSpeed = currentSpeed,
                onSelect = onSelect,
            )
        }
    }
}
