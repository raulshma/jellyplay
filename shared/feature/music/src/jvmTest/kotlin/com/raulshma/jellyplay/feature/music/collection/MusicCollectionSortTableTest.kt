package com.raulshma.jellyplay.feature.music.collection

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The declared sort-admission table for the five music collections: each kind
 * admits exactly the [MusicSortOption] set its menus may offer, plus the
 * paged-vs-list source binding. This is the ONE pin — screens render menus
 * from `MusicCollectionKind.sortOptions`, never from hand-picked lists.
 */
class MusicCollectionSortTableTest {

    @Test
    fun sortAdmissionTable_isTheDeclaredSetPerKind() {
        // Browse artists gained DATE_PLAYED here: the richer standalone list
        // is canonical.
        assertEquals(
            listOf(
                MusicSortOption.NAME,
                MusicSortOption.DATE_ADDED,
                MusicSortOption.DATE_PLAYED,
                MusicSortOption.RANDOM,
            ),
            MusicCollectionKind.ARTISTS.sortOptions,
        )
        // Albums keep the full vocabulary (YEAR is an albums-only axis).
        assertEquals(
            listOf(
                MusicSortOption.NAME,
                MusicSortOption.DATE_ADDED,
                MusicSortOption.DATE_PLAYED,
                MusicSortOption.RANDOM,
                MusicSortOption.YEAR,
            ),
            MusicCollectionKind.ALBUMS.sortOptions,
        )
        // Tracks never offered YEAR on either side.
        assertEquals(
            listOf(
                MusicSortOption.NAME,
                MusicSortOption.DATE_ADDED,
                MusicSortOption.DATE_PLAYED,
                MusicSortOption.RANDOM,
            ),
            MusicCollectionKind.TRACKS.sortOptions,
        )
        // List-sourced collections take no sort parameter — no menu at all.
        assertEquals(emptyList(), MusicCollectionKind.GENRES.sortOptions)
        assertEquals(emptyList(), MusicCollectionKind.PLAYLISTS.sortOptions)
    }

    @Test
    fun albumsAdmitTheEntireSortVocabulary() {
        assertEquals(MusicSortOption.entries.toList(), MusicCollectionKind.ALBUMS.sortOptions)
    }

    @Test
    fun everyAdmittedOptionIsAVocabularyMember() {
        MusicCollectionKind.entries.forEach { kind ->
            kind.sortOptions.forEach { option ->
                assertTrue(MusicSortOption.entries.contains(option), "${kind.name} admits unknown option $option")
            }
        }
    }

    @Test
    fun pagedKindsBindAMediaType_listKindsAreListSourcedOnly() {
        assertEquals(MediaType.ARTIST, MusicCollectionKind.ARTISTS.mediaType)
        assertEquals(MediaType.ALBUM, MusicCollectionKind.ALBUMS.mediaType)
        assertEquals(MediaType.AUDIO, MusicCollectionKind.TRACKS.mediaType)
        assertNull(MusicCollectionKind.GENRES.mediaType)
        assertNull(MusicCollectionKind.PLAYLISTS.mediaType)
    }

    @Test
    fun sortedPagedPath_rejectsListSourcedKinds() {
        val mediaRepository: MediaRepository = mockk()
        val error = assertFailsWith<IllegalStateException> {
            SortedPagedCollection(mediaRepository, CoroutineScope(Dispatchers.Unconfined), MusicCollectionKind.GENRES)
        }
        assertTrue(error.message!!.contains("GENRES"))
    }
}
