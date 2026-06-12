package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.EqualizerPreset

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                Row {
                    androidx.compose.material3.TextButton(onClick = onReset) {
                        Text("Reset")
                    }
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val presets = EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }
                items(presets.size, key = { presets[it].name }) { index ->
                    val preset = presets[index]
                    androidx.compose.material3.FilterChip(
                        selected = currentPreset == preset,
                        onClick = { onPresetChange(preset) },
                        label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val frequencies = listOf("60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz", "14kHz", "16kHz")
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
