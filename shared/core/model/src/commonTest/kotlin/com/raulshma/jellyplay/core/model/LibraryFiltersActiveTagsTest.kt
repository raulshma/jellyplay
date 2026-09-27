package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [LibraryFilters.activeTags] — the read-side active-filter fold behind
 * the shared active-filter bar (library + search screens). Companion to
 * [LibraryFiltersAlgebraTest]: that pins the WRITE algebra, this pins the
 * enumeration + per-tag clear semantics (the exact `copy(... minus x)` the
 * screens' hand-rolled dismiss lambdas used to perform).
 */
class LibraryFiltersActiveTagsTest {

    // ── empty / consistency with hasActiveFilters ───────────────────────────

    @Test
    fun defaultFilters_noTags() {
        assertTrue(LibraryFilters().activeTags().isEmpty())
    }

    @Test
    fun noSortChange_tagsEmpty_iffHasActiveFiltersFalse() {
        // For every dimension EXCEPT sort, tags empty <=> nothing active: the
        // fold and the predicate read the same state.
        val quiet = LibraryFilters(playedStatus = PlayedStatus.ALL, isResumable = false, isDownloaded = false)
        assertFalse(quiet.hasActiveFilters())
        assertTrue(quiet.activeTags().isEmpty())
    }

    @Test
    fun sortOnly_activeWithoutTags() {
        // The declared exception: a non-default sort counts as active (the sort
        // chips highlight and Back-press clears it) but is not a dismissible
        // tag — neither screen surfaces it in the bar.
        val filters = LibraryFilters().withSortBy(SortOption.SORT_NAME)

        assertTrue(filters.hasActiveFilters())
        assertTrue(filters.activeTags().isEmpty())
    }

    // ── one tag per active dimension, with the exact clear write ────────────

    @Test
    fun eachDimensionSet_emitsExactlyItsTag_andClearCopiesWithoutIt() {
        val cases: List<LibraryFilters> = listOf(
            LibraryFilters(mediaTypes = listOf(MediaType.MOVIE)),
            LibraryFilters(genres = listOf("Drama")),
            LibraryFilters(years = listOf(2020)),
            LibraryFilters(tags = listOf("neo-noir")),
            LibraryFilters(minRating = 6f),
            LibraryFilters(playedStatus = PlayedStatus.PLAYED),
            LibraryFilters(isResumable = true),
            LibraryFilters(isDownloaded = true),
        )

        cases.forEach { filters ->
            assertTrue(filters.hasActiveFilters(), "expected active: $filters")
            val tags = filters.activeTags()
            assertEquals(1, tags.size, "expected one tag for $filters")

            val cleared = tags.single().clear()
            assertEquals(LibraryFilters(), cleared, "clear must reset exactly the active dimension of $filters")
            assertFalse(cleared.hasActiveFilters())
        }
    }

    @Test
    fun dimensionValues_carryTheDismissedIdentity() {
        assertEquals(
            MediaType.SERIES.name,
            LibraryFilters(mediaTypes = listOf(MediaType.SERIES)).activeTags().single().value,
        )
        assertEquals("Drama", LibraryFilters(genres = listOf("Drama")).activeTags().single().value)
        assertEquals("2020", LibraryFilters(years = listOf(2020)).activeTags().single().value)
        assertEquals("neo-noir", LibraryFilters(tags = listOf("neo-noir")).activeTags().single().value)
        assertEquals("6.0", LibraryFilters(minRating = 6f).activeTags().single().value)
        assertEquals(PlayedStatus.UNPLAYED.name, LibraryFilters(playedStatus = PlayedStatus.UNPLAYED).activeTags().single().value)
    }

    @Test
    fun inactiveDimensions_emitNothing() {
        val quiet = LibraryFilters(
            playedStatus = PlayedStatus.ALL,
            isResumable = false, // stored "off" — tri-state, not active
            isDownloaded = false,
        )

        assertTrue(quiet.activeTags().isEmpty())
    }

    // ── multi-value dimensions: one tag per value, in list order ────────────

    @Test
    fun multiValueDimensions_oneTagPerValue_inListOrder() {
        val filters = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES),
            genres = listOf("Drama", "Crime"),
        )

        val tags = filters.activeTags()

        assertEquals(
            listOf(MediaType.MOVIE.name, MediaType.SERIES.name, "Drama", "Crime"),
            tags.map { it.value },
        )

        // Clearing the first of two media types leaves the rest intact.
        val clearedFirst = tags.first().clear()
        assertEquals(listOf(MediaType.SERIES), clearedFirst.mediaTypes)
        assertEquals(listOf("Drama", "Crime"), clearedFirst.genres)
    }

    // ── caller-controlled subset + ordering ─────────────────────────────────

    @Test
    fun dimensionsParameter_selectsAndOrders() {
        val filters = LibraryFilters(
            genres = listOf("Drama"),
            mediaTypes = listOf(MediaType.MOVIE),
            playedStatus = PlayedStatus.PLAYED,
        )

        // The library screen's order: media types → status → downloaded → genres.
        val libraryOrder = filters.activeTags(
            listOf(
                LibraryFilterDimension.MEDIA_TYPES,
                LibraryFilterDimension.PLAYED_STATUS,
                LibraryFilterDimension.IS_DOWNLOADED,
                LibraryFilterDimension.GENRES,
            ),
        )

        assertEquals(
            listOf(
                LibraryFilterDimension.MEDIA_TYPES,
                LibraryFilterDimension.PLAYED_STATUS,
                LibraryFilterDimension.GENRES,
            ),
            libraryOrder.map { it.dimension },
        )

        // Search's order: media types → genres → years → tags → min rating → status.
        val searchOrder = filters.activeTags(
            listOf(
                LibraryFilterDimension.MEDIA_TYPES,
                LibraryFilterDimension.GENRES,
                LibraryFilterDimension.YEARS,
                LibraryFilterDimension.TAGS,
                LibraryFilterDimension.MIN_RATING,
                LibraryFilterDimension.PLAYED_STATUS,
            ),
        )

        assertEquals(
            listOf(
                LibraryFilterDimension.MEDIA_TYPES,
                LibraryFilterDimension.GENRES,
                LibraryFilterDimension.PLAYED_STATUS,
            ),
            searchOrder.map { it.dimension },
        )
    }

    @Test
    fun requestedDimension_inactive_emitsNothing() {
        val tags = LibraryFilters(genres = listOf("Drama")).activeTags(
            listOf(LibraryFilterDimension.MEDIA_TYPES, LibraryFilterDimension.IS_DOWNLOADED),
        )

        assertTrue(tags.isEmpty())
    }

    // ── clear-all composition ───────────────────────────────────────────────

    @Test
    fun clearingEveryTag_ofAFullyPopulatedFilterSet_yieldsDefaults() {
        val populated = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE, MediaType.ALBUM),
            genres = listOf("Drama", "Crime"),
            years = listOf(1999, 2001),
            tags = listOf("neo-noir"),
            minRating = 7.5f,
            playedStatus = PlayedStatus.UNPLAYED,
            isResumable = true,
            isDownloaded = true,
        )

        assertTrue(populated.hasActiveFilters())

        // A tag's clear() is pinned to the filters it was derived FROM (it
        // returns "those filters minus one value"), so — exactly like the
        // screens, which rebuild the bar from the current state after every
        // dismiss — each step must re-derive the tags from the accumulated
        // filters. Each step removes exactly one active value.
        var current = populated
        var guard = 0
        while (current.activeTags().isNotEmpty()) {
            current = current.activeTags().first().clear()
            guard++
            assertTrue(guard < 32, "dismiss steps did not converge: $current")
        }

        assertEquals(LibraryFilters(), current)
        assertFalse(current.hasActiveFilters())
    }
}
