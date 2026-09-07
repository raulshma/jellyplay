package com.raulshma.jellyplay.core.network.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared list-projection policy ([LIST_PROJECTION_FIELDS]): the
 * exact wire field set both library clients attach to every list-shaped
 * query, plus the compositions that extend it (the genre-rendering queries'
 * `+ "Genres"`, the playlists listing's `+ CanDelete/DateCreated` — the
 * exact expressions the two clients evaluate). Element-for-element pins,
 * not sets: the list IS the wire order (the wasm client comma-joins it, the
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
}
