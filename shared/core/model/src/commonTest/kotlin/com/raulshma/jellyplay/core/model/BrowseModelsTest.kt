package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the invariants of the browse/library-view models:
 *
 *  - [LibraryViewMode.next] is a single closed cycle
 *    (GRID → THUMB → LIST → MASONRY → GRID) — the one source of truth every
 *    view-mode cycling surface shares.
 *  - [LibraryFolder.defaultViewMode] maps the server-configured
 *    `collectionType` to the curated default: music → LIST,
 *    musicvideos/homevideos/trailers → THUMB, everything else
 *    (movies, tvshows, boxsets, photos, UNKNOWN/null) → GRID.
 *  - [PinnedHomeSection.id] is the stable composite
 *    `"<type.name>_<sourceId>"` the home list and management screen key on.
 *  - [PinnedSectionType.displayName] maps every variant to its label.
 */
class BrowseModelsTest {

    // ── LibraryViewMode.next ─────────────────────────────────────────────────

    @Test
    fun `view mode cycles in the canonical order`() {
        assertEquals(LibraryViewMode.THUMB, LibraryViewMode.GRID.next)
        assertEquals(LibraryViewMode.LIST, LibraryViewMode.THUMB.next)
        assertEquals(LibraryViewMode.MASONRY, LibraryViewMode.LIST.next)
        assertEquals(LibraryViewMode.GRID, LibraryViewMode.MASONRY.next)
    }

    @Test
    fun `cycling from any entry six times returns to itself`() {
        for (mode in LibraryViewMode.entries) {
            var current = mode
            repeat(LibraryViewMode.entries.size) { current = current.next }
            assertEquals(mode, current)
        }
    }

    // ── LibraryFolder.defaultViewMode ────────────────────────────────────────

    @Test
    fun `music libraries default to a list`() {
        assertEquals(LibraryViewMode.LIST, LibraryFolder(id = "l", name = "Music", collectionType = "music").defaultViewMode())
    }

    @Test
    fun `landscape libraries default to a thumb grid`() {
        assertEquals(LibraryViewMode.THUMB, LibraryFolder(id = "l", name = "MV", collectionType = "musicvideos").defaultViewMode())
        assertEquals(LibraryViewMode.THUMB, LibraryFolder(id = "l", name = "HV", collectionType = "homevideos").defaultViewMode())
        assertEquals(LibraryViewMode.THUMB, LibraryFolder(id = "l", name = "TR", collectionType = "trailers").defaultViewMode())
    }

    @Test
    fun `poster libraries and unknown types default to a grid`() {
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "M", collectionType = "movies").defaultViewMode())
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "TV", collectionType = "tvshows").defaultViewMode())
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "B", collectionType = "boxsets").defaultViewMode())
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "P", collectionType = "photos").defaultViewMode())
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "U", collectionType = "books").defaultViewMode())
    }

    @Test
    fun `null and empty collection types default to a grid`() {
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "N", collectionType = null).defaultViewMode())
        assertEquals(LibraryViewMode.GRID, LibraryFolder(id = "l", name = "E", collectionType = "").defaultViewMode())
    }

    // ── PinnedHomeSection / PinnedSectionType ────────────────────────────────

    @Test
    fun `pinned section id is the composite type_sourceId key`() {
        assertEquals(
            "COLLECTION_abc123",
            PinnedHomeSection(PinnedSectionType.COLLECTION, "abc123", "My Collection").id,
        )
        assertEquals(
            "GENRE_x",
            PinnedHomeSection(PinnedSectionType.GENRE, "x", "Sci-Fi").id,
        )
    }

    @Test
    fun `favorites sentinel source id is documented and stable`() {
        assertEquals("__favorites__", PinnedHomeSection.FAVORITES_SOURCE_ID)
        assertEquals(
            "FAVORITES___favorites__",
            PinnedHomeSection(PinnedSectionType.FAVORITES, PinnedHomeSection.FAVORITES_SOURCE_ID, "Favorites").id,
        )
    }

    @Test
    fun `every pinned section type has a display name`() {
        assertEquals("Collection", PinnedSectionType.COLLECTION.displayName)
        assertEquals("Playlist", PinnedSectionType.PLAYLIST.displayName)
        assertEquals("Favorites", PinnedSectionType.FAVORITES.displayName)
        assertEquals("Genre", PinnedSectionType.GENRE.displayName)
        assertEquals("Studio", PinnedSectionType.STUDIO.displayName)
    }

    @Test
    fun `home sections result carries failed section types independently of sections`() {
        val result = HomeSectionsResult(
            sections = emptyList(),
            failedSectionTypes = setOf(HomeSectionType.CONTINUE_WATCHING),
        )
        assertTrue(result.sections.isEmpty())
        assertEquals(setOf(HomeSectionType.CONTINUE_WATCHING), result.failedSectionTypes)
    }
}
