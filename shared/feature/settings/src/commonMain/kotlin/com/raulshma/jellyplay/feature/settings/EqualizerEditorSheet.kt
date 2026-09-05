package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_apply
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_equalizer

/**
 * The per-band equalizer editor sheet for [AudioSettingsScreen], co-located
 * beside it. This used to be the lone survivor of the per-screen dialog
 * hierarchies [PickerState] killed — a `sealed class AudioSettingsDialog
 * { None; EqualizerEditor }` identity tag plus an inline ~50-line
 * `TvSafeSheet` block inside the screen file. Now the screen opens it through
 * one boolean slot and this composable owns the whole sheet: band sliders
 * (-15…+15 dB per [EqualizerSettings.BAND_FREQUENCIES]), Cancel discarding
 * the edits, Apply committing one [EqualizerSettings] write.
 *
 * Not a [PickerState] variant on purpose: the band ladder is a bespoke
 * multi-slider editor with its own apply step, not a payload picker — folding
 * it in would grow the shared picker vocabulary for a single consumer.
 *
 * [bandLevels] keys the working copy, so a preferences change while the sheet
 * is open re-seeds it (the original `remember(bandLevels)` behavior).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerEditorSheet(
    visible: Boolean,
    bandLevels: List<Int>,
    onApply: (EqualizerSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val levels = remember(bandLevels) { mutableStateListOf<Int>().apply { addAll(bandLevels) } }
    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = stringResource(Res.string.settings_equalizer), icon = Tabler.Outline.WaveSine)
            LazyColumn(
                // KMP replacement for the Android-only LocalConfiguration.screenHeightDp:
                // the window container height in dp (shared/core/ui WindowSizeClass pattern).
                modifier = Modifier.heightIn(
                    max = with(LocalDensity.current) {
                        LocalWindowInfo.current.containerSize.height.toDp() * 0.5f
                    },
                ),
            ) {
                items(EqualizerSettings.BAND_FREQUENCIES.size, key = { it }, contentType = { "band" }) { i ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "${EqualizerSettings.BAND_FREQUENCIES[i]} Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("-15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            Slider(
                                value = (levels[i] + 15).toFloat(),
                                onValueChange = { levels[i] = (it - 15).toInt() },
                                valueRange = 0f..30f,
                                modifier = Modifier.weight(1f),
                            )
                            Text("+15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onApply(EqualizerSettings(levels.toList())) }) { Text(stringResource(Res.string.settings_apply)) }
            }
        }
    }
}
