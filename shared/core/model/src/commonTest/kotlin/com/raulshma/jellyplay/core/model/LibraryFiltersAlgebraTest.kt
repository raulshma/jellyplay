package com.raulshma.jellyplay.core.model
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [LibraryFilters]' write algebra and the canonical [LibraryFilters.hasActiveFilters]
 * fold — the behaviour the library and search screens' filter toggles/clears route
 * through (same pattern as [HomeSectionPrefs]). Previously these were hand-rolled
 * at each call site and the two screens' active-filter predicates had drifted:
 * library's copy omitted years/tags/minRating/sort, so its badge and BackHandler
 * guard under-reported the active set.
 */
class LibraryFiltersAlgebraTest {

    // ── withMediaTypeToggled ────────────────────────────────────────────────

    @Test
    fun withMediaTypeToggled_absentType_appends() {
        val updated = LibraryFilters().withMediaTypeToggled(MediaType.MOVIE)

        assertEquals(listOf(MediaType.MOVIE), updated.mediaTypes)
    }

    @Test
    fun withMediaTypeToggled_presentType_removes() {
        val filters = LibraryFilters(mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES))

        val updated = filters.withMediaTypeToggled(MediaType.MOVIE)

        assertEquals(listOf(MediaType.SERIES), updated.mediaTypes)
    }

    @Test
    fun withMediaTypeToggled_leavesOtherDimensionsUntouched() {
        val filters = LibraryFilters(genres = listOf("Drama"), years = listOf(2020), minRating = 6f)

        val updated = filters.withMediaTypeToggled(MediaType.SERIES)

        assertEquals(listOf("Drama"), updated.genres)
        assertEquals(listOf(2020), updated.years)
        assertEquals(6f, updated.minRating)
    }

    // ── withGenreToggled / withTagToggled ───────────────────────────────────

    @Test
    fun withGenreToggled_togglesMembership() {
        val added = LibraryFilters().withGenreToggled("Drama")
        assertEquals(listOf("Drama"), added.genres)

        val removed = added.withGenreToggled("Drama")
        assertTrue(removed.genres.isEmpty())
    }

    @Test
    fun withTagToggled_togglesMembership() {
        val added = LibraryFilters().withTagToggled("neo-noir")
        assertEquals(listOf("neo-noir"), added.tags)

        val removed = added.withTagToggled("neo-noir")
        assertTrue(removed.tags.isEmpty())
    }

    // ── withYears / withMinRating ───────────────────────────────────────────

    @Test
    fun withYears_replacesTheWholeSet() {
        val filters = LibraryFilters(years = listOf(1980, 1990))

        val updated = filters.withYears(setOf(2000, 2010))

        assertEquals(setOf(2000, 2010), updated.years.toSet())
    }

    @Test
    fun withYears_emptyClearsTheDimension() {
        val filters = LibraryFilters(years = listOf(1980))

        assertTrue(filters.withYears(emptyList()).years.isEmpty())
    }

    @Test
    fun withMinRating_setsTheFloor() {
        val updated = LibraryFilters().withMinRating(7.5f)

        assertEquals(7.5f, updated.minRating)
    }

    // ── withSortBy / withPlayedStatus ───────────────────────────────────────

    @Test
    fun withSortBy_setsTheSingleSelectOption() {
        val updated = LibraryFilters().withSortBy(SortOption.RATING)

        assertEquals(SortOption.RATING, updated.sortBy)
    }

    @Test
    fun withPlayedStatus_setsTheSingleSelectStatus() {
        val updated = LibraryFilters().withPlayedStatus(PlayedStatus.UNPLAYED)

        assertEquals(PlayedStatus.UNPLAYED, updated.playedStatus)
    }

    // ── withResumableToggled / withDownloadedToggled ────────────────────────

    @Test
    fun withResumableToggled_nullFlipsToTrue() {
        val updated = LibraryFilters().withResumableToggled()

        assertTrue(updated.isResumable == true)
    }

    @Test
    fun withResumableToggled_mirrorsTheSheetsExactFlip() {
        // The sheet's hand-rolled write was `!(isResumable == true)`, so a stored
        // false flips back to true (not to the null default) — pinned as-is.
        assertTrue(LibraryFilters(isResumable = false).withResumableToggled().isResumable == true)
        assertFalse(LibraryFilters(isResumable = true).withResumableToggled().isResumable == true)
    }

    @Test
    fun withDownloadedToggled_nullFlipsToTrue() {
        val updated = LibraryFilters().withDownloadedToggled()

        assertTrue(updated.isDownloaded == true)
    }

    @Test
    fun withDownloadedToggled_mirrorsTheChipsExactFlip() {
        assertTrue(LibraryFilters(isDownloaded = false).withDownloadedToggled().isDownloaded == true)
        assertFalse(LibraryFilters(isDownloaded = true).withDownloadedToggled().isDownloaded == true)
    }

    // ── cleared ─────────────────────────────────────────────────────────────

    @Test
    fun cleared_resetsEveryDimensionToDefault() {
        val populated = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE),
            genres = listOf("Drama"),
            years = listOf(2020),
            sortBy = SortOption.RANDOM,
            playedStatus = PlayedStatus.PLAYED,
            tags = listOf("neo-noir"),
            minRating = 6f,
            isResumable = true,
            isDownloaded = true,
        )

        assertEquals(LibraryFilters(), populated.cleared())
    }

    @Test
    fun cleared_onDefaults_isTheDefault() {
        assertEquals(LibraryFilters(), LibraryFilters().cleared())
    }

    // ── hasActiveFilters (the canonical fold) ───────────────────────────────

    @Test
    fun hasActiveFilters_defaultIsFalse() {
        assertFalse(LibraryFilters().hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_mediaTypesOnly() {
        assertTrue(LibraryFilters(mediaTypes = listOf(MediaType.MOVIE)).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_genresOnly() {
        assertTrue(LibraryFilters(genres = listOf("Drama")).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_yearsOnly() {
        assertTrue(LibraryFilters(years = listOf(2020)).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_tagsOnly() {
        assertTrue(LibraryFilters(tags = listOf("neo-noir")).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_minRatingOnly() {
        assertTrue(LibraryFilters(minRating = 6f).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_zeroMinRatingIsInactive() {
        assertFalse(LibraryFilters(minRating = 0f).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_playedStatusOnly() {
        assertTrue(LibraryFilters(playedStatus = PlayedStatus.UNPLAYED).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_sortOnly_isActive() {
        // Search's original predicate counted a non-default sort as active
        // (its Sort chip highlights and Back-press clears it) — the canonical
        // fold keeps that semantic.
        assertTrue(LibraryFilters(sortBy = SortOption.RATING).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_defaultSortIsInactive() {
        assertFalse(LibraryFilters(sortBy = SortOption.YEAR_DESC).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_resumableOnly() {
        assertTrue(LibraryFilters(isResumable = true).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_storedFalseResumableIsInactive() {
        assertFalse(LibraryFilters(isResumable = false).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_downloadedOnly() {
        assertTrue(LibraryFilters(isDownloaded = true).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_storedFalseDownloadedIsInactive() {
        assertFalse(LibraryFilters(isDownloaded = false).hasActiveFilters())
    }

    @Test
    fun hasActiveFilters_clearedAfterPopulationIsFalse() {
        val populated = LibraryFilters(
            years = listOf(2020),
            tags = listOf("neo-noir"),
            minRating = 6f,
            sortBy = SortOption.RATING,
        )

        assertFalse(populated.cleared().hasActiveFilters())
    }
}
