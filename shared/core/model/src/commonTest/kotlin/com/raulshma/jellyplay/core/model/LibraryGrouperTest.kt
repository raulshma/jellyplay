package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * JVM tests for [LibraryGrouper] — the pure grouping logic lifted out of
 * `GroupedLibraryContent`. Pins the two #113 crash paths: the throw on
 * [GroupBy.NONE] and the duplicate-lazy-key scatter from an unsorted snapshot.
 */
class LibraryGrouperTest {

    private fun item(id: String, name: String, mediaType: MediaType = MediaType.MOVIE, year: Int? = null, genres: List<String> = emptyList()) =
        MediaItem(id = id, name = name, mediaType = mediaType, year = year, genres = genres)

    // ---- groupKey ----

    @Test
    fun `groupKey NONE returns empty string instead of throwing`() {
        // Issue #113: persisted GroupBy flows in async and can transiently be NONE
        // while a grouped view is mounted. Throwing crashed the app on open.
        assertEquals(
LibraryGrouper.groupKey(item("1", "Anything"), GroupBy.NONE),
"",
)
    }

    @Test
    fun `groupKey NAME uppercases first letter`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "Apollo"), GroupBy.NAME),
"A",
)
    }

    @Test
    fun `groupKey NAME falls back to hash for non-letter start`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "300"), GroupBy.NAME),
"#",
)
        assertEquals(
LibraryGrouper.groupKey(item("2", "Été"), GroupBy.NAME),
"#",
)
        assertEquals(
LibraryGrouper.groupKey(item("3", "_underscore"), GroupBy.NAME),
"#",
)
    }

    @Test
    fun `groupKey NAME is case-insensitive`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "batman"), GroupBy.NAME),
"B",
)
    }

    @Test
    fun `groupKey TYPE uses mediaType name`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "X", MediaType.MOVIE), GroupBy.TYPE),
"MOVIE",
)
        assertEquals(
LibraryGrouper.groupKey(item("2", "Y", MediaType.SERIES), GroupBy.TYPE),
"SERIES",
)
    }

    @Test
    fun `groupKey GENRE uses first genre or Unknown`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "X", genres = listOf("Action", "Drama")), GroupBy.GENRE),
"Action",
)
        assertEquals(
LibraryGrouper.groupKey(item("2", "X", genres = emptyList()), GroupBy.GENRE),
"Unknown",
)
    }

    @Test
    fun `groupKey YEAR uses year or Unknown`() {
        assertEquals(
LibraryGrouper.groupKey(item("1", "X", year = 2021), GroupBy.YEAR),
"2021",
)
        assertEquals(
LibraryGrouper.groupKey(item("2", "X", year = null), GroupBy.YEAR),
"Unknown",
)
    }

    // ---- groupComparator ----

    @Test
    fun `groupComparator sorts groups contiguous for a scattered snapshot`() {
        // Issue #113: a snapshot server-sorted by a different dimension scattered
        // the same group key across the list → duplicate "header_${key}" lazy keys
        // → IllegalArgumentException crash. The comparator must make groups contiguous.
        val scattered = listOf(
            item("1", "Apple", year = 2020),
            item("2", "Banana", year = 2021),
            item("3", "Apricot", year = 2020),
            item("4", "Blueberry", year = 2021),
            item("5", "Almond", year = 2020),
        )
        val ordered = scattered.sortedWith(LibraryGrouper.groupComparator(GroupBy.NAME))
        val keys = ordered.map { LibraryGrouper.groupKey(it, GroupBy.NAME) }
        // All 'A' keys contiguous, then all 'B' keys.
        assertEquals(listOf("A", "A", "A", "B", "B"), keys)
    }

    @Test
    fun `groupComparator is stable - preserves within-group server order`() {
        val scattered = listOf(
            item("1", "Apple", year = 2020),
            item("2", "Apricot", year = 2020),
            item("3", "Almond", year = 2020),
        )
        val ordered = scattered.sortedWith(LibraryGrouper.groupComparator(GroupBy.NAME))
        // Stable sort preserves input order within the single 'A' group.
        assertEquals(listOf("1", "2", "3"), ordered.map { it.id })
    }

    @Test
    fun `groupComparator with NONE is a stable no-op order`() {
        val items = listOf(item("1", "A"), item("2", "B"), item("3", "C"))
        val ordered = items.sortedWith(LibraryGrouper.groupComparator(GroupBy.NONE))
        assertEquals(listOf("1", "2", "3"), ordered.map { it.id })
    }
}
