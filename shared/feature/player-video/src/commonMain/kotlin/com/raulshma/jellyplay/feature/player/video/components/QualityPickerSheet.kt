package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.verticalWrapAround
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_adaptive_bitrate_cap
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_adaptive_bitrate_off
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_adaptive_bitrate_on
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_quality_description
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_quality_source
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_quality_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_quality_unavailable
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Video

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QualityPickerSheet(
    currentQuality: StreamingQuality,
    adaptiveBitrateEnabled: Boolean,
    onToggleAdaptiveBitrate: (Boolean) -> Unit,
    onSelect: (StreamingQuality) -> Unit,
    onDismiss: () -> Unit,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null,
) {
    PickerSheetScaffold(
        title = stringResource(Res.string.player_video_quality_title),
        icon = Tabler.Outline.Video,
        subtitle = stringResource(Res.string.player_video_quality_description),
        onDismiss = onDismiss,
    ) { isTv, focusRequester ->
        QualityPickerOptions(
            currentQuality = currentQuality,
            adaptiveBitrateEnabled = adaptiveBitrateEnabled,
            onToggleAdaptiveBitrate = onToggleAdaptiveBitrate,
            // Standalone sheet: a pick confirms and closes. The combined
            // playback-options sheet embeds the options directly (no dismiss
            // on select) so it stays open across picks.
            onSelect = { quality ->
                onSelect(quality)
                onDismiss()
            },
            isTv = isTv,
            tvFocusRequester = focusRequester,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
    }
}

/**
 * The tiers a source can actually deliver: transcoding never upscales, so a
 * cap above the source is a no-op offer (4K on a 1080p file). Quality classes
 * are width-conventional though ("1080p" = 1920 across), so the cap is judged
 * against the 16:9 height the source's width implies as well as its coded
 * height — a scope master (1920x800) still counts as 1080p. Unknown source
 * dimensions keep every tier; [StreamingQuality.AUTO] always stays.
 */
internal fun offeredQualities(sourceWidth: Int?, sourceHeight: Int?): List<StreamingQuality> =
    StreamingQuality.entries.filter { quality ->
        val cap = quality.maxHeight ?: return@filter true
        if (sourceWidth == null && sourceHeight == null) return@filter true
        val widthClassHeight = (sourceWidth ?: 0) * 9 / 16
        cap <= maxOf(sourceHeight ?: 0, widthClassHeight)
    }

/**
 * The quality chip row + adaptive-bitrate toggle, shared by the standalone
 * quality sheet and the combined playback-options sheet (which embeds it so
 * one open sheet covers engine + delivery + quality). Never dismisses its
 * host — the caller decides what a pick does. [enabled] = false renders the
 * section disabled with the unavailable help text (forced direct play /
 * offline / cast / SyncPlay have no quality to pick).
 */
@Composable
fun QualityPickerOptions(
    currentQuality: StreamingQuality,
    adaptiveBitrateEnabled: Boolean,
    onToggleAdaptiveBitrate: (Boolean) -> Unit,
    onSelect: (StreamingQuality) -> Unit,
    isTv: Boolean,
    tvFocusRequester: FocusRequester,
    enabled: Boolean = true,
    /**
     * TV only: keep the rows in their own [LazyColumn] (standalone sheet).
     * Pass `false` when embedding this section in a parent lazy list — a lazy
     * list nested in another lazy list item (or in a `verticalScroll` column)
     * is measured with infinite height and crashes.
     */
    tvLazyRows: Boolean = true,
    sourceWidth: Int? = null,
    sourceHeight: Int? = null,
) {
    if (!enabled) {
        Text(
            text = stringResource(Res.string.player_video_quality_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        return
    }
    val offered = offeredQualities(sourceWidth, sourceHeight)
    // The "Source: WxH" line: tiers key on the source's coded resolution, and
    // on scope masters the width-matched tiers (1080p on a 1920x800 file)
    // only make sense once the real frame size is visible.
    val showSourceCaption = sourceWidth != null && sourceHeight != null
    if (isTv) {
        if (tvLazyRows) {
            Column {
                if (showSourceCaption) {
                    QualitySourceCaption(sourceWidth, sourceHeight)
                }
                LazyColumn(modifier = Modifier.verticalWrapAround()) {
                    items(offered, key = { it.name }) { quality ->
                        QualityTvOptionRow(
                            quality = quality,
                            currentQuality = currentQuality,
                            offered = offered,
                            onSelect = onSelect,
                            tvFocusRequester = tvFocusRequester,
                        )
                    }
                }
            }
        } else {
            Column {
                if (showSourceCaption) {
                    QualitySourceCaption(sourceWidth, sourceHeight)
                }
                offered.forEach { quality ->
                    QualityTvOptionRow(
                        quality = quality,
                        currentQuality = currentQuality,
                        offered = offered,
                        onSelect = onSelect,
                        tvFocusRequester = tvFocusRequester,
                    )
                }
            }
        }
    } else {
        Column {
            if (showSourceCaption) {
                QualitySourceCaption(sourceWidth, sourceHeight)
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                offered.forEach { quality ->
                    val isSelected = quality == currentQuality
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(quality) },
                        label = {
                            Text(
                                quality.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
                            selected = isSelected,
                        ),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    AdaptiveBitrateToggle(
        enabled = adaptiveBitrateEnabled,
        onToggle = onToggleAdaptiveBitrate,
    )
}

/**
 * The "Source: WxH" line above the tier list: tiers key on the source's coded
 * resolution, and on scope masters the width-matched tiers (1080p on a
 * 1920x800 file) only make sense once the real frame size is visible.
 */
@Composable
private fun QualitySourceCaption(sourceWidth: Int?, sourceHeight: Int?) {
    if (sourceWidth == null || sourceHeight == null) return
    Text(
        text = stringResource(Res.string.player_video_quality_source, sourceWidth, sourceHeight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

/**
 * One TV quality row. Kept out of [QualityPickerOptions] so the standalone
 * sheet (own [LazyColumn]) and the combined playback-options sheet (parent
 * lazy list, [QualityPickerOptions]' `tvLazyRows = false`) render identical
 * rows.
 */
@Composable
private fun QualityTvOptionRow(
    quality: StreamingQuality,
    currentQuality: StreamingQuality,
    offered: List<StreamingQuality>,
    onSelect: (StreamingQuality) -> Unit,
    tvFocusRequester: FocusRequester,
) {
    val isSelected = quality == currentQuality
    // A saved quality the source can't deliver anymore stays selected in the
    // state but has no row here; land focus on Auto in that case.
    val isFirstOrSelected = isSelected ||
        (currentQuality !in offered && quality == StreamingQuality.AUTO)
    TvFocusableOptionRow(
        selected = isSelected,
        onClick = { onSelect(quality) },
        focusRequesterModifier = Modifier.ifElse(
            isFirstOrSelected,
            Modifier.focusRequester(tvFocusRequester),
        ),
        trailingCheck = true,
    ) {
        TvOptionRowLabel(text = quality.displayName, selected = isSelected)
    }
}

@Composable
private fun AdaptiveBitrateToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(ShapeCache.smooth8)
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = stringResource(Res.string.player_video_adaptive_bitrate_cap),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (enabled) {
                    stringResource(Res.string.player_video_adaptive_bitrate_on)
                } else {
                    stringResource(Res.string.player_video_adaptive_bitrate_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}
