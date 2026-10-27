package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsSort
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
 * status filter + sort control row and the one [DownloadGroupHeader] card every
 * series/season group renders (visually matching the list's ElevatedCard rows,
 * chevron + expand/collapse mirroring ManageSeriesScreen's season groups).
 */

/**
 * The whole filter chrome on ONE row: the All/Movies/Series segmented bar,
 * the status filter collapsed into a dropdown chip (current value stays
 * visible — the funnel icon disambiguates it from the segmented "All" — and
 * the four options sit one tap away), the expand/collapse-all control for the
 * group tree, and the sort menu. The expand control is only composed while
 * the current view actually contains series groups — a flat view (Movies
 * filter, no identified episodes) has nothing to expand. The segmented row
 * carries the weight, so the type labels shrink before the row overflows;
 * every label is single-line so a narrow phone can never wrap the row taller
 * (the old two-row layout wrapped "Completed" on 360dp screens).
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
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val options = DownloadListFilter.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelectFilter(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(
                        stringResource(option.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Status filter: a chip anchored over a menu — reading it answers
        // "what am I looking at", opening it lists every view (gh-178).
        // Iconless on purpose: the segmented row needs every pixel it can get
        // on a narrow phone, and "All" next to the segmented "All" stays
        // readable through the chip's filled selected style.
        Box(modifier = Modifier.padding(start = 4.dp)) {
            var statusMenuOpen by remember { mutableStateOf(false) }
            val statusFocus = rememberTvFocusState(focusedScale = 1.1f)
            FilterChip(
                selected = true,
                onClick = { statusMenuOpen = true },
                label = {
                    Text(
                        stringResource(statusFilter.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier
                    .then(statusFocus.focusModifier)
                    .tvFocusIndicator(statusFocus, CircleShape),
            )
            DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                DownloadStatusFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(option.labelRes),
                                fontWeight = if (option == statusFilter) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelectStatusFilter(option)
                            statusMenuOpen = false
                        },
                    )
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
                    // ArrowsSort, not ArrowDown — the download-y arrow read as
                    // "add/download" next to the nav bar's download icon.
                    Tabler.Outline.ArrowsSort,
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
}

/**
 * One expandable group header — series or season; the caller resolves the
 * title (seasons fall back to "Season N"). [title] null renders the count
 * line alone. Visually matches [DownloadItemRow]'s ElevatedCard rows; the
 * chevron + expand/collapse interaction mirrors ManageSeriesScreen's season
 * groups. When [imageUrl] is present the header carries the series artwork
 * (or episode-still fallback) between the chevron and the text, matching the
 * flat rows' thumbnails; null keeps the compact text-only shape (season
 * sub-headers stay that way to keep the hierarchy readable).
 *
 * Beyond the count line the header renders the group's context at a glance:
 * ONE meta line — episode count plus (partial download progress + total size)
 * — where the count and summary used to take two, the update-available dot
 * when episodes inside are flagged for a resync (overlaid on the artwork when
 * there is any, standalone otherwise), — outside selection mode — a Play
 * button targeting the group's next unwatched episode, and (also outside
 * selection mode) group-level transfer controls (gh-178): pause / cancel for
 * the group's in-flight episodes, each shown only when at least one episode
 * admits it, mirroring the per-row button gates.
 *
 * While the group is COLLAPSED with episodes actively DOWNLOADING, the
 * disclosed rows' own bars are hidden with them, so the header carries the
 * aggregate instead: one whole-group transfer bar (Σ downloaded / Σ total,
 * [groupProgress]) with the percent at its trailing edge. Null [groupProgress]
 * (nothing in flight) and the expanded state (every episode shows its own
 * bar) both hide it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    /**
     * The group's live whole-group transfer fraction (Σ downloaded / Σ total
     * over ALL its episodes, completed ones counted as landed — see
     * [downloadGroupProgressFraction]) while at least one episode is
     * DOWNLOADING — renders the collapsed header's aggregate bar; null
     * (nothing in flight, or the group is expanded) hides it.
     */
    groupProgress: Float? = null,
    /** Episodes inside flagged for a metadata resync — renders the update dot. */
    updateCount: Int = 0,
    /** Series (or episode-still) artwork; null keeps the text-only header. */
    imageUrl: String? = null,
    imageBlurHash: String? = null,
    /** Play the group's next unwatched episode; null hides the button. */
    onPlay: (() -> Unit)? = null,
    /** Pause the group's in-flight episodes; null hides the button. */
    onPauseGroup: (() -> Unit)? = null,
    /** Cancel the group's in-flight episodes; null hides the button. */
    onCancelGroup: (() -> Unit)? = null,
) {
    val countText = pluralStringResource(Res.plurals.downloads_episode_count, episodeCount, episodeCount)
    // Summary segments: partial progress ("3 of 10 downloaded") whenever
    // anything in the group is still missing — 0-of-M groups of only
    // queued/in-flight rows included, so they don't masquerade as fully
    // downloaded — plus the group's on-disk size. A fully-downloaded group
    // shows the size alone; an empty/zero group shows nothing extra.
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
    // ONE meta line ("4 episodes · 2 of 4 downloaded · 3.1 GB") where the
    // count and summary used to take two lines — maxLines 1 keeps the header
    // to title + line no matter how long the segments grow.
    val metaText = listOf(countText, summaryText)
        .filter(String::isNotEmpty)
        .joinToString(" · ")
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
            if (imageUrl != null) {
                Box(
                    modifier = Modifier
                        // The DownloadItemRow thumb size — the header's artwork
                        // reads as the same row face, one rank up.
                        .size(56.dp)
                        .clip(ShapeCache.smooth8),
                ) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = null,
                        blurHash = imageBlurHash,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (updateCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (metaText.isNotEmpty()) {
                    Text(
                        metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Collapsed in-flight aggregate: the group's whole transfer as
                // one bar + percent. Only composed while collapsed AND some
                // episode is DOWNLOADING (see [groupProgress]) — expanded
                // groups show each episode's own bar instead, and the same
                // animateFloatAsState easing as the item rows keeps the bar
                // gliding between ticks.
                val aggregate = groupProgress
                if (!expanded && aggregate != null) {
                    Spacer(Modifier.height(4.dp))
                    val animatedAggregate by animateFloatAsState(
                        targetValue = aggregate,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "groupAggregateProgress",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        JellyPlayLinearProgressIndicator(
                            progress = { animatedAggregate },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(aggregate * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Update-available dot for the text-only headers (no artwork to
            // overlay): same tertiary color and meaning as the item rows'
            // thumbnail dot — some episodes inside have a resync waiting.
            if (imageUrl == null && updateCount > 0) {
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
