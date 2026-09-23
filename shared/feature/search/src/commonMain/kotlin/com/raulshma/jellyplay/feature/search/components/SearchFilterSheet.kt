package com.raulshma.jellyplay.feature.search.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.components.MediaFilterSheet
import com.raulshma.jellyplay.core.ui.components.MediaFilterSheetTexts
import com.raulshma.jellyplay.core.ui.components.SearchFilterSheetSections
import com.raulshma.jellyplay.feature.search.generated.resources.Res
import com.raulshma.jellyplay.feature.search.generated.resources.search_apply_filters
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_all
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_any
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_genres
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_media_type
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_minimum_rating
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_rating_plus
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_tags
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_year_range
import com.raulshma.jellyplay.feature.search.generated.resources.search_filters_title

/**
 * The search screen's filter sheet — a thin host over the shared
 * [MediaFilterSheet] (core/ui): it resolves this screen's `search_*` strings
 * and passes the search section subset (no sort / played-status /
 * resumable+downloaded — search manages those through its dedicated
 * per-dimension sheets; the shared apply fold preserves them from
 * [currentFilters] so applying here never resets them). Section shape pins to
 * what this sheet always looked like: 20 genre chips, flat (non-collapsible)
 * years/tags/rating, no from/to year slider, no body scroll, no TV sheet
 * chrome title. Structure, draft state and the apply/reset folds live once in
 * core/ui — see [MediaFilterSheetSectionsTest] for the pinned algebra.
 */
@Composable
fun SearchFilterSheet(
    currentFilters: LibraryFilters,
    genres: List<Genre>,
    availableTags: List<String> = emptyList(),
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    MediaFilterSheet(
        currentFilters = currentFilters,
        sections = SearchFilterSheetSections,
        texts = MediaFilterSheetTexts(
            title = stringResource(Res.string.search_filters_title),
            // The search sheet never passed a TvSafeSheet chrome title.
            sheetTitle = null,
            reset = stringResource(Res.string.search_clear_all),
            apply = stringResource(Res.string.search_apply_filters),
            anyLabel = stringResource(Res.string.search_filter_any),
            mediaType = stringResource(Res.string.search_filter_media_type),
            genres = stringResource(Res.string.search_filter_genres),
            years = stringResource(Res.string.search_filter_year_range),
            tags = stringResource(Res.string.search_filter_tags),
            minRating = stringResource(Res.string.search_filter_minimum_rating),
            ratingLabel = { rating ->
                stringResource(Res.string.search_filter_rating_plus, formatFixed(rating.toDouble(), 1))
            },
        ),
        genres = genres,
        availableTags = availableTags,
        contentScrollable = false,
        onApply = onApply,
        onDismiss = onDismiss,
    )
}
