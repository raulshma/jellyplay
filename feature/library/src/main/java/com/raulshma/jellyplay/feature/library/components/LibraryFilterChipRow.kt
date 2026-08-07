package com.raulshma.jellyplay.feature.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.yearPresetSelection
import com.raulshma.jellyplay.core.ui.components.yearRangePresets
import com.raulshma.jellyplay.core.ui.components.toggleYearPreset
import com.raulshma.jellyplay.core.ui.components.YearPresetSelection
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayNamePlural
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.library.LibraryFilters
import com.raulshma.jellyplay.feature.library.R

/**
 * Which per-filter sheet is open, if any. Hoisted in [LibraryScreen] so only one
 * sheet renders at a time. `ALL` maps to the legacy full [LibraryFilterSheet].
 */
enum class FilterSheetKind { SORT, TYPE, STATUS, GENRES, YEARS, TAGS, ALL }

/**
 * The pinned filter chip row. A horizontally-scrolling row of
 * [FilterOptionChip]s — Sort (shows the active sort), Type / Genres / Years /
 * Tags (show a count when non-empty), Status (shows the active status unless
 * ALL) — plus an "All Filters" overflow chip that opens the legacy full sheet.
 *
 * Tapping a chip opens its dedicated selection sheet (immediate-apply, no Apply
 * button) via [onOpenSheet]. Chips whose option list is empty are hidden so the
 * row never shows inert affordances (e.g. genres before they load).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterChipRow(
    filters: LibraryFilters,
    genres: List<Genre>,
    availableTags: List<String>,
    onOpenSheet: (FilterSheetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "sort") {
            FilterOptionChip(
                label = filters.sortBy.displayName,
                onClick = { onOpenSheet(FilterSheetKind.SORT) },
                highlight = filters.sortBy != SortOption.YEAR_DESC,
            )
        }
        item(key = "type") {
            FilterOptionChip(
                label = stringResource(R.string.library_type),
                onClick = { onOpenSheet(FilterSheetKind.TYPE) },
                selectedCount = filters.mediaTypes.size,
            )
        }
        item(key = "status") {
            FilterOptionChip(
                label = if (filters.playedStatus == PlayedStatus.ALL) {
                    stringResource(R.string.library_status)
                } else {
                    filters.playedStatus.displayName
                },
                onClick = { onOpenSheet(FilterSheetKind.STATUS) },
                highlight = filters.playedStatus != PlayedStatus.ALL,
            )
        }
        if (genres.isNotEmpty()) {
            item(key = "genres") {
                FilterOptionChip(
                    label = stringResource(R.string.library_genres),
                    onClick = { onOpenSheet(FilterSheetKind.GENRES) },
                    selectedCount = filters.genres.size,
                )
            }
        }
        item(key = "years") {
            FilterOptionChip(
                label = stringResource(R.string.library_year_range),
                onClick = { onOpenSheet(FilterSheetKind.YEARS) },
                selectedCount = filters.years.size,
            )
        }
        if (availableTags.isNotEmpty()) {
            item(key = "tags") {
                FilterOptionChip(
                    label = stringResource(R.string.library_tags),
                    onClick = { onOpenSheet(FilterSheetKind.TAGS) },
                    selectedCount = filters.tags.size,
                )
            }
        }
        item(key = "all") {
            FilterOptionChip(
                label = stringResource(R.string.library_all_filters),
                onClick = { onOpenSheet(FilterSheetKind.ALL) },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Per-filter sheets (immediate-apply). Each renders inside [FilterSelectionSheet].
// ──────────────────────────────────────────────────────────────────────────

/**
 * Sort sheet — single-select. Tapping an option applies it immediately and
 * dismisses. Includes an "Any" reset that maps to the default SortName sort.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortFilterSheet(
    current: SortOption,
    onApply: (SortOption) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSelectionSheet(title = stringResource(R.string.library_sort_by), onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortOption.entries.forEach { option ->
                GlassFilterChip(
                    label = option.displayName,
                    selected = option == current,
                    onClick = {
                        onApply(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Multi-select sheet (immediate-apply toggle chips) shared by the type, genres
 * and tags sheets — the same "if (x in list) list - x else list + x" toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiSelectFilterSheet(
    title: String,
    options: List<T>,
    selected: List<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSelectionSheet(title = title, onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                GlassFilterChip(
                    label = label(option),
                    selected = option in selected,
                    onClick = { onToggle(option) },
                )
            }
        }
    }
}

/** Media-type sheet — multi-select (toggle). */
@Composable
fun MediaTypeFilterSheet(
    current: List<MediaType>,
    onToggle: (MediaType) -> Unit,
    onDismiss: () -> Unit,
) {
    MultiSelectFilterSheet(
        title = stringResource(R.string.library_media_type),
        options = MediaType.entries.filter { it != MediaType.UNKNOWN },
        selected = current,
        label = { it.mediaTypeDisplayNamePlural() },
        onToggle = onToggle,
        onDismiss = onDismiss,
    )
}

/** Status (played / unplayed) sheet — single-select. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusFilterSheet(
    current: PlayedStatus,
    onApply: (PlayedStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSelectionSheet(title = stringResource(R.string.library_status), onDismiss = onDismiss) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayedStatus.entries.forEach { status ->
                GlassFilterChip(
                    label = status.displayName,
                    selected = status == current,
                    onClick = {
                        onApply(status)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** Genres sheet — multi-select (toggle). */
@Composable
fun GenreFilterSheet(
    current: List<String>,
    genres: List<Genre>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MultiSelectFilterSheet(
        title = stringResource(R.string.library_genres),
        options = genres.map { it.name },
        selected = current,
        label = { it },
        onToggle = onToggle,
        onDismiss = onDismiss,
    )
}

/** Tags sheet — multi-select (toggle). */
@Composable
fun TagFilterSheet(
    current: List<String>,
    tags: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MultiSelectFilterSheet(
        title = stringResource(R.string.library_tags),
        options = tags,
        selected = current,
        label = { it },
        onToggle = onToggle,
        onDismiss = onDismiss,
    )
}

/** Year-range sheet — decade presets (multi-select toggle) plus a custom
 * From/To slider range, reusing core/ui helpers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearRangeFilterSheet(
    current: Set<Int>,
    onApply: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = remember { yearRangePresets() }
    FilterSelectionSheet(title = stringResource(R.string.library_year_range), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassFilterChip(
                    label = stringResource(R.string.library_any),
                    selected = current.isEmpty(),
                    onClick = { onApply(emptySet()) },
                )
                presets.forEach { preset ->
                    val selection = yearPresetSelection(preset, current)
                    GlassFilterChip(
                        label = preset.label,
                        selected = selection == YearPresetSelection.Full,
                        onClick = { onApply(toggleYearPreset(preset, current)) },
                    )
                }
            }

            CustomYearRangeSelector(
                current = current,
                onRangeChange = { range -> onApply(range.toSet()) },
            )
        }
    }
}
