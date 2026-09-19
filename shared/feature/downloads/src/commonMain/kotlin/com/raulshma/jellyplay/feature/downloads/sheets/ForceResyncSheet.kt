package com.raulshma.jellyplay.feature.downloads.sheets

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ResyncCategory
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.episodeContextLine
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.downloads.ForceResyncCandidate
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_select_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_close
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_backdrop
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_backdrop_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_chapters_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_header
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_metadata
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_metadata_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_poster
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_poster_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_segments
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_segments_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_subtitles
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_subtitles_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_trickplay
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_data_trickplay_desc
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_description
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_done
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_empty
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_in_progress
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_items_header
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_no_data
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_summary
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_force_resync_title
import org.jetbrains.compose.resources.stringResource

/**
 * Force-resync sheet: a user-directed resync over an explicit set of downloaded
 * items, refreshing only the selected data categories (metadata / poster /
 * backdrop). Two phases — a picker (items + data checkboxes) and a progress
 * state that reuses [ResyncBatchProgress] from the sync manager, matching the
 * regular resync sheet's progress granularity.
 *
 * Entry is via the resync sheet's header action, so the resync icon remains the
 * single freshness hub. Mirrors the unified detail tree's
 * `DeleteDownloadedEpisodesSheet` multi-select pattern (tri-state select-all
 * header + per-item checkboxes) for consistency.
 *
 * Three phases, derived from live [progress] + the local [started] latch:
 *  - **picker** (default): editable items + data checkboxes;
 *  - **running**: read-only aggregate progress while [ResyncBatchProgress.active];
 *  - **done**: terminal view once a sync this sheet started has finished.
 * The `started` latch is keyed on [ResyncBatchProgress] so that reopening the
 * sheet while a background batch is still running (after a mid-batch dismiss)
 * surfaces running progress rather than a fresh editable picker.
 *
 * @param candidates downloaded items available for selection, with episode context.
 * @param progress live batch progress, shared with the regular resync flow.
 * @param onSync invoked with the selected item ids and data options.
 * @param onDismiss closes the sheet and clears batch progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForceResyncSheet(
    candidates: List<ForceResyncCandidate>,
    progress: com.raulshma.jellyplay.core.model.ResyncBatchProgress,
    onSync: (itemIds: List<String>, options: com.raulshma.jellyplay.core.model.ResyncOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Selection lives in the sheet so it resets on each open. Defaulting data
    // to all-on matches the historical resync behaviour; items start empty so
    // the user must opt in (an accidental full-library resync is costly).
    var selectedIds by remember { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }
    var selectedOptions by remember {
        androidx.compose.runtime.mutableStateOf(com.raulshma.jellyplay.core.model.ResyncOptions.ALL)
    }
    // Latch: sticky once a sync has been kicked off (or is still running from a
    // prior mid-batch dismiss). Derived purely from live progress so an
    // orphaned background batch latches this sheet straight into the running/
    // done phase on reopen instead of offering an editable picker.
    val started = progress.active || progress.completed > 0
    // Phase precedence: a still-active batch (this sheet or orphaned) shows
    // running progress; once it finishes the sheet holds a terminal done view
    // until dismissed, so the editable picker never returns with a stale
    // selection that could be re-fired accidentally.
    val showRunning = progress.active
    val showDone = started && !progress.active

    TvSafeSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Tabler.Outline.RefreshAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.downloads_force_resync_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (showRunning) {
                // Running phase: mirror the regular resync sheet's aggregate line.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyPlayCircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_force_resync_in_progress, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showDone) {
                // Terminal phase: a sync this sheet started has finished. Holds
                // a read-only summary until dismissed so the editable picker —
                // and its prior selection — never returns to be re-fired.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Tabler.Outline.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(Res.string.downloads_force_resync_done, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Picker phase: the whole content scrolls so long item lists
                // (and the data section beneath them) stay reachable on small
                // screens. The header above and the footer below are pinned.
                // heightIn caps the scroll viewport regardless of the sheet's
                // height constraints (a ModalBottomSheet content column isn't
                // guaranteed to bound a weighted child), matching the regular
                // resync sheet's capped list.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.downloads_force_resync_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (candidates.isEmpty()) {
                        Text(
                            stringResource(Res.string.downloads_force_resync_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // ── Items section ──────────────────────────────────────
                        Text(
                            stringResource(Res.string.downloads_force_resync_items_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val triState = when {
                            selectedIds.isEmpty() -> ToggleableState.Off
                            selectedIds.size == candidates.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        val selectAllFocusState = rememberTvFocusState(focusedScale = 1.01f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(selectAllFocusState.focusModifier)
                                .tvFocusIndicator(selectAllFocusState, ShapeCache.smooth12)
                                .clickable {
                                    selectedIds = if (triState == ToggleableState.On) emptySet()
                                    else candidates.map { it.id }.toSet()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(state = triState, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(Res.string.downloads_action_select_all),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(candidates, key = { _, candidate -> candidate.id }) { _, candidate ->
                                ForceResyncItemRow(
                                    candidate = candidate,
                                    checked = candidate.id in selectedIds,
                                    onToggle = {
                                        selectedIds = if (candidate.id in selectedIds) selectedIds - candidate.id
                                        else selectedIds + candidate.id
                                    },
                                )
                            }
                        }

                        // ── Data section ───────────────────────────────────────
                        Text(
                            stringResource(Res.string.downloads_force_resync_data_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ResyncCategory.entries.forEach { category ->
                            val strings = category.stringRes()
                            ForceResyncDataRow(
                                label = stringResource(strings.labelRes),
                                description = stringResource(strings.descRes),
                                checked = category in selectedOptions,
                                onToggle = {
                                    selectedOptions =
                                        if (category in selectedOptions) selectedOptions - category
                                        else selectedOptions + category
                                },
                            )
                        }
                        if (selectedOptions.isEmpty) {
                            Text(
                                stringResource(Res.string.downloads_force_resync_no_data),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Footer: Close always renders (all phases — a running/done batch
            // still needs a way out, and closing mid-batch leaves progress to
            // complete in the background, matching the regular resync sheet).
            // In the picker phase the primary Sync action sits beside it so the
            // two terminal controls share a row.
            val canSync = !showRunning && !showDone && selectedIds.isNotEmpty() && !selectedOptions.isEmpty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.downloads_close))
                }
                androidx.compose.material3.Button(
                    onClick = { onSync(selectedIds.toList(), selectedOptions) },
                    modifier = Modifier.weight(1f),
                    enabled = canSync,
                ) {
                    Icon(Tabler.Outline.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.downloads_force_resync_summary, selectedIds.size))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ForceResyncItemRow(
    candidate: ForceResyncCandidate,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Episode context (SXXEXX · series) so episodes are identifiable in
            // the picker — same shape and styling as the downloads list row.
            episodeContextLine(
                mediaType = candidate.mediaType,
                seriesName = candidate.seriesName,
                seasonNumber = candidate.seasonNumber,
                episodeNumber = candidate.episodeNumber,
            )?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Label + description string resources for one resync data-category row,
 *  exhaustive so a new [ResyncCategory] fails compilation until it gets copy. */
private data class ResyncCategoryStrings(
    val labelRes: org.jetbrains.compose.resources.StringResource,
    val descRes: org.jetbrains.compose.resources.StringResource,
)

private fun ResyncCategory.stringRes(): ResyncCategoryStrings = when (this) {
    ResyncCategory.METADATA ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_metadata,
            descRes = Res.string.downloads_force_resync_data_metadata_desc,
        )
    ResyncCategory.CHAPTERS ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_chapters,
            descRes = Res.string.downloads_force_resync_data_chapters_desc,
        )
    ResyncCategory.POSTER ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_poster,
            descRes = Res.string.downloads_force_resync_data_poster_desc,
        )
    ResyncCategory.BACKDROP ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_backdrop,
            descRes = Res.string.downloads_force_resync_data_backdrop_desc,
        )
    ResyncCategory.SUBTITLES ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_subtitles,
            descRes = Res.string.downloads_force_resync_data_subtitles_desc,
        )
    ResyncCategory.TRICKPLAY ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_trickplay,
            descRes = Res.string.downloads_force_resync_data_trickplay_desc,
        )
    ResyncCategory.SEGMENTS ->
        ResyncCategoryStrings(
            labelRes = Res.string.downloads_force_resync_data_segments,
            descRes = Res.string.downloads_force_resync_data_segments_desc,
        )
}

@Composable
private fun ForceResyncDataRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
