package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure [LibraryBrowserReducer]. No Compose, no Robolectric,
 * no MockK — the reducer's invariants are the single source of truth for the
 * old hand-scattered guards in `LibraryViewModel`.
 *
 * The view-mode precedence test pins the divergence fix: the old `selectFolder()`
 * omitted `defaultViewMode()`, so a music folder with no saved override rendered
 * as a grid instead of a list. Here that's an asserted invariant.
 */
class LibraryBrowserReducerTest {

    private val globalDefault = LibraryViewMode.GRID

    // ---- resolveViewMode precedence (the single rule) ----

    @Test
    fun `resolveViewMode uses per-folder override when present`() {
        val state = LibraryBrowserState(folder = LibraryFolder(id = "lib-1", name = "Movies"))
        assertEquals(
            LibraryViewMode.LIST,
            LibraryBrowserReducer.resolveViewMode(state, perFolderOverride = LibraryViewMode.LIST, globalDefault),
        )
    }

    @Test
    fun `resolveViewMode ignores per-folder override in section mode`() {
        // The section's synthetic parentId must never be read as a real library's
        // saved view mode — so a per-folder override is discarded while in section.
        val state = LibraryBrowserState(
            folder = LibraryFolder(id = "lib-tv", name = "Latest Shows"),
            sectionContext = LibrarySectionContext(title = "Latest Shows", parentId = "lib-tv"),
        )
        assertEquals(
            globalDefault,
            LibraryBrowserReducer.resolveViewMode(state, perFolderOverride = LibraryViewMode.LIST, globalDefault),
        )
    }

    @Test
    fun `resolveViewMode falls back to collectionType default`() {
        // music → LIST. This is the divergence the old selectFolder() missed.
        val state = LibraryBrowserState(folder = LibraryFolder(id = "lib-music", name = "Music", collectionType = "music"))
        assertEquals(
            LibraryViewMode.LIST,
            LibraryBrowserReducer.resolveViewMode(state, perFolderOverride = null, globalDefault),
        )
    }

    @Test
    fun `resolveViewMode falls back to global default`() {
        val state = LibraryBrowserState(folder = LibraryFolder(id = "lib-1", name = "Movies"))
        assertEquals(
            globalDefault,
            LibraryBrowserReducer.resolveViewMode(state, perFolderOverride = null, globalDefault),
        )
    }

    @Test
    fun `resolveViewMode per-folder override beats collectionType default`() {
        val state = LibraryBrowserState(folder = LibraryFolder(id = "lib-music", name = "Music", collectionType = "music"))
        assertEquals(
            LibraryViewMode.GRID,
            LibraryBrowserReducer.resolveViewMode(state, perFolderOverride = LibraryViewMode.GRID, globalDefault),
        )
    }

    // ---- selectFolder applies the full 3-tier rule (the divergence fix) ----

    @Test
    fun `selectFolder applies collectionType default with no saved override`() {
        // The regression: a music folder with no saved view-mode override should
        // land on LIST, not the global GRID. The old sync path omitted this tier.
        val next = LibraryBrowserReducer.selectFolder(
            current = LibraryBrowserState(),
            folder = LibraryFolder(id = "lib-music", name = "Music", collectionType = "music"),
            filters = LibraryFilters(),
            perFolderOverride = null,
            globalDefault = globalDefault,
        )
        assertEquals(LibraryViewMode.LIST, next.viewMode)
    }

    @Test
    fun `selectFolder applies saved override`() {
        val next = LibraryBrowserReducer.selectFolder(
            current = LibraryBrowserState(),
            folder = LibraryFolder(id = "lib-1", name = "Movies"),
            filters = LibraryFilters(sortBy = SortOption.RATING),
            perFolderOverride = LibraryViewMode.THUMB,
            globalDefault = globalDefault,
        )
        assertEquals(LibraryViewMode.THUMB, next.viewMode)
        assertEquals(SortOption.RATING, next.filters.sortBy)
    }

    @Test
    fun `selectFolder with null folder clears folder and section`() {
        val next = LibraryBrowserReducer.selectFolder(
            current = LibraryBrowserState(
                folder = LibraryFolder("lib-1", "Movies"),
                sectionContext = LibrarySectionContext("x", parentId = "lib-1"),
            ),
            folder = null,
            filters = LibraryFilters(),
            perFolderOverride = null,
            globalDefault = globalDefault,
        )
        assertNull(next.folder)
        assertFalse(next.isSection)
    }

    // ---- configureSection / clearSectionMode round-trip ----

    @Test
    fun `configureSection scopes to synthetic folder and marks isSection`() {
        val next = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(),
            LibrarySectionContext(title = "Latest Movies", parentId = "lib-movies", collectionType = "movies", sortBy = SortOption.DATE_ADDED.apiValue),
        )
        assertEquals("lib-movies", next.folder?.id)
        assertEquals("Latest Movies", next.title)
        assertTrue(next.isSection)
        assertEquals(SortOption.DATE_ADDED, next.filters.sortBy)
    }

    @Test
    fun `configureSection resets groupBy to NONE`() {
        // A leftover "Group by Year" from a previous section must not shadow the
        // latest-first sort on the next "Latest X" open (#113).
        val next = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(groupBy = GroupBy.YEAR),
            LibrarySectionContext(title = "Latest Shows", parentId = "lib-tv", collectionType = "tvshows", sortBy = SortOption.DATE_LAST_CONTENT_ADDED.apiValue),
        )
        assertEquals(GroupBy.NONE, next.groupBy)
    }

    @Test
    fun `configureSection falls back to Recently Added Content when sortBy is null`() {
        // Compound items (series that just gained an episode) must rise to the top
        // even when no explicit sort is carried by the section context (#113).
        val next = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(),
            LibrarySectionContext(title = "Latest", parentId = "lib-1"),
        )
        assertEquals(SortOption.DATE_LAST_CONTENT_ADDED, next.filters.sortBy)
    }

    @Test
    fun `clearSectionMode is a no-op when not in section`() {
        val state = LibraryBrowserState(folder = LibraryFolder("lib-1", "Movies"))
        val next = LibraryBrowserReducer.clearSectionMode(state)
        assertEquals(state, next)
    }

    @Test
    fun `configureSection then clearSectionMode round-trips to default browsing`() {
        val sectioned = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(),
            LibrarySectionContext(title = "Latest Shows", parentId = "lib-tv", collectionType = "tvshows", sortBy = SortOption.DATE_ADDED.apiValue),
        )
        val cleared = LibraryBrowserReducer.clearSectionMode(sectioned)
        assertFalse(cleared.isSection)
        assertNull(cleared.folder)
        assertNull(cleared.title)
        assertEquals(LibraryFilters(), cleared.filters)
    }

    // ---- shouldPersistPerFolder ----

    @Test
    fun `shouldPersistPerFolder is false for every section state`() {
        val sectioned = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(),
            LibrarySectionContext(title = "S", parentId = "lib-1"),
        )
        assertFalse(LibraryBrowserReducer.shouldPersistPerFolder(sectioned))
    }

    @Test
    fun `shouldPersistPerFolder is true for a real folder`() {
        val state = LibraryBrowserState(folder = LibraryFolder("lib-1", "Movies"))
        assertTrue(LibraryBrowserReducer.shouldPersistPerFolder(state))
    }

    @Test
    fun `shouldPersistPerFolder is false with no folder`() {
        assertFalse(LibraryBrowserReducer.shouldPersistPerFolder(LibraryBrowserState()))
    }

    // ---- resetToDefault ----

    @Test
    fun `resetToDefault does not preserve a synthetic folder id`() {
        val sectioned = LibraryBrowserReducer.configureSection(
            LibraryBrowserState(),
            LibrarySectionContext(title = "Latest", parentId = "lib-tv"),
        )
        val result = LibraryBrowserReducer.resetToDefault(sectioned, globalDefault)
        assertNull(result.realFolderIdToClean)
        assertNull(result.state.folder)
        assertFalse(result.state.isSection)
    }

    @Test
    fun `resetToDefault cleans a real folder id`() {
        val state = LibraryBrowserState(folder = LibraryFolder("lib-1", "Movies"))
        val result = LibraryBrowserReducer.resetToDefault(state, globalDefault)
        assertEquals("lib-1", result.realFolderIdToClean)
        assertEquals(DEFAULT_POSTER_SIZE, result.state.posterSize, 0.0001f)
        assertEquals(GroupBy.NONE, result.state.groupBy)
        assertEquals(globalDefault, result.state.viewMode)
    }

    // ---- simple setters ----

    @Test
    fun `setViewMode updateFilters setGroupBy setPosterSize only touch their field`() {
        val s0 = LibraryBrowserState()
        val s1 = LibraryBrowserReducer.setViewMode(s0, LibraryViewMode.LIST)
        assertEquals(LibraryViewMode.LIST, s1.viewMode)
        val s2 = LibraryBrowserReducer.updateFilters(s1, LibraryFilters(sortBy = SortOption.RATING))
        assertEquals(SortOption.RATING, s2.filters.sortBy)
        val s3 = LibraryBrowserReducer.setGroupBy(s2, GroupBy.GENRE)
        assertEquals(GroupBy.GENRE, s3.groupBy)
        val s4 = LibraryBrowserReducer.setPosterSize(s3, 1.5f)
        assertEquals(1.5f, s4.posterSize, 0.0001f)
    }
}
