package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronRight
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Pre-download picker sheet: the storage confirmation (estimated size +
 * available storage) promoted from the former storage-confirmation dialog, plus a
 * download-quality chip row and an external-subtitle multi-select list.
 *
 * Driven by [DetailContentState.downloadPicker] (visibility / quality /
 * [SubtitleSelection]); the confirm action hands off to
 * [DetailContentCallbacks.onDownloadClick] (→ [DetailViewModel.startDownload]),
 * which reads the pending values and runs the cellular-size gate before the
 * transfer. Quality maps to `maxBitrate` via `qualityToMaxBitrate`; subtitle
 * selection narrows the external subtitles bundled offline
 * ([SubtitleSelection.All] = every deliverable subtitle).
 *
 * Cellular-warning flow is unchanged: if the cellular threshold is hit, this
 * sheet closes and the standalone cellular ConfirmDialog takes over — the
 * pending quality/subtitle selection persists through that confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadPickerSheet(
    fileSize: Long?,
    isAudio: Boolean,
    availableStorageProvider: suspend (Boolean) -> Long,
    subtitleStreams: List<MediaStream>,
    pendingQuality: DownloadQuality,
    pendingSubtitleSelection: SubtitleSelection,
    onPendingQualityChange: (DownloadQuality) -> Unit,
    onPendingSubtitleSelectionChange: (SubtitleSelection) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val availableBytes by produceState(initialValue = 0L, isAudio) {
        value = availableStorageProvider(isAudio)
    }
    val fileSizeText = fileSize?.let { size ->
        when {
            size >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, size / 1_000_000_000.0)
            size >= 1_000_000 -> stringResource(R.string.detail_size_mb, size / 1_000_000.0)
            size >= 1_000 -> stringResource(R.string.detail_size_kb, size / 1_000.0)
            else -> stringResource(R.string.detail_size_b, size)
        }
    } ?: stringResource(R.string.detail_size_unknown)
    val availableText = when {
        availableBytes >= 1_000_000_000 -> stringResource(R.string.detail_size_gb, availableBytes / 1_000_000_000.0)
        availableBytes >= 1_000_000 -> stringResource(R.string.detail_size_mb, availableBytes / 1_000_000.0)
        else -> stringResource(R.string.detail_size_kb, availableBytes / 1_000_000.0)
    }
    val enoughSpace = fileSize == null || fileSize <= availableBytes

    // All-subtitle indices, used to resolve SubtitleSelection.All to the full
    // set for rendering + toggling, and to detect when a toggled subset once
    // again covers every subtitle (collapse back to All).
    val allSubtitleIndices = remember(subtitleStreams) {
        subtitleStreams.map { it.index }.toSet()
    }
    val checkedIndices: Set<Int> = when (val selection = pendingSubtitleSelection) {
        SubtitleSelection.All -> allSubtitleIndices
        is SubtitleSelection.Subset -> selection.indices
    }
    // Hoisted out of the items() lambda so the label fallback isn't a
    // conditionally-invoked @Composable (stringResource) — mirrors
    // MediaStreamPicker's subtitle option builder.
    val subtitleTrackFormat = stringResource(R.string.detail_subtitle_track_format)

    // One-line summary of the pending quality + subtitle selection, shown on the
    // collapsed Advanced row so the defaults are legible without expanding.
    val subtitleSummary = when {
        subtitleStreams.isEmpty() -> null
        pendingSubtitleSelection is SubtitleSelection.All ->
            stringResource(R.string.detail_download_subtitles_all)
        checkedIndices.isEmpty() ->
            stringResource(R.string.detail_download_subtitles_none)
        else -> pluralStringResource(
            R.plurals.detail_download_subtitles_count,
            checkedIndices.size,
            checkedIndices.size,
        )
    }
    val advancedHint = if (subtitleSummary != null) {
        stringResource(
            R.string.detail_download_advanced_hint_quality_subtitles,
            pendingQuality.displayName,
            subtitleSummary,
        )
    } else {
        pendingQuality.displayName
    }

    TvSafeSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.detail_download_dialog_title),
    ) {
        // TvSafeSheet's TV path already insets content 24dp and spaces it 16dp;
        // the mobile bottom-sheet path provides neither, so apply the side inset
        // here only on mobile and let both render paths share one spaced layout.
        val isTv = LocalJellyPlayUi.current.isTv
        val sideInset = if (isTv) 0.dp else 24.dp
        var advancedExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sideInset)
                .padding(bottom = sideInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Storage gate (carried over from the former confirmation dialog) ──
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.detail_estimated_size, fileSizeText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.detail_available_storage, availableText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!enoughSpace) {
                    Text(
                        text = stringResource(R.string.detail_not_enough_storage),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── Advanced (quality + subtitles), collapsed by default ──
            AdvancedToggleRow(
                expanded = advancedExpanded,
                hint = advancedHint,
                onClick = { advancedExpanded = !advancedExpanded },
            )

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ── Quality chips (single-select) ──
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.detail_download_quality),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DownloadQuality.entries.forEach { quality ->
                                PickerChip(
                                    label = quality.displayName,
                                    selected = quality == pendingQuality,
                                    onClick = { onPendingQualityChange(quality) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // ── External subtitle multi-select ──
                    // Note: spec §1.2 also floated an audio multi-select, but downloads use
                    // a plain stream URL (PlaybackRepository.getStreamUrl carries no
                    // AudioStreamIndex), so a track picker would have no effect on the
                    // fetched file — audio selection is intentionally deferred until the
                    // download path routes through PlaybackInfo with an audio index.
                    if (subtitleStreams.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.detail_local_subtitles),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(
                                    subtitleStreams,
                                    key = { "dl_sub_${it.index}" },
                                    contentType = { "dlSubOption" },
                                ) { stream ->
                                    val isChecked = stream.index in checkedIndices
                                    SubtitleToggleRow(
                                        label = stream.displayTitle
                                            ?: stream.title
                                            ?: stream.language
                                            ?: subtitleTrackFormat.format(stream.index),
                                        isChecked = isChecked,
                                        isDefault = stream.isDefault,
                                        onClick = {
                                            val next = if (isChecked) {
                                                checkedIndices - stream.index
                                            } else {
                                                checkedIndices + stream.index
                                            }
                                            // When the toggled set again covers every subtitle,
                                            // collapse back to All so the default round-trips.
                                            onPendingSubtitleSelectionChange(
                                                if (next == allSubtitleIndices) SubtitleSelection.All
                                                else SubtitleSelection.Subset(next),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Actions ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.detail_cancel))
                }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onConfirm, enabled = enoughSpace) {
                    Text(stringResource(R.string.detail_download_dialog_title))
                }
            }
        }
    }
}

/**
 * Collapsible header for the quality + subtitle pickers. Shows a one-line hint of
 * the current pending selection so the defaults stay legible while collapsed, and
 * a chevron that swaps [ChevronRight] → [ChevronDown] with [expanded]. Mirrors
 * the focusable-row treatment used by [com.raulshma.jellyplay.feature.details.ManageSeriesScreen]'s
 * season header so D-pad focus reads consistently on TV.
 */
@Composable
private fun AdvancedToggleRow(
    expanded: Boolean,
    hint: String,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Tabler.Outline.AdjustmentsHorizontal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detail_download_advanced),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Tabler.Outline.ChevronDown else Tabler.Outline.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Compact single-select chip for the download-quality row. Mirrors the
 * [QuickInfoPill] aesthetic (primary-tinted fill when selected, TV-focusable)
 * so the picker feels native to the detail screen.
 */
@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.04f)
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        label = "pickerChipContainer",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(ShapeCache.smooth14)
            .background(container)
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth14))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * Multi-select row for an external subtitle stream — checked/unchecked state
 * drives the pending index set. Reuses the selectable-row look from
 * [MediaInfoSection]'s picker so the two sheets read consistently.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubtitleToggleRow(
    label: String,
    isChecked: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "subtitleToggleScale",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    val container by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        label = "subtitleToggleContainer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .background(container)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isDefault) {
            Text(
                text = stringResource(R.string.detail_stream_default_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (isChecked) {
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
