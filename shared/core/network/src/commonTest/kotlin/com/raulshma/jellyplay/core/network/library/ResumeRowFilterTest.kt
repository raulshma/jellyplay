package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the #157 rule shared by every `getContinueWatching` implementation
 * (JVM + wasm): a played row must never survive into Continue Watching,
 * even though /Items/Resume reports it. The wasm client has no test runner
 * of its own — it delegates here, so this is the coverage for both.
 */
class ResumeRowFilterTest {

    private fun row(id: String, played: Boolean) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
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
}
