package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryBrowserState
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.feature.library.components.FilterSelectionSheet
import com.raulshma.jellyplay.feature.library.components.FilterSheetKind
import com.raulshma.jellyplay.feature.library.components.GenreFilterSheet
import com.raulshma.jellyplay.feature.library.components.LibraryFilterSheet
import com.raulshma.jellyplay.feature.library.components.LibraryResetConfirmDialog
import com.raulshma.jellyplay.feature.library.components.MediaTypeFilterSheet
import com.raulshma.jellyplay.feature.library.components.SortFilterSheet
import com.raulshma.jellyplay.feature.library.components.StatusFilterSheet
import com.raulshma.jellyplay.feature.library.components.TagFilterSheet
import com.raulshma.jellyplay.feature.library.components.YearRangeFilterSheet
import com.raulshma.jellyplay.feature.library.components.groupByLabel
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by
import com.raulshma.jellyplay.feature.library.generated.resources.library_poster_size

/**
 * The Library screen's sheet host — the reset confirm dialog, the legacy full
 * filter sheet, the per-filter immediate-apply selection sheets and the
 * poster-size / group-by sheets, all behind the one [LibrarySheet] state the
 * screen root owns (the back ladder folds over it via [libraryBackAction]).
 *
 * Composition-shape only: the bodies moved verbatim out of [LibraryScreen]
 * (the deferred section-hosts design), zero behaviour change. Every write
 * rides the view model's event channel; sheet dismissal writes
 * [openSheetState].
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySheetHost(
    viewModel: LibraryViewModel,
    browser: LibraryBrowserState,
    genres: List<Genre>,
    tags: List<String>,
    offlineAutoFilter: Boolean,
    isTv: Boolean,
    showFilters: Boolean,
    resetDialogVisible: Boolean,
    openSheetState: MutableState<LibrarySheet?>,
) {
    val filters = if (offlineAutoFilter) browser.filters.copy(isDownloaded = true) else browser.filters
    val posterSize = browser.posterSize
    val groupBy = browser.groupBy

    if (resetDialogVisible) {
        LibraryResetConfirmDialog(
            onConfirm = { dontShowAgain ->
                viewModel.onEvent(LibraryUiEvent.ConfirmResetAll(dontShowAgain))
            },
            onDismiss = { viewModel.onEvent(LibraryUiEvent.DismissResetDialog) },
        )
    }

    if (showFilters) {
        LibraryFilterSheet(
            // Same offline pinning as the chip row: the Downloaded
            // toggle renders on while the grid is auto-served from the local
            // store, so the sheet can't claim it's off.
            currentFilters = filters,
            genres = genres,
            availableTags = tags,
            onApply = { newFilters ->
                viewModel.onEvent(LibraryUiEvent.UpdateFilters(newFilters))
                viewModel.onEvent(LibraryUiEvent.ToggleFilters)
            },
            onDismiss = { viewModel.onEvent(LibraryUiEvent.ToggleFilters) },
        )
    }

    // Per-filter sheets (immediate-apply) + the poster-size / group-by sheets,
    // all behind the one [LibrarySheet] state.
    when (val sheet = openSheetState.value) {
        is LibrarySheet.Filter -> when (sheet.kind) {
            FilterSheetKind.SORT -> SortFilterSheet(
                current = browser.filters.sortBy,
                onApply = {
                    viewModel.onEvent(LibraryUiEvent.UpdateFilters(browser.filters.withSortBy(it)))
                },
                onDismiss = { openSheetState.value = null },
            )
            FilterSheetKind.TYPE -> MediaTypeFilterSheet(
                current = browser.filters.mediaTypes,
                onToggle = { type ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withMediaTypeToggled(type))
                    )
                },
                onDismiss = { openSheetState.value = null },
            )
            FilterSheetKind.STATUS -> StatusFilterSheet(
                current = browser.filters.playedStatus,
                onApply = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withPlayedStatus(it))
                    )
                },
                onDismiss = { openSheetState.value = null },
                isResumable = browser.filters.isResumable == true,
                onToggleResumable = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withResumableToggled())
                    )
                },
            )
            FilterSheetKind.GENRES -> GenreFilterSheet(
                current = browser.filters.genres,
                genres = genres,
                onToggle = { genre ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withGenreToggled(genre))
                    )
                },
                onDismiss = { openSheetState.value = null },
            )
            FilterSheetKind.TAGS -> TagFilterSheet(
                current = browser.filters.tags,
                tags = tags,
                onToggle = { tag ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withTagToggled(tag))
                    )
                },
                onDismiss = { openSheetState.value = null },
            )
            FilterSheetKind.YEARS -> YearRangeFilterSheet(
                current = browser.filters.years.toSet(),
                onApply = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(browser.filters.withYears(it))
                    )
                },
                onDismiss = { openSheetState.value = null },
            )
            FilterSheetKind.ALL -> {
                openSheetState.value = null
                viewModel.onEvent(LibraryUiEvent.ToggleFilters)
            }
        }
        LibrarySheet.PosterSize -> {
            FilterSelectionSheet(
                title = stringResource(Res.string.library_poster_size),
                onDismiss = { openSheetState.value = null },
            ) {
                Column {
                    // TvOrTouchSlider gives the slider D-pad stepping on TV (same control the
                    // year-range sheet uses); the raw M3 Slider wasn't TV-operable.
                    TvOrTouchSlider(
                        value = posterSize,
                        onValueChange = { viewModel.onEvent(LibraryUiEvent.SetPosterSize(it)) },
                        valueRange = 0.7f..1.4f,
                        isTv = isTv,
                        onValueChangeFinished = { viewModel.onEvent(LibraryUiEvent.PersistPosterSize) },
                    )
                }
            }
        }
        LibrarySheet.GroupBy -> {
            FilterSelectionSheet(
                title = stringResource(Res.string.library_group_by),
                onDismiss = { openSheetState.value = null },
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GroupBy.entries.forEach { option ->
                        GlassFilterChip(
                            label = groupByLabel(option),
                            selected = option == groupBy,
                            onClick = {
                                viewModel.onEvent(LibraryUiEvent.SetGroupBy(option))
                                openSheetState.value = null
                            },
                        )
                    }
                }
            }
        }
        null -> {}
    }
}
