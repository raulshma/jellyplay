package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the #157 rule shared by every `getContinueWatching` /
 * `getContinueReading` implementation: a played row must never
 * survive into a resume row, even though /Items/Resume reports it.
 */
class ResumeRowFilterTest {

    @Test
    fun `played rows are dropped even with a stale resume position`() {
        val rows = listOf(row("resumable", played = false), row("poisoned", played = true))

        assertEquals(listOf("resumable"), rows.resumableOnly().map { it.id })
    }

    @Test
    fun `an all-played row collapses to empty`() {
        val rows = listOf(row("a", played = true), row("b", played = true))

        assertEquals(emptyList(), rows.resumableOnly())
    }

    @Test
    fun `an empty row stays empty`() {
        assertEquals(emptyList(), emptyList<MediaItem>().resumableOnly())
    }

    // ── The books half ([readingResumableOnly]) — the exact complement ──

    @Test
    fun `reading rows keep only unplayed books`() {
        val rows = listOf(
            row("book-open", played = false, mediaType = MediaType.BOOK),
            row("book-done", played = true, mediaType = MediaType.BOOK),
            row("movie", played = false),
            row("episode", played = false, mediaType = MediaType.EPISODE),
        )

        assertEquals(listOf("book-open"), rows.readingResumableOnly().map { it.id })
    }

    @Test
    fun `a played book never occupies Continue Reading`() {
        val rows = listOf(row("done", played = true, mediaType = MediaType.BOOK))

        assertEquals(emptyList(), rows.readingResumableOnly())
    }

    @Test
    fun `the two resume filters partition without overlap`() {
        val rows = listOf(
            row("m", played = false),
            row("b", played = false, mediaType = MediaType.BOOK),
            row("mp", played = true),
            row("bp", played = true, mediaType = MediaType.BOOK),
        )

        val watching = rows.resumableOnly().map { it.id }
        val reading = rows.readingResumableOnly().map { it.id }
        assertEquals(listOf("m"), watching)
        assertEquals(listOf("b"), reading)
        assertTrue(watching.intersect(reading.toSet()).isEmpty())
    }

    // ── The folded post-fetch chain ([toFilteredResumeRows]) ──────────────
    // The full tail both client twins run after /UserItems/Resume: parental
    // filter → id-distinct → the #157 rule's books-or-video half.

    @Test
    fun `the fold chains parental filter, distinct ids and the played-row rule`() {
        val rows = listOf(
            row("keep", played = false),
            row("dup", played = false),
            row("dup", played = false), // server-reported duplicate id
            row("poisoned", played = true),
            row("r-rated", played = false, officialRating = "R"),
            row("pg", played = false, officialRating = "PG"),
        )

        assertEquals(
            listOf("keep", "dup", "pg"),
            rows.toFilteredResumeRows(maxParentalRating = 13, isBooks = false).map { it.id },
        )
    }

    @Test
    fun `the fold's books half keeps only unplayed books and a null max is unfiltered`() {
        val rows = listOf(
            row("book-open", played = false, mediaType = MediaType.BOOK),
            row("book-done", played = true, mediaType = MediaType.BOOK),
            row("movie", played = false),
            row("r-rated-book", played = false, mediaType = MediaType.BOOK, officialRating = "R"),
        )

        // No max rating → the R-rated book survives alongside the open one.
        assertEquals(
            listOf("book-open", "r-rated-book"),
            rows.toFilteredResumeRows(maxParentalRating = null, isBooks = true).map { it.id },
        )
        // With one, it drops like any other row.
        assertEquals(
            listOf("book-open"),
            rows.toFilteredResumeRows(maxParentalRating = 13, isBooks = true).map { it.id },
        )
    }

    @Test
    fun `the fold keeps distinct-before-filter survivors in order`() {
        val rows = listOf(
            row("a", played = false),
            row("a", played = true), // duplicate id: first survives even if the dup is played
            row("b", played = false),
        )

        assertEquals(listOf("a", "b"), rows.toFilteredResumeRows(maxParentalRating = null, isBooks = false).map { it.id })
    }

    private fun row(
        id: String,
        played: Boolean,
        mediaType: MediaType = MediaType.MOVIE,
        officialRating: String? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        isPlayed = played,
        playbackPositionTicks = 30_000_000L,
        officialRating = officialRating,
    )
}
