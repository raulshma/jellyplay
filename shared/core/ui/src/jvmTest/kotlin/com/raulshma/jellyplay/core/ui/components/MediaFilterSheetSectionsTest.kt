package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the two shipped [FilterSection] sets and the [MediaFilterDraft]
 * algebra behind [MediaFilterSheet] — the sheet the library and search
 * screens used to duplicate by hand. The pins:
 *  - the enum's declaration order IS the layout order, and
 *    [LibraryFilterSheetSections] renders every section in it (the library
 *    sheet's historical order);
 *  - [SearchFilterSheetSections] is a strict subset of the library set that,
 *    filtered through the canonical order, reproduces the search sheet's
 *    historical order — so per-screen section drift is structurally
 *    impossible;
 *  - the Apply fold takes the draft for every rendered section and the
 *    current value for every section the caller doesn't render (search's
 *    sort/status and the tri-states are preserved, never silently reset),
 *    and the downloaded pin drops tags only where the toggles render;
 *  - Reset clears everything except the downloaded pin.
 */
class MediaFilterSheetSectionsTest {

    @Test
    fun `library sections render every section in canonical order`() {
        assertEquals(
            FilterSection.entries.toList(),
            FilterSection.entries.filter { it in LibraryFilterSheetSections },
        )
    }

    @Test
    fun `search sections are a library subset that keeps canonical order`() {
        assertTrue(SearchFilterSheetSections.all { it in LibraryFilterSheetSections })
        assertEquals(
            listOf(
                FilterSection.MEDIA_TYPE,
                FilterSection.GENRES,
                FilterSection.YEARS,
                FilterSection.TAGS,
                FilterSection.MIN_RATING,
            ),
            FilterSection.entries.filter { it in SearchFilterSheetSections },
        )
    }

    @Test
    fun `apply with library sections takes every draft value`() {
        val draft = MediaFilterDraft(
            mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES),
            genres = listOf("Action"),
            years = setOf(2020, 2021),
            sortBy = SortOption.RATING,
            playedStatus = PlayedStatus.PLAYED,
            tags = setOf("night"),
            minRating = 3.5f,
            isResumable = true,
            isDownloaded = false,
        )

        assertEquals(
            LibraryFilters(
                mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES),
                genres = listOf("Action"),
                years = listOf(2020, 2021),
                sortBy = SortOption.RATING,
                playedStatus = PlayedStatus.PLAYED,
                tags = listOf("night"),
                minRating = 3.5f,
                isResumable = true,
                isDownloaded = null,
            ),
            draft.appliedTo(LibraryFilters(), LibraryFilterSheetSections),
        )
    }

    @Test
    fun `apply with search sections preserves the dimensions search does not render`() {
        // What the search sheet historically emitted: draft multi-dimensions +
        // the current sort/played-status/tri-states passed through untouched.
        val current = LibraryFilters(
            sortBy = SortOption.DATE_ADDED,
            playedStatus = PlayedStatus.UNPLAYED,
            isResumable = true,
            isDownloaded = null,
        )
        val draft = MediaFilterDraft(current)
            .withGenreToggled("Action")
            .withYears(setOf(2020))

        assertEquals(
            LibraryFilters(
                genres = listOf("Action"),
                years = listOf(2020),
                sortBy = SortOption.DATE_ADDED,
                playedStatus = PlayedStatus.UNPLAYED,
                isResumable = true,
            ),
            draft.appliedTo(current, SearchFilterSheetSections),
        )
    }

    @Test
    fun `downloaded pin drops tags only where the toggles render`() {
        val draft = MediaFilterDraft(tags = setOf("night"), isDownloaded = true)

        assertEquals(
            emptyList(),
            draft.appliedTo(LibraryFilters(), LibraryFilterSheetSections).tags,
        )
        // Search never renders the toggles, so its apply keeps the tags and
        // passes the current tri-state through.
        assertEquals(
            listOf("night"),
            draft.appliedTo(LibraryFilters(tags = listOf("night")), SearchFilterSheetSections).tags,
        )
        assertNull(
            draft.appliedTo(LibraryFilters(), SearchFilterSheetSections).isDownloaded,
        )
    }

    @Test
    fun `tri-state toggles emit null when off`() {
        val draft = MediaFilterDraft(isResumable = true)
            .withResumableToggled()

        val applied = draft.appliedTo(LibraryFilters(), LibraryFilterSheetSections)
        assertNull(applied.isResumable)
        assertNull(applied.isDownloaded)
    }

    @Test
    fun `reset clears everything except the downloaded pin`() {
        val draft = MediaFilterDraft(
            mediaTypes = listOf(MediaType.MOVIE),
            genres = listOf("Action"),
            years = setOf(2020),
            sortBy = SortOption.RATING,
            playedStatus = PlayedStatus.PLAYED,
            tags = setOf("night"),
            minRating = 3.5f,
            isResumable = true,
            isDownloaded = true,
        )

        assertEquals(
            MediaFilterDraft(isDownloaded = true),
            draft.clearedKeepingDownloaded(),
        )
    }

    @Test
    fun `media type toggle preserves toggle order`() {
        val draft = MediaFilterDraft()
            .withMediaTypeToggled(MediaType.SERIES)
            .withMediaTypeToggled(MediaType.MOVIE)

        assertEquals(listOf(MediaType.SERIES, MediaType.MOVIE), draft.mediaTypes)

        assertEquals(
            listOf(MediaType.SERIES),
            draft.withMediaTypeToggled(MediaType.MOVIE).mediaTypes,
        )
    }
}
