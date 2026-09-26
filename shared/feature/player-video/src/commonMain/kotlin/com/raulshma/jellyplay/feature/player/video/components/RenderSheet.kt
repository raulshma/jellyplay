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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Wand
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MpvRenderQuality
import com.raulshma.jellyplay.core.model.MpvShaderPack
import com.raulshma.jellyplay.core.model.MpvToneMapping
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader

/**
 * The "Rendering" sheet: shader pack, HDR→SDR tone mapping, render
 * quality, and the "save for this series" persistence toggle (off = this
 * session only; on = pinned to the series row, or the item row for
 * standalone movies). "Inherit (follow global)" clears the stored override.
 *
 * English literals match the sibling sheets (PlaybackModeSheet et al.) —
 * sheet bodies are the established hard-coded-string surface.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RenderSheet(
    shaderPack: MpvShaderPack,
    toneMapping: MpvToneMapping,
    renderQuality: MpvRenderQuality,
    hasStoredOverride: Boolean,
    canSaveForSeries: Boolean,
    saveForSeries: Boolean,
    onShaderPackSelect: (MpvShaderPack) -> Unit,
    onToneMappingSelect: (MpvToneMapping) -> Unit,
    onRenderQualitySelect: (MpvRenderQuality) -> Unit,
    onSaveForSeriesChange: (Boolean) -> Unit,
    onInherit: () -> Unit,
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
                title = "Rendering",
                icon = Tabler.Outline.Wand,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "GPU shader packs, tone mapping and quality profiles. " +
                    "Applies immediately; \"Save for this series\" pins it for future playback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            RenderSectionTitle("Shader Pack")
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MpvShaderPack.entries.forEach { pack ->
                    RenderChip(
                        label = pack.displayName,
                        selected = pack == shaderPack,
                        onClick = { onShaderPackSelect(pack) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            RenderSectionTitle("Tone Mapping (HDR → SDR)")
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MpvToneMapping.entries.forEach { mapping ->
                    RenderChip(
                        label = mapping.displayName,
                        selected = mapping == toneMapping,
                        onClick = { onToneMappingSelect(mapping) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            RenderSectionTitle("Quality Profile")
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MpvRenderQuality.entries.forEach { quality ->
                    RenderChip(
                        label = quality.displayName,
                        selected = quality == renderQuality,
                        onClick = { onRenderQualitySelect(quality) },
                    )
                }
            }

            if (canSaveForSeries) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Save for this series",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = saveForSeries,
                        onCheckedChange = onSaveForSeriesChange,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onInherit, enabled = hasStoredOverride) {
                    Text("Inherit (follow global)")
                }
            }
        }
    }
}

@Composable
private fun RenderSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = ShapeCache.smoothPill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            enabled = true,
            selected = selected,
        ),
    )
}
