package com.raulshma.jellyplay.core.network.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared list-projection policy ([LIST_PROJECTION_FIELDS]): the
 * exact wire field set the library clients attach to every list-shaped
 * query, plus the compositions that extend it (the genre-rendering queries'
 * `+ "Genres"`, the playlists listing's `+ CanDelete/DateCreated` — the
 * exact expressions the clients evaluate). Element-for-element pins,
 * not sets: the list IS the wire order (the
 * JVM client resolves it in order against the SDK ItemFields enum).
 * Documented one-offs that project something else (CHILD_COUNT, TAGS, the
 * photo grid's aspect-ratio-only projection) stay at their call sites and
 * are deliberately not pinned here.
 */
class LibraryRequestPolicyTest {

    @Test
    fun `list projection is the overview and aspect ratio pair in wire order`() {
        assertEquals(listOf("Overview", "PrimaryImageAspectRatio"), LIST_PROJECTION_FIELDS)
    }

    @Test
    fun `genres variant appends Genres after the base projection`() {
        assertEquals(
            listOf("Overview", "PrimaryImageAspectRatio", "Genres"),
            LIST_PROJECTION_FIELDS + "Genres",
        )
    }

    @Test
    fun `playlists variant appends CanDelete and DateCreated after the base projection`() {
        assertEquals(
            listOf("Overview", "PrimaryImageAspectRatio", "CanDelete", "DateCreated"),
            LIST_PROJECTION_FIELDS + listOf("CanDelete", "DateCreated"),
        )
    }

    @Test
    fun `detail projection ends with the tagline field`() {
        // Element-for-element pin: Taglines rides last (the model and
        // mapper already read it; this wire field is what was missing, so
        // dropping it silently re-breaks remote taglines).
        assertEquals(
            listOf(
                "People", "Chapters", "MediaSources", "Trickplay", "ExternalUrls",
                "OriginalTitle", "ProductionLocations", "Studios", "Genres", "Overview",
                "ProviderIds", "PrimaryImageAspectRatio", "Path", "Taglines",
            ),
            DETAIL_PROJECTION_FIELDS,
        )
    }

    @Test
    fun `home-row projection is the list minimum plus MediaStreams DateCreated and Trickplay`() {
        // The home cards' fetches (resume / Next Up / Upcoming / latest) must
        // carry exactly the card-overlay reads: MediaStreams for the quality
        // badges, DateCreated for the NEW dot, Trickplay for the
        // focus preview — appended after the two-field minimum, never
        // folded into LIST_PROJECTION_FIELDS itself (search hints, collection
        // and playlist listings keep the small payload).
        assertEquals(
            listOf("Overview", "PrimaryImageAspectRatio", "MediaStreams", "DateCreated", "Trickplay"),
            HOME_ROW_PROJECTION_FIELDS,
        )
    }
}
