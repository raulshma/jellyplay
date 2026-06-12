package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioEffectsSheet(
    viewModel: AudioPlayerViewModel,
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Audio Effects", style = MaterialTheme.typography.titleMedium)
            }

            item {
                androidx.compose.material3.HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Tabler.Outline.Adjustments, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Equalizer", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row {
                        TextButton(onClick = onOpenEqualizer) { Text("Open") }
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = viewModel.equalizerEnabled,
                            onCheckedChange = { viewModel.toggleEqualizer() },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.WaveSine,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Bass Boost", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                viewModel.bassBoostStrength.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.bassBoostEnabled) {
                            EffectStrength.entries.forEach { strength ->
                                androidx.compose.material3.FilterChip(
                                    selected = viewModel.bassBoostStrength == strength,
                                    onClick = { viewModel.setBassBoostStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = viewModel.bassBoostEnabled,
                            onCheckedChange = { viewModel.toggleBassBoost() },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Speakerphone,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.virtualizerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Virtualizer / Spatial", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${(viewModel.virtualizerStrength / 10)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (viewModel.virtualizerEnabled) {
                        Slider(
                            value = viewModel.virtualizerStrength.toFloat(),
                            onValueChange = { viewModel.applyVirtualizerStrength(it.toInt()) },
                            valueRange = 0f..1000f,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = viewModel.virtualizerEnabled,
                        onCheckedChange = { viewModel.toggleVirtualizer() },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.WaveSine,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.reverbPreset != ReverbPreset.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reverb", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val reverbPresets = ReverbPreset.entries
                    items(reverbPresets.size, key = { reverbPresets[it].name }) { index ->
                        val preset = reverbPresets[index]
                        androidx.compose.material3.FilterChip(
                            selected = viewModel.reverbPreset == preset,
                            onClick = { viewModel.applyReverbPreset(preset) },
                            label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Microphone2,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.dialogueBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Dialogue Boost", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                viewModel.dialogueBoostStrength.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.dialogueBoostEnabled) {
                            EffectStrength.entries.forEach { strength ->
                                androidx.compose.material3.FilterChip(
                                    selected = viewModel.dialogueBoostStrength == strength,
                                    onClick = { viewModel.setDialogueBoostStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = viewModel.dialogueBoostEnabled,
                            onCheckedChange = { viewModel.toggleDialogueBoost() },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Moon,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.nightModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Night Mode", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                viewModel.nightModeStrength.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.nightModeEnabled) {
                            EffectStrength.entries.forEach { strength ->
                                androidx.compose.material3.FilterChip(
                                    selected = viewModel.nightModeStrength == strength,
                                    onClick = { viewModel.setNightModeStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = viewModel.nightModeEnabled,
                            onCheckedChange = { viewModel.toggleNightMode() },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Scale,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.lrBalance != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("L/R Balance", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    viewModel.lrBalance < 0f -> "Left ${kotlin.math.abs((viewModel.lrBalance * 100).toInt())}%"
                                    viewModel.lrBalance > 0f -> "Right ${(viewModel.lrBalance * 100).toInt()}%"
                                    else -> "Center"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = viewModel.lrBalance,
                        onValueChange = { viewModel.applyLrBalance(it) },
                        valueRange = -1f..1f,
                        modifier = Modifier.width(140.dp),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Piano,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.pitchSemitones != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Pitch", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (viewModel.pitchSemitones == 0f) "Original"
                                else "${if (viewModel.pitchSemitones > 0) "+" else ""}${String.format("%.1f", viewModel.pitchSemitones)} semitones",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = viewModel.pitchSemitones,
                        onValueChange = { viewModel.applyPitchSemitones(it) },
                        valueRange = -12f..12f,
                        steps = 23,
                        modifier = Modifier.width(140.dp),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.Wand,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = if (viewModel.autoEqByGenre) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Auto-EQ by Genre", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Apply EQ preset based on track genre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = viewModel.autoEqByGenre,
                        onCheckedChange = { viewModel.applyAutoEqByGenre(it) },
                    )
                }
            }
        }
    }
}
