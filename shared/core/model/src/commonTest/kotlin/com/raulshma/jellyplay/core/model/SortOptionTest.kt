package com.raulshma.jellyplay.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the invariants of [SortOption] — the library sort taxonomy whose
 * [SortOption.apiValue] strings are passed verbatim to the Jellyfin
 * `sortBy` query parameter:
 *
 *  - Compound keys keep their exact comma-separated spelling
 *    ("ProductionYear,SortName", …) and pair with their [SortOption.sortOrder]
 *    ("Ascending"/"Descending") — a typo here silently breaks server-side
 *    sorting, so the strings are pinned.
 *  - IN_PROGRESS shares the DATE_PLAYED key (DatePlayed desc) and is
 *    disambiguated by the isResumable filter at the call site, not by a
 *    different sort string.
 *  - The enum serializes by name (the on-disk wire format the library filter
 *    store writes) and round-trips.
 */
class SortOptionTest {

    @Test
    fun `single-key sorts pin their api values`() {
        assertEquals("SortName", SortOption.SORT_NAME.apiValue)
        assertEquals("Random", SortOption.RANDOM.apiValue)
        assertEquals("Album,SortName", SortOption.ALBUM.apiValue)
        assertEquals("AlbumArtist,SortName", SortOption.ALBUM_ARTIST.apiValue)
    }

    @Test
    fun `compound sorts pair their api value with the right order`() {
        assertEquals("ProductionYear,SortName" to "Descending", SortOption.YEAR_DESC.apiValue to SortOption.YEAR_DESC.sortOrder)
        assertEquals("ProductionYear,SortName" to "Ascending", SortOption.YEAR_ASC.apiValue to SortOption.YEAR_ASC.sortOrder)
        assertEquals("CommunityRating,SortName" to "Descending", SortOption.RATING.apiValue to SortOption.RATING.sortOrder)
        assertEquals("DateCreated,SortName" to "Descending", SortOption.DATE_ADDED.apiValue to SortOption.DATE_ADDED.sortOrder)
        assertEquals("DateLastContentAdded,SortName" to "Descending", SortOption.DATE_LAST_CONTENT_ADDED.apiValue to SortOption.DATE_LAST_CONTENT_ADDED.sortOrder)
        assertEquals("PremiereDate,SortName" to "Descending", SortOption.PREMIERE_DATE.apiValue to SortOption.PREMIERE_DATE.sortOrder)
    }

    @Test
    fun `in progress shares the recently played sort key`() {
        assertEquals(SortOption.DATE_PLAYED.apiValue, SortOption.IN_PROGRESS.apiValue)
        assertEquals("Descending", SortOption.IN_PROGRESS.sortOrder)
    }

    @Test
    fun `every option has a display name and a non-empty api value`() {
        for (option in SortOption.entries) {
            assertEquals(true, option.displayName.isNotBlank(), option.name)
            assertEquals(true, option.apiValue.isNotBlank(), option.name)
            assertEquals(true, option.sortOrder == "Ascending" || option.sortOrder == "Descending", option.name)
        }
    }

    @Test
    fun `sort options serialize by name and round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        for (option in SortOption.entries) {
            val encoded = json.encodeToString(SortOption.serializer(), option)
            assertEquals("\"${option.name}\"", encoded)
            assertEquals(option, json.decodeFromString(SortOption.serializer(), encoded))
        }
    }
}
