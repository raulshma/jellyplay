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
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_quality_original_uncapped
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
 * The resolution tiers a source can actually deliver: transcoding never
 * upscales, so a tier above the source cannot honor its resolution meaning
 * and does not render as one (4K on a 1080p file). Quality classes are
 * width-conventional though ("1080p" = 1920 across), so the cap is judged
 * against the 16:9 height the source's width implies as well as its coded
 * height — a scope master (1920x800) still counts as 1080p. Unknown source
 * dimensions keep every tier; [StreamingQuality.AUTO] always stays.
 *
 * One tier is exempt from disappearing outright: the bitrate map resolves
 * [StreamingQuality.UHD_4K] to *no* bitrate cap, so on a metered connection
 * it is the only uncapped choice and dropping it would leave high-bitrate
 * playback unreachable from the picker. [offersOriginalUncapped] flags that
 * case and the UI keeps the tier as "Original (uncapped)" instead.
 */
internal fun offeredQualities(sourceWidth: Int?, sourceHeight: Int?): List<StreamingQuality> =
    StreamingQuality.entries.filter { quality ->
        val cap = quality.maxHeight ?: return@filter true
        if (sourceWidth == null && sourceHeight == null) return@filter true
        val widthClassHeight = (sourceWidth ?: 0) * 9 / 16
        cap <= maxOf(sourceHeight ?: 0, widthClassHeight)
    }

/**
 * True when the source cannot deliver 4K: the resolution rule above filters
 * [StreamingQuality.UHD_4K] out, and the UI keeps it offered under its other
 * identity — the never-quality-capped bitrate choice — labeled "Original
 * (uncapped)". "Uncapped" means no tier-derived cap: a manually configured
 * bandwidth cap still applies (see [com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager]).
 * Unknown dimensions keep every tier as plain resolution tiers, and a source
 * at or above 4K shows the tier as plain "4K" (the meanings coincide there).
 */
internal fun offersOriginalUncapped(sourceWidth: Int?, sourceHeight: Int?): Boolean {
    if (sourceWidth == null && sourceHeight == null) return false
    val cap = StreamingQuality.UHD_4K.maxHeight ?: return false
    val widthClassHeight = (sourceWidth ?: 0) * 9 / 16
    return cap > maxOf(sourceHeight ?: 0, widthClassHeight)
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
    // Below-4K sources keep the never-capped tier, relabeled "Original
    // (uncapped)", so metered playback always has a one-tap uncapped choice.
    val originalUncapped = offersOriginalUncapped(sourceWidth, sourceHeight)
    val rows = if (originalUncapped) offered + StreamingQuality.UHD_4K else offered
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
                    items(rows, key = { it.name }) { quality ->
                        QualityTvOptionRow(
                            quality = quality,
                            currentQuality = currentQuality,
                            offered = rows,
                            originalUncapped = originalUncapped,
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
                rows.forEach { quality ->
                    QualityTvOptionRow(
                        quality = quality,
                        currentQuality = currentQuality,
                        offered = rows,
                        originalUncapped = originalUncapped,
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
                rows.forEach { quality ->
                    val isSelected = quality == currentQuality
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(quality) },
                        label = {
                            Text(
                                qualityOptionLabel(quality, originalUncapped),
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
 * The row label for a quality tier. Below-4K sources present the never-capped
 * tier under its other identity — "Original (uncapped)" — instead of a
 * resolution the source can't deliver.
 */
@Composable
private fun qualityOptionLabel(
    quality: StreamingQuality,
    originalUncapped: Boolean,
): String =
    if (originalUncapped && quality == StreamingQuality.UHD_4K) {
        stringResource(Res.string.player_video_quality_original_uncapped)
    } else {
        quality.displayName
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
    originalUncapped: Boolean,
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
        TvOptionRowLabel(
            text = qualityOptionLabel(quality, originalUncapped),
            selected = isSelected,
        )
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
