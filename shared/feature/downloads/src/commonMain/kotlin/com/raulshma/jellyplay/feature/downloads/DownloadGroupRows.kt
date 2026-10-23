package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ChevronsDown
import com.composables.icons.tabler.outline.ChevronsUp
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerStop
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_cancel
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_action_play
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_collapse_all_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_episode_count
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_expand_all_cd
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_group_progress
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_pause_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_cd

/**
 * UI pieces of the grouped downloads list (gh-175): the All/Movies/Series +
 * sort control bar and the one [DownloadGroupHeader] card every series/season
 * group renders (visually matching the list's ElevatedCard rows, chevron +
 * expand/collapse mirroring ManageSeriesScreen's season groups).
 */

/**
 * The All/Movies/Series segmented bar + sort menu, with the expand/collapse-all
 * control for the group tree parked beside the sort button. The expand control
 * is only composed while the current view actually contains series groups —
 * a flat view (Movies filter, no identified episodes) has nothing to expand.
 *
 * The second segmented row is the status filter (All / Active / Completed /
 * Failed, gh-178) — the "show me what's downloading" view the transfer
 * notification alone never offered in-app. It sits on its own row so neither
 * segmented group exceeds four segments on a narrow phone.
 */
@Composable
internal fun DownloadsFilterBar(
    selected: DownloadListFilter,
    onSelectFilter: (DownloadListFilter) -> Unit,
    statusFilter: DownloadStatusFilter,
    onSelectStatusFilter: (DownloadStatusFilter) -> Unit,
    sort: DownloadListSort,
    onSelectSort: (DownloadListSort) -> Unit,
    hasGroups: Boolean,
    anyExpanded: Boolean,
    onToggleExpandAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val options = DownloadListFilter.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelectFilter(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(stringResource(option.labelRes))
                    }
                }
            }
            if (hasGroups) {
                val expandFocus = rememberTvFocusState(focusedScale = 1.1f)
                IconButton(
                    onClick = onToggleExpandAll,
                    modifier = Modifier
                        .then(expandFocus.focusModifier)
                        .tvFocusIndicator(expandFocus, CircleShape),
                ) {
                    Icon(
                        if (anyExpanded) Tabler.Outline.ChevronsUp else Tabler.Outline.ChevronsDown,
                        contentDescription = stringResource(
                            if (anyExpanded) {
                                Res.string.downloads_collapse_all_cd
                            } else {
                                Res.string.downloads_expand_all_cd
                            },
                        ),
                    )
                }
            }
            Box {
                var sortMenuOpen by remember { mutableStateOf(false) }
                val sortFocus = rememberTvFocusState(focusedScale = 1.1f)
                IconButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier
                        .then(sortFocus.focusModifier)
                        .tvFocusIndicator(sortFocus, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.ArrowDown,
                        contentDescription = stringResource(Res.string.downloads_sort_cd),
                    )
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    DownloadListSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(option.labelRes),
                                    fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelectSort(option)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        val statusOptions = DownloadStatusFilter.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
            statusOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == statusFilter,
                    onClick = { onSelectStatusFilter(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, statusOptions.size),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }
    }
}

/**
 * One expandable group header — series or season; the caller resolves the
 * title (seasons fall back to "Season N"). [title] null renders the count
 * line alone. Visually matches [DownloadItemRow]'s ElevatedCard rows; the
 * chevron + expand/collapse interaction mirrors ManageSeriesScreen's season
 * groups.
 *
 * Beyond the count line the header renders the group's context at a glance:
 * a summary line (partial download progress + total size, gh-175 follow-up),
 * the update-available dot when episodes inside are flagged for a resync,
 * — outside selection mode — a Play button targeting the group's next
 * unwatched episode, and (also outside selection mode) group-level transfer
 * controls (gh-178): pause / cancel for the group's in-flight episodes, each
 * shown only when at least one episode admits it, mirroring the per-row
 * button gates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DownloadGroupHeader(
    title: String?,
    episodeCount: Int,
    expanded: Boolean,
    selectionMode: Boolean,
    selection: ToggleableState,
    onToggleExpanded: () -> Unit,
    onToggleGroupSelection: () -> Unit,
    modifier: Modifier = Modifier,
    /** How many of [episodeCount] are already downloaded — drives the summary line. */
    completedCount: Int = 0,
    /** Total bytes of the group's episodes — rendered alongside the progress summary. */
    totalBytes: Long = 0L,
    formatBytes: (Long) -> String = Long::toString,
    /** Episodes inside flagged for a metadata resync — renders the update dot. */
    updateCount: Int = 0,
    /** Play the group's next unwatched episode; null hides the button. */
    onPlay: (() -> Unit)? = null,
    /** Pause the group's in-flight episodes; null hides the button. */
    onPauseGroup: (() -> Unit)? = null,
    /** Cancel the group's in-flight episodes; null hides the button. */
    onCancelGroup: (() -> Unit)? = null,
) {
    val countText = pluralStringResource(Res.plurals.downloads_episode_count, episodeCount, episodeCount)
    // Summary line: partial progress ("3 of 10 downloaded") whenever anything
    // in the group is still missing — 0-of-M groups of only queued/in-flight
    // rows included, so they don't masquerade as fully downloaded — plus the
    // group's on-disk size. A fully-downloaded group shows the size alone;
    // an empty/zero group shows nothing extra.
    val summaryText = buildString {
        if (completedCount < episodeCount) {
            append(
                pluralStringResource(
                    Res.plurals.downloads_group_progress,
                    completedCount,
                    completedCount,
                    episodeCount,
                ),
            )
        }
        if (totalBytes > 0) {
            if (isNotEmpty()) append(" · ")
            append(formatBytes(totalBytes))
        }
    }
    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .combinedClickable(
                onClick = {
                    // In selection mode a tap toggles the whole group; otherwise
                    // it expands/collapses — same split as DownloadItemRow.
                    if (selectionMode) onToggleGroupSelection() else onToggleExpanded()
                },
                onLongClick = onToggleGroupSelection,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selectionMode && selection == ToggleableState.On) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                TriStateCheckbox(
                    state = selection,
                    onClick = { onToggleGroupSelection() },
                )
            }
            Icon(
                if (expanded) Tabler.Outline.ChevronDown else Tabler.Outline.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summaryText.isNotEmpty()) {
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Update-available dot: same tertiary color and meaning as the
            // item rows' thumbnail dot — some episodes inside have a resync
            // waiting (count rendered for TV screen readers via the row's
            // own semantics; the dot itself stays decorative).
            if (updateCount > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
            // Group transfer controls (gh-178): pause / cancel for the group's
            // in-flight episodes, gated by the caller's per-group admission
            // (null = nothing in the group admits the action). Outside
            // selection mode, alongside the group Play button. Cancel dispatches
            // directly — the per-row cancel carries no confirmation either;
            // only DELETE and the appbar's Cancel All confirm.
            if (!selectionMode && onPauseGroup != null) {
                val pauseFocus = rememberTvFocusState(focusedScale = 1.1f)
                IconButton(
                    onClick = onPauseGroup,
                    modifier = Modifier
                        .then(pauseFocus.focusModifier)
                        .tvFocusIndicator(pauseFocus, ShapeCache.smooth10),
                ) {
                    Icon(
                        Tabler.Outline.PlayerPause,
                        contentDescription = stringResource(Res.string.downloads_pause_all),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!selectionMode && onCancelGroup != null) {
                val cancelFocus = rememberTvFocusState(focusedScale = 1.1f)
                IconButton(
                    onClick = onCancelGroup,
                    modifier = Modifier
                        .then(cancelFocus.focusModifier)
                        .tvFocusIndicator(cancelFocus, ShapeCache.smooth10),
                ) {
                    Icon(
                        Tabler.Outline.PlayerStop,
                        contentDescription = stringResource(Res.string.downloads_action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Group Play: outside selection mode, targets the next unwatched
            // episode in the group's playback order.
            if (onPlay != null && !selectionMode) {
                val playFocus = rememberTvFocusState(focusedScale = 1.1f)
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .then(playFocus.focusModifier)
                        .tvFocusIndicator(playFocus, ShapeCache.smooth10),
                ) {
                    Icon(
                        Tabler.Outline.PlayerPlay,
                        contentDescription = stringResource(Res.string.downloads_action_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
