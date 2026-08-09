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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.audio.AudioEffectsState
import com.raulshma.jellyplay.feature.player.audio.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AudioEffectsSheet(
    state: AudioEffectsState,
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleEqualizer: () -> Unit,
    onToggleBassBoost: () -> Unit,
    onBassBoostStrength: (EffectStrength) -> Unit,
    onToggleVirtualizer: () -> Unit,
    onVirtualizerStrength: (Int) -> Unit,
    onReverbPreset: (ReverbPreset) -> Unit,
    onToggleDialogueBoost: () -> Unit,
    onDialogueBoostStrength: (EffectStrength) -> Unit,
    onToggleNightMode: () -> Unit,
    onNightModeStrength: (EffectStrength) -> Unit,
    onLrBalance: (Float) -> Unit,
    onPitchSemitones: (Float) -> Unit,
    onAutoEqByGenre: (Boolean) -> Unit,
) {
    PlayerModalBottomSheet(
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
                SheetHeader(
                    title = stringResource(R.string.audio_effects_title),
                    icon = Tabler.Outline.Adjustments,
                )
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
                        Text(stringResource(R.string.audio_menu_equalizer), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row {
                        TextButton(onClick = onOpenEqualizer) { Text(stringResource(R.string.audio_open)) }
                        Spacer(Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = state.equalizerEnabled,
                            onCheckedChange = { onToggleEqualizer() },
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                tint = if (state.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.audio_effects_bass_boost), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    state.bassBoostStrength.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = state.bassBoostEnabled,
                            onCheckedChange = { onToggleBassBoost() },
                        )
                    }
                    if (state.bassBoostEnabled) {
                        Spacer(Modifier.height(8.dp))
                        // Strength chips wrap to a new row below the toggle so they never
                        // overflow horizontally on narrow screens.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EffectStrength.entries.forEach { strength ->
                                val isSelected = state.bassBoostStrength == strength
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onBassBoostStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
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
                            tint = if (state.virtualizerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                                Text(stringResource(R.string.audio_effects_virtualizer), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${(state.virtualizerStrength / 10)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.virtualizerEnabled) {
                        Slider(
                            value = state.virtualizerStrength.toFloat(),
                            onValueChange = { onVirtualizerStrength(it.toInt()) },
                            valueRange = 0f..1000f,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.virtualizerEnabled,
                        onCheckedChange = { onToggleVirtualizer() },
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
                            tint = if (state.reverbPreset != ReverbPreset.NONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_effects_reverb), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val reverbPresets = ReverbPreset.entries
                    items(reverbPresets.size, key = { reverbPresets[it].name }) { index ->
                        val preset = reverbPresets[index]
                        val isSelected = state.reverbPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onReverbPreset(preset) },
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
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                tint = if (state.dialogueBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.audio_effects_dialogue_boost), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    state.dialogueBoostStrength.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = state.dialogueBoostEnabled,
                            onCheckedChange = { onToggleDialogueBoost() },
                        )
                    }
                    if (state.dialogueBoostEnabled) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EffectStrength.entries.forEach { strength ->
                                val isSelected = state.dialogueBoostStrength == strength
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onDialogueBoostStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
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
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                tint = if (state.nightModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.audio_effects_night_mode), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    state.nightModeStrength.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        androidx.compose.material3.Switch(
                            checked = state.nightModeEnabled,
                            onCheckedChange = { onToggleNightMode() },
                        )
                    }
                    if (state.nightModeEnabled) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EffectStrength.entries.forEach { strength ->
                                val isSelected = state.nightModeStrength == strength
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onNightModeStrength(strength) },
                                    label = { Text(strength.displayName, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(28.dp),
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
                            tint = if (state.lrBalance != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.audio_effects_lr_balance), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    state.lrBalance < 0f -> stringResource(R.string.audio_effects_balance_left, kotlin.math.abs((state.lrBalance * 100).toInt()))
                                    state.lrBalance > 0f -> stringResource(R.string.audio_effects_balance_right, (state.lrBalance * 100).toInt())
                                    else -> stringResource(R.string.audio_effects_balance_center)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = state.lrBalance,
                        onValueChange = { onLrBalance(it) },
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
                            tint = if (state.pitchSemitones != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.audio_effects_pitch), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (state.pitchSemitones == 0f) stringResource(R.string.audio_effects_pitch_original)
                                else stringResource(
                                    R.string.audio_effects_pitch_value,
                                    "${if (state.pitchSemitones > 0) "+" else ""}${String.format("%.1f", state.pitchSemitones)}",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = state.pitchSemitones,
                        onValueChange = { onPitchSemitones(it) },
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
                            tint = if (state.autoEqByGenre) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.audio_effects_auto_eq), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.audio_effects_auto_eq_description),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = state.autoEqByGenre,
                        onCheckedChange = { onAutoEqByGenre(it) },
                    )
                }
            }
        }
    }
}
