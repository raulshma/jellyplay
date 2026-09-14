package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the #157 rule shared by every `getContinueWatching` /
 * `getContinueReading` implementation (JVM + wasm): a played row must never
 * survive into a resume row, even though /Items/Resume reports it. The wasm
 * client has no test runner of its own — it delegates here, so this is the
 * coverage for both.
 */
class ResumeRowFilterTest {

    private fun row(id: String, played: Boolean, mediaType: MediaType = MediaType.MOVIE) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        isPlayed = played,
        playbackPositionTicks = 30_000_000L,
    )

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
}
