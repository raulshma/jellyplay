package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.LibraryFolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [isExcludedTvDrawerFolder]: a Live-TV-enabled server injects two extra
 * rows into /Users/{userId}/Views — the "Live TV" UserView (collectionType
 * "livetv") and the DVR library named "Recordings" with no collection type.
 * Both duplicate destinations the drawer already offers (primary Live TV item,
 * Live TV screen's Recordings tab) and must not render as folder rows.
 */
class TvDrawerFolderFilterTest {

    private fun folder(
        id: String = "f",
        name: String = "Movies",
        collectionType: String? = "movies",
        type: String? = "CollectionFolder",
    ) = LibraryFolder(id = id, name = name, collectionType = collectionType, type = type)

    // ── Excluded ────────────────────────────────────────────────────────────

    @Test
    fun `livetv user view is excluded`() {
        assertTrue(
            isExcludedTvDrawerFolder(
                folder(id = "livetv-view", name = "Live TV", collectionType = "livetv", type = "UserView"),
            ),
        )
        assertTrue(isExcludedTvDrawerFolder(folder(name = "Live TV", collectionType = "LIVETV")))
    }

    @Test
    fun `server-created Recordings library is excluded regardless of case`() {
        assertTrue(isExcludedTvDrawerFolder(folder(name = "Recordings", collectionType = null)))
        assertTrue(isExcludedTvDrawerFolder(folder(name = "recordings", collectionType = null)))
    }

    @Test
    fun `standard collection types stay excluded`() {
        listOf("movies", "tvshows", "music", "boxsets", "playlists", "homevideos", "anime")
            .forEach { type ->
                assertTrue(isExcludedTvDrawerFolder(folder(collectionType = type)))
            }
    }

    // ── Kept ────────────────────────────────────────────────────────────────

    @Test
    fun `untyped libraries are kept unless named Recordings`() {
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Home Videos", collectionType = null)))
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Mixed Content", collectionType = null)))
    }

    @Test
    fun `typed library named Recordings is kept`() {
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Recordings", collectionType = "photos")))
        assertFalse(isExcludedTvDrawerFolder(folder(name = "My Recordings", collectionType = null)))
    }

    @Test
    fun `non-excluded types are kept`() {
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Photos", collectionType = "photos")))
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Books", collectionType = "books")))
        assertFalse(isExcludedTvDrawerFolder(folder(name = "Trailers", collectionType = "trailers")))
    }

    @Test
    fun `null collection type with null name parts is kept`() {
        assertFalse(isExcludedTvDrawerFolder(folder(name = "", collectionType = null)))
    }
}
