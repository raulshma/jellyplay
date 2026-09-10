package com.raulshma.jellyplay.tile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Truth table for [TilePlaybackState.policy] — the three-state QS-tile fold
 * that lived inline in JellyPlayTileService.updateTile. Every combination of
 * (isPlaying, hasSession, title) is pinned: the tile is binary
 * (ACTIVE/INACTIVE — playing is the only ACTIVE state), the paused state
 * surfaces via the paused-template label, empty titles fall back to null (the
 * service substitutes its app-name string), and the content description
 * consults isPlaying regardless of session.
 */
class TilePlaybackStateTest {

    private fun fold(
        isPlaying: Boolean,
        hasSession: Boolean,
        title: String,
    ): TilePlaybackState.Presentation = TilePlaybackState.policy(isPlaying, hasSession, title)

    @Test
    fun `playing with a session — ACTIVE, track title, playing description`() {
        assertEquals(
            TilePlaybackState.Presentation(
                state = TilePlaybackState.TileState.ACTIVE,
                label = TilePlaybackState.Label.TrackTitle("Episode 1"),
                description = TilePlaybackState.Description.PLAYING,
            ),
            fold(isPlaying = true, hasSession = true, title = "Episode 1"),
        )
    }

    @Test
    fun `paused with a session — INACTIVE, paused template around the title, paused description`() {
        assertEquals(
            TilePlaybackState.Presentation(
                state = TilePlaybackState.TileState.INACTIVE,
                label = TilePlaybackState.Label.PausedTemplate("Episode 1"),
                description = TilePlaybackState.Description.PAUSED,
            ),
            fold(isPlaying = false, hasSession = true, title = "Episode 1"),
        )
    }

    @Test
    fun `no session never shows track metadata even while the flag says playing`() {
        assertEquals(
            TilePlaybackState.Label.AppName,
            fold(isPlaying = true, hasSession = false, title = "Episode 1").label,
        )
        // ...but the content description still consults isPlaying first — the
        // original fold's ordering, pinned.
        assertEquals(
            TilePlaybackState.Description.PLAYING,
            fold(isPlaying = true, hasSession = false, title = "Episode 1").description,
        )
        assertEquals(
            TilePlaybackState.TileState.ACTIVE,
            fold(isPlaying = true, hasSession = false, title = "Episode 1").state,
        )
    }

    @Test
    fun `no session and not playing — fully app-name presentation`() {
        assertEquals(
            TilePlaybackState.Presentation(
                state = TilePlaybackState.TileState.INACTIVE,
                label = TilePlaybackState.Label.AppName,
                description = TilePlaybackState.Description.APP_NAME,
            ),
            fold(isPlaying = false, hasSession = false, title = "Episode 1"),
        )
    }

    @Test
    fun `empty titles fall back to null for the service's app-name substitution`() {
        // Paused template with a blank title.
        assertEquals(
            TilePlaybackState.Label.PausedTemplate(null),
            fold(isPlaying = false, hasSession = true, title = "").label,
        )
        // Track title with a blank title.
        assertEquals(
            TilePlaybackState.Label.TrackTitle(null),
            fold(isPlaying = true, hasSession = true, title = "").label,
        )
    }

    @Test
    fun `exhaustive truth table over both title shapes`() {
        for (isPlaying in listOf(false, true)) {
            for (hasSession in listOf(false, true)) {
                for (title in listOf("", "Episode 1")) {
                    val p = fold(isPlaying, hasSession, title)
                    val expectedState = if (isPlaying) {
                        TilePlaybackState.TileState.ACTIVE
                    } else {
                        TilePlaybackState.TileState.INACTIVE
                    }
                    val expectedLabel = when {
                        hasSession && !isPlaying ->
                            TilePlaybackState.Label.PausedTemplate(title.ifEmpty { null })
                        hasSession -> TilePlaybackState.Label.TrackTitle(title.ifEmpty { null })
                        else -> TilePlaybackState.Label.AppName
                    }
                    val expectedDescription = when {
                        isPlaying -> TilePlaybackState.Description.PLAYING
                        hasSession -> TilePlaybackState.Description.PAUSED
                        else -> TilePlaybackState.Description.APP_NAME
                    }
                    assertEquals("state($isPlaying, $hasSession)", expectedState, p.state)
                    assertEquals("label($isPlaying, $hasSession, $title)", expectedLabel, p.label)
                    assertEquals(
                        "description($isPlaying, $hasSession)",
                        expectedDescription,
                        p.description,
                    )
                }
            }
        }
    }
}
