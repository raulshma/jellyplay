package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Plus
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.feature.player.video.SubtitleStylePresetPolicy
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_apply
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cancel
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_big_bold
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_classic_yellow
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_delete_a11y
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_name_label
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_netflixish
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_save
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_save_dialog_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_presets_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_preset_subtle
import org.jetbrains.compose.resources.stringResource

/**
 * The named style presets row: the four built-in looks (code, localized
 * labels) plus the user-named presets (persisted on the subtitle slice), a
 * save-current entry, and per-user-preset delete. Applying routes through the
 * host's `onStyleChange` (the existing [SubtitleStyleControls] edit path —
 * [com.raulshma.jellyplay.feature.player.video.SubtitleStyleController.setStyle]);
 * the look/persist bookkeeping lives in
 * [com.raulshma.jellyplay.feature.player.video.SubtitleStylePresetPolicy].
 *
 * Shared by the hub's Style tab and the standalone [SubtitleStyleSheet].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleStylePresetsRow(
    userPresets: List<SubtitleStylePreset>,
    onApplyPreset: (SubtitleStylePreset) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.player_video_presets_title),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Built-ins: code-owned looks, localized labels. A transient
            // SubtitleStylePreset carries the style through the same apply
            // fold the user presets use.
            SubtitleStylePresetPolicy.builtIns.forEach { builtIn ->
                val label = when (builtIn) {
                    SubtitleStylePresetPolicy.BuiltInPreset.SUBTLE ->
                        stringResource(Res.string.player_video_preset_subtle)
                    SubtitleStylePresetPolicy.BuiltInPreset.BIG_BOLD ->
                        stringResource(Res.string.player_video_preset_big_bold)
                    SubtitleStylePresetPolicy.BuiltInPreset.CLASSIC_YELLOW ->
                        stringResource(Res.string.player_video_preset_classic_yellow)
                    SubtitleStylePresetPolicy.BuiltInPreset.NETFLIXISH ->
                        stringResource(Res.string.player_video_preset_netflixish)
                }
                SubtitleStyleChip(
                    label = label,
                    selected = false,
                    onClick = {
                        onApplyPreset(SubtitleStylePreset(name = label, style = builtIn.style))
                    },
                )
            }

            // User presets: chip applies; the adjacent close affordance deletes.
            userPresets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubtitleStyleChip(
                        label = preset.name,
                        selected = false,
                        onClick = { onApplyPreset(preset) },
                    )
                    IconButton(
                        onClick = { onDeletePreset(preset.name) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Tabler.Outline.Trash,
                            contentDescription = stringResource(
                                Res.string.player_video_preset_delete_a11y,
                                preset.name,
                            ),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Save-current entry: names the CURRENT style (the host reads it
            // off its style state when the event fires).
            SubtitleStyleChip(
                label = stringResource(Res.string.player_video_preset_save),
                selected = false,
                onClick = { showSaveDialog = true },
            )
        }
    }

    if (showSaveDialog) {
        SavePresetNameDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                onSavePreset(name)
            },
        )
    }
}

@Composable
private fun SavePresetNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.player_video_preset_save_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.player_video_preset_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subtitle-preset-name"),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) { Text(stringResource(Res.string.player_video_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.player_video_cancel)) }
        },
    )
}
