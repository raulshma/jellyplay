package com.raulshma.jellyplay.feature.library.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.ui.components.FilterSection
import com.raulshma.jellyplay.core.ui.components.LibraryFilterSheetSections
import com.raulshma.jellyplay.core.ui.components.MediaFilterSheet
import com.raulshma.jellyplay.core.ui.components.MediaFilterSheetTexts
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_apply_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_collapse
import com.raulshma.jellyplay.feature.library.generated.resources.library_expand
import com.raulshma.jellyplay.feature.library.generated.resources.library_filter_downloaded
import com.raulshma.jellyplay.feature.library.generated.resources.library_filter_resumable
import com.raulshma.jellyplay.feature.library.generated.resources.library_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_genres
import com.raulshma.jellyplay.feature.library.generated.resources.library_media_type
import com.raulshma.jellyplay.feature.library.generated.resources.library_minimum_rating
import com.raulshma.jellyplay.feature.library.generated.resources.library_reset
import com.raulshma.jellyplay.feature.library.generated.resources.library_sort_by
import com.raulshma.jellyplay.feature.library.generated.resources.library_status
import com.raulshma.jellyplay.feature.library.generated.resources.library_tags
import com.raulshma.jellyplay.feature.library.generated.resources.library_year_range

/**
 * The library screen's filter sheet — a thin host over the shared
 * [MediaFilterSheet] (core/ui): it resolves this screen's `library_*` strings,
 * passes the full section set, and pins the section-shape knobs to what this
 * sheet always looked like (24 genre chips, collapsible years/tags/rating,
 * a scrollable body, the [CustomYearRangeSelector] from/to sliders inside the
 * year section). Structure, draft state and the apply/reset folds live once
 * in core/ui — see [MediaFilterSheetSectionsTest] for the pinned algebra.
 */
@Composable
fun LibraryFilterSheet(
    currentFilters: LibraryFilters,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    MediaFilterSheet(
        currentFilters = currentFilters,
        sections = LibraryFilterSheetSections,
        texts = MediaFilterSheetTexts(
            title = stringResource(Res.string.library_filters),
            sheetTitle = stringResource(Res.string.library_filters),
            reset = stringResource(Res.string.library_reset),
            apply = stringResource(Res.string.library_apply_filters),
            // Historical literals, not resources — the library sheet always
            // rendered the English "Any"/"3.0+" labels.
            anyLabel = "Any",
            sortBy = stringResource(Res.string.library_sort_by),
            mediaType = stringResource(Res.string.library_media_type),
            status = stringResource(Res.string.library_status),
            resumable = stringResource(Res.string.library_filter_resumable),
            downloaded = stringResource(Res.string.library_filter_downloaded),
            genres = stringResource(Res.string.library_genres),
            years = stringResource(Res.string.library_year_range),
            tags = stringResource(Res.string.library_tags),
            minRating = stringResource(Res.string.library_minimum_rating),
            ratingLabel = { rating -> "${rating}+" },
            collapseContentDescription = stringResource(Res.string.library_collapse),
            expandContentDescription = stringResource(Res.string.library_expand),
        ),
        genres = genres,
        availableTags = availableTags,
        genreLimit = 24,
        collapsibleSections = setOf(FilterSection.YEARS, FilterSection.TAGS, FilterSection.MIN_RATING),
        contentScrollable = true,
        customYearRange = { years, onRangeChange ->
            CustomYearRangeSelector(
                current = years,
                onRangeChange = onRangeChange,
            )
        },
        onApply = onApply,
        onDismiss = onDismiss,
    )
}
