package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_equalizer_title
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_reset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    enabled: Boolean,
    bandLevels: List<Int>,
    currentPreset: EqualizerPreset,
    onToggle: () -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onPresetChange: (EqualizerPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.audio_equalizer_title),
                    icon = Tabler.Outline.WaveSine,
                trailing = {
                    TextButton(onClick = onReset) {
                        Text(stringResource(Res.string.audio_reset))
                    }
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                    )
                },
            )
            Spacer(Modifier.height(20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val presets = EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }
                items(presets.size, key = { presets[it].name }) { index ->
                    val preset = presets[index]
                    val isSelected = currentPreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetChange(preset) },
                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) },
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
            Spacer(Modifier.height(12.dp))
            // Frequency bands are wrapped in a scrollable, height-constrained column so the
            // sheet content never overflows on small screens (10 bands can't all fit at once).
            val frequencies = listOf("60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz", "14kHz", "16kHz")
            SheetSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    frequencies.forEachIndexed { index, freq ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                freq,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(48.dp),
                            )
                            Slider(
                                value = bandLevels.getOrElse(index) { 0 }.toFloat(),
                                onValueChange = { onBandChange(index, it.toInt()) },
                                valueRange = -1500f..1500f,
                                steps = 30,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(bandLevels.getOrElse(index) { 0 } / 100.0)}dB",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
