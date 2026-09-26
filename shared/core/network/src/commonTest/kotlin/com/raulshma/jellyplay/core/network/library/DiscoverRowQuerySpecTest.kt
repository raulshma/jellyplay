package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.StudioRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the discover-row query builder: the shared [LibraryFilters]
 * dimensions ride [buildMediaItemsQuerySpec], and the discover-only
 * dimensions (studios, people, relative date windows) fold on top.
 */
class DiscoverRowQuerySpecTest {

    private val row = DiscoverRowConfig(
        id = "dr_test",
        title = "Test row",
        filters = LibraryFilters(
            mediaTypes = listOf(MediaType.MOVIE),
            genres = listOf("Sci-Fi"),
            playedStatus = PlayedStatus.UNPLAYED,
            sortBy = SortOption.RANDOM,
        ),
        studios = listOf(StudioRef("s1", "A24")),
        people = listOf(PersonRef("p1", "Someone")),
    )

    @Test
    fun `shared dimensions map like the library query`() {
        val spec = buildDiscoverRowQuerySpec(row, parentId = "lib1", startIndex = 0, limit = 20, nowEpochMs = 0L)
        assertEquals(listOf("lib1"), listOfNotNull(spec.parentId))
        assertEquals(listOf("Movie"), spec.includeKinds)
        assertEquals(listOf("Sci-Fi"), spec.genres)
        assertEquals(listOf("IsUnplayed"), spec.itemFilters)
        assertEquals(listOf("Random"), spec.sortBy)
    }

    @Test
    fun `studios and people map to server ids`() {
        val spec = buildDiscoverRowQuerySpec(row, parentId = null, startIndex = 0, limit = 20, nowEpochMs = 0L)
        assertEquals(listOf("s1"), spec.studioIds)
        assertEquals(listOf("p1"), spec.personIds)
    }

    @Test
    fun `relative windows compute epoch bounds from now`() {
        val windowed = row.copy(addedWithinDays = 30, premieredWithinYears = 5)
        val now = 1_000_000_000_000L
        val spec = buildDiscoverRowQuerySpec(windowed, parentId = null, startIndex = 0, limit = 20, nowEpochMs = now)
        assertEquals(now - 30L * 24 * 60 * 60 * 1000, spec.minDateLastSavedMs)
        assertEquals(now - 5L * 365 * 24 * 60 * 60 * 1000, spec.minPremiereDateMs)
    }

    @Test
    fun `no windows and no picks omit the params`() {
        val bare = row.copy(studios = emptyList(), people = emptyList())
        val spec = buildDiscoverRowQuerySpec(bare, parentId = null, startIndex = 0, limit = 20, nowEpochMs = 0L)
        assertNull(spec.personIds)
        assertNull(spec.studioIds)
        assertNull(spec.minDateLastSavedMs)
        assertNull(spec.minPremiereDateMs)
    }
}
