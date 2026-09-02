package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests for the offline library's client-side [LibraryFilters] projection —
 * the data path behind the library screen's "Downloaded" filter.
 */
class OfflineLibraryFilteringTest {

    private fun offline(
        id: String,
        name: String = id,
        type: MediaType = MediaType.MOVIE,
        year: Int? = null,
        rating: Float? = null,
        genres: List<String> = emptyList(),
        played: Boolean = false,
        resumeTicks: Long? = null,
        createdAt: Long = 0L,
    ) = OfflineMediaItem(
        id = id,
        name = name,
        mediaType = type,
        year = year,
        communityRating = rating,
        genres = genres,
        isPlayed = played,
        playbackPositionTicks = resumeTicks,
        runTimeTicks = if (resumeTicks != null) 10_000_000L else null,
        createdAt = createdAt,
    )

    private fun ids(items: List<MediaItem>) = items.map { it.id }

    @Test
    fun `media type filter keeps only wanted types`() {
        val items = listOf(
            offline("m1", type = MediaType.MOVIE),
            offline("s1", type = MediaType.SERIES),
        )

        val filtered = items.toFilteredLibraryItems(
            LibraryFilters(mediaTypes = listOf(MediaType.SERIES)),
        )

        assertEquals(listOf("s1"), ids(filtered))
    }

    @Test
    fun `genre and year and rating filters apply over stored fields`() {
        val items = listOf(
            offline("match", genres = listOf("Action"), year = 2020, rating = 8f),
            offline("wrongGenre", genres = listOf("Drama"), year = 2020, rating = 8f),
            offline("wrongYear", genres = listOf("Action"), year = 1990, rating = 8f),
            offline("wrongRating", genres = listOf("Action"), year = 2020, rating = 5f),
        )

        val filtered = items.toFilteredLibraryItems(
            LibraryFilters(genres = listOf("Action"), years = listOf(2020), minRating = 7f),
        )

        assertEquals(listOf("match"), ids(filtered))
    }

    @Test
    fun `played status filter uses the normalized watch state`() {
        val items = listOf(
            offline("played", played = true),
            offline("unplayed"),
            // >=95% resume counts as played (same normalization the badge shows).
            offline("nearlyDone", resumeTicks = 9_600_000L),
        )

        val played = items.toFilteredLibraryItems(LibraryFilters(playedStatus = PlayedStatus.PLAYED))
        val unplayed = items.toFilteredLibraryItems(LibraryFilters(playedStatus = PlayedStatus.UNPLAYED))

        assertEquals(setOf("played", "nearlyDone"), ids(played).toSet())
        assertEquals(listOf("unplayed"), ids(unplayed))
    }

    @Test
    fun `resumable filter restricts to items with a resume position`() {
        val items = listOf(
            offline("resumable", resumeTicks = 1_000_000L),
            offline("fresh"),
        )

        val filtered = items.toFilteredLibraryItems(LibraryFilters(isResumable = true))

        assertEquals(listOf("resumable"), ids(filtered))
    }

    @Test
    fun `sort maps to offline fields including download date for Recently Added`() {
        val items = listOf(
            offline("old", year = 1990, rating = 5f, createdAt = 100L),
            offline("new", year = 2020, rating = 9f, createdAt = 300L),
            offline("mid", year = 2000, rating = 7f, createdAt = 200L),
        )

        assertEquals(
            listOf("new", "mid", "old"),
            ids(items.toFilteredLibraryItems(LibraryFilters(sortBy = SortOption.DATE_ADDED))),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            ids(items.toFilteredLibraryItems(LibraryFilters(sortBy = SortOption.YEAR_DESC))),
        )
        assertEquals(
            listOf("old", "mid", "new"),
            ids(items.toFilteredLibraryItems(LibraryFilters(sortBy = SortOption.YEAR_ASC))),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            ids(items.toFilteredLibraryItems(LibraryFilters(sortBy = SortOption.RATING))),
        )
        assertEquals(
            listOf("mid", "new", "old"),
            ids(items.toFilteredLibraryItems(LibraryFilters(sortBy = SortOption.SORT_NAME))),
        )
    }

    @Test
    fun `tags dimension is ignored rather than filtering everything out`() {
        val items = listOf(offline("m1"), offline("m2"))

        // Tags have no offline column; the chip row hides them while the
        // filter is active, and the projection must not drop items for them.
        val filtered = items.toFilteredLibraryItems(LibraryFilters(tags = listOf("fav")))

        assertEquals(2, filtered.size)
    }

    @Test
    fun `result maps to MediaItem with identity preserved`() {
        val items = listOf(offline("m1", name = "Movie"))

        val result = items.toFilteredLibraryItems(LibraryFilters())

        assertEquals("m1", result.single().id)
        assertEquals("Movie", result.single().name)
        assertFalse(result.single().isPlayed)
    }
}
