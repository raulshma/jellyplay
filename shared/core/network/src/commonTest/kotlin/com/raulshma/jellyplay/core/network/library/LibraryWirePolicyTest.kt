package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the canonical commonMain wire-policy tables that survived the wasmJs
 * removal ([parseItemSortList], [parentalRatingAge], [filterByParentalRating],
 * [MediaType.toWireItemKind]) — the tables the jvmShared mappers delegate to
 * (cases ported from the deleted `LibraryWireMapperTest`; the wire-DTO
 * mapping pins they were interleaved with are covered jvmShared-side by
 * `JellyfinDtoMappersTest` / `LibraryApiClientImplTest`).
 */
class LibraryWirePolicyTest {

    private fun item(
        id: String,
        officialRating: String?,
        mediaType: MediaType = MediaType.MOVIE,
    ) = MediaItem(id = id, name = id, mediaType = mediaType, officialRating = officialRating)

    @Test
    fun `parental rating filter mirrors the engine semantics`() {
        val items = listOf(
            item("0b0f2a75-5677-4c76-a416-a1c0d9d11111", "R"),
            item("m2", "PG"),
            item("m3", officialRating = null),
            item("m4", "TV-14"),
        )
        assertEquals(4, items.filterByParentalRating(null).size, "no max rating = unfiltered")
        assertEquals(listOf("m2", "m3", "m4"), items.filterByParentalRating(13).map { it.id },
            "R (age 17) dropped, TV-14 (age 13) kept")
        assertEquals(
            listOf("0b0f2a75-5677-4c76-a416-a1c0d9d11111", "m2", "m3", "m4"),
            items.filterByParentalRating(17).map { it.id },
        )
        assertEquals(listOf("m3"), items.filterByParentalRating(0).map { it.id },
            "unrated passes even with max 0; every known rating is above 0")
    }

    @Test
    fun `parental rating age table covers the known ratings and nulls unknowns`() {
        assertEquals(0, parentalRatingAge("G"))
        assertEquals(0, parentalRatingAge("TV-Y"))
        assertEquals(0, parentalRatingAge("TV-G"))
        assertEquals(7, parentalRatingAge("PG"))
        assertEquals(7, parentalRatingAge("TV-Y7"))
        assertEquals(7, parentalRatingAge("TV-PG"))
        assertEquals(13, parentalRatingAge("PG-13"))
        assertEquals(13, parentalRatingAge("TV-14"))
        assertEquals(17, parentalRatingAge("R"))
        assertEquals(17, parentalRatingAge("TV-MA"))
        assertEquals(18, parentalRatingAge("NC-17"))
        // Case-insensitive; unknown ratings mean "no opinion".
        assertEquals(17, parentalRatingAge("r"))
        assertNull(parentalRatingAge("NotARealRating"))
    }

    @Test
    fun `media type to wire item kind table including folds and null unknown`() {
        assertEquals("MusicAlbum", MediaType.ALBUM.toWireItemKind())
        assertEquals("Audio", MediaType.MUSIC.toWireItemKind(), "MUSIC folds to Audio")
        assertEquals("Audio", MediaType.AUDIO.toWireItemKind())
        assertEquals("Book", MediaType.BOOK.toWireItemKind())
        assertEquals("Folder", MediaType.FOLDER.toWireItemKind())
        assertEquals("LiveTvChannel", MediaType.CHANNEL.toWireItemKind())
        assertEquals("LiveTvProgram", MediaType.LIVE_TV.toWireItemKind())
        assertNull(MediaType.UNKNOWN.toWireItemKind(), "UNKNOWN drops the include filter")
    }

    @Test
    fun `sort tokens parse compound keys and drop unknowns`() {
        assertEquals(
            listOf("ProductionYear", "SortName"),
            parseItemSortList("ProductionYear,SortName"),
        )
        assertEquals(listOf("Random"), parseItemSortList(" Random , bogus-token "))
        assertEquals(emptyList(), parseItemSortList(""))
        // The enum-name aliases the JVM lookup registers still resolve.
        assertEquals(listOf("SortName", "DateCreated"), parseItemSortList("sort_name,DATE_CREATED"))
        assertEquals(listOf("IsFavoriteOrLiked"), parseItemSortList("IsFavoriteOrLiked"))
    }
}
