package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the [MediaItem] watch-state predicates and the
 * [MediaType] classification helpers:
 *
 *  - [MediaItem.hasPlaybackPosition] is true exactly when a non-null,
 *    strictly positive playback position exists (0 ticks is NOT a position —
 *    servers emit 0 for unstarted items).
 *  - [MediaItem.hasWatchProgress] additionally requires the item to be
 *    unfinished ([MediaItem.isPlayed] false) — the "time remaining" badge must
 *    not render on completed items that still carry a stale resume position.
 *  - [MediaType.isAudioType] / [isVideoType] / [isMusicTrack] / [isPhotoType]
 *    partition the taxonomy along the boundaries the library and download
 *    flows branch on; every entry hits exactly the documented arms.
 */
class MediaItemPredicatesTest {

    private fun item(
        playbackPositionTicks: Long? = null,
        isPlayed: Boolean = false,
    ) = MediaItem(
        id = "i",
        name = "n",
        mediaType = MediaType.MOVIE,
        playbackPositionTicks = playbackPositionTicks,
        isPlayed = isPlayed,
    )

    // ── hasPlaybackPosition ──────────────────────────────────────────────────

    @Test
    fun `null position means no playback position`() {
        assertFalse(item(playbackPositionTicks = null).hasPlaybackPosition)
    }

    @Test
    fun `zero position is not a playback position`() {
        assertFalse(item(playbackPositionTicks = 0L).hasPlaybackPosition)
    }

    @Test
    fun `positive position means playback position`() {
        assertTrue(item(playbackPositionTicks = 1L).hasPlaybackPosition)
        assertTrue(item(playbackPositionTicks = 120_000_000L).hasPlaybackPosition)
    }

    // ── hasWatchProgress ─────────────────────────────────────────────────────

    @Test
    fun `watch progress requires a position and an unfinished item`() {
        assertTrue(item(playbackPositionTicks = 500L, isPlayed = false).hasWatchProgress)
    }

    @Test
    fun `played items never report watch progress even with a stale position`() {
        assertFalse(item(playbackPositionTicks = 500L, isPlayed = true).hasWatchProgress)
    }

    @Test
    fun `no position means no watch progress regardless of played state`() {
        assertFalse(item(playbackPositionTicks = null, isPlayed = false).hasWatchProgress)
        assertFalse(item(playbackPositionTicks = 0L, isPlayed = false).hasWatchProgress)
    }

    // ── MediaType classification helpers ─────────────────────────────────────

    @Test
    fun `audio types are exactly audio music album and artist`() {
        assertEquals(
            setOf(MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST),
            MediaType.entries.filter { it.isAudioType }.toSet(),
        )
    }

    @Test
    fun `video types are exactly movie episode and music video`() {
        assertEquals(
            setOf(MediaType.MOVIE, MediaType.EPISODE, MediaType.MUSIC_VIDEO),
            MediaType.entries.filter { it.isVideoType }.toSet(),
        )
    }

    @Test
    fun `music tracks are exactly audio and music`() {
        assertEquals(
            setOf(MediaType.AUDIO, MediaType.MUSIC),
            MediaType.entries.filter { it.isMusicTrack }.toSet(),
        )
    }

    @Test
    fun `photo types are exactly photo and photo folder`() {
        assertEquals(
            setOf(MediaType.PHOTO, MediaType.PHOTO_FOLDER),
            MediaType.entries.filter { it.isPhotoType }.toSet(),
        )
    }

    @Test
    fun `classification arms are disjoint`() {
        for (type in MediaType.entries) {
            val arms = listOf(type.isAudioType, type.isVideoType, type.isPhotoType).count { it }
            assertTrue(arms <= 1, "$type matched $arms classification arms")
        }
    }
}
