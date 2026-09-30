package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the #168 classic-rows belt-and-braces for the latest-media rows: the
 * client-side kind fold re-applies the server-side `includeItemTypes`
 * narrowing (a server that ignores the param still can't leak non-conforming
 * rows), and pins the per-folder [classicLatestKinds] policy.
 */
class LatestRowFilterTest {

    @Test
    fun `the fold drops season and episode rows when narrowed to series`() {
        val rows = listOf(
            row("series", MediaType.SERIES),
            row("season", MediaType.SEASON),
            row("episode", MediaType.EPISODE),
        )

        assertEquals(
            listOf("series"),
            rows.toFilteredLatestRows(maxParentalRating = null, allowedKinds = setOf(MediaType.SERIES)).map { it.id },
        )
    }

    @Test
    fun `a null allowed-kinds set leaves the fold unconstrained`() {
        val rows = listOf(row("series", MediaType.SERIES), row("season", MediaType.SEASON))

        assertEquals(
            listOf("series", "season"),
            rows.toFilteredLatestRows(maxParentalRating = null, allowedKinds = null).map { it.id },
        )
    }

    @Test
    fun `the fold chains the parental filter`() {
        val rows = listOf(
            row("pg-series", MediaType.SERIES, officialRating = "PG"),
            row("r-series", MediaType.SERIES, officialRating = "R"),
        )

        assertEquals(
            listOf("pg-series"),
            rows.toFilteredLatestRows(maxParentalRating = 13, allowedKinds = setOf(MediaType.SERIES)).map { it.id },
        )
    }

    @Test
    fun `the wire kinds resolve through the canonical table`() {
        // The same narrowing the fetcher passes over the wire resolves to the
        // same MediaTypes the fold filters on — one table pair, no drift.
        assertEquals(setOf(MediaType.SERIES), listOf("Series").toAllowedMediaTypes())
        assertEquals(setOf(MediaType.MOVIE), listOf("Movie").toAllowedMediaTypes())
        assertEquals(
            setOf(MediaType.EPISODE, MediaType.MOVIE, MediaType.MUSIC_VIDEO),
            CLASSIC_RESUME_LEAF_KINDS.toAllowedMediaTypes(),
        )
        assertEquals(null, emptyList<String>().toAllowedMediaTypes())
        assertEquals(null, null.toAllowedMediaTypes())
    }

    // ── The per-folder latest narrowing policy ([classicLatestKinds]) ─────

    @Test
    fun `tv folders pin to series, movie folders to movie, mixed stays unconstrained`() {
        assertEquals(listOf("Series"), classicLatestKinds("tvshows"))
        assertEquals(listOf("Movie"), classicLatestKinds("movies"))
        assertEquals(null, classicLatestKinds("mixed"))
        assertEquals(null, classicLatestKinds("books"))
        assertEquals(null, classicLatestKinds(null))
    }

    private fun row(
        id: String,
        mediaType: MediaType,
        officialRating: String? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        officialRating = officialRating,
    )
}
