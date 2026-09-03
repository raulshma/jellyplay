package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the lyrics models:
 *
 *  - [LrcLibTrack.hasSyncedLyrics] / [hasPlainLyrics] treat `null` AND blank
 *    payloads as absent — an empty-string `syncedLyrics` from the LRCLIB API
 *    must not route the player into the synced-lyrics path.
 *  - A track can carry both, either, or neither lyric representation.
 *  - [LyricsLine]/[LyricsWord] keep their time/duration values verbatim
 *    (no normalization at the model boundary).
 */
class LyricsModelsTest {

    @Test
    fun `synced lyrics present only for non-blank payloads`() {
        assertTrue(LrcLibTrack(id = 1, trackName = "t", artistName = "a", syncedLyrics = "[00:01.00] hi").hasSyncedLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", syncedLyrics = null).hasSyncedLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", syncedLyrics = "").hasSyncedLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", syncedLyrics = "   ").hasSyncedLyrics)
    }

    @Test
    fun `plain lyrics present only for non-blank payloads`() {
        assertTrue(LrcLibTrack(id = 1, trackName = "t", artistName = "a", plainLyrics = "hello").hasPlainLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", plainLyrics = null).hasPlainLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", plainLyrics = "").hasPlainLyrics)
        assertFalse(LrcLibTrack(id = 1, trackName = "t", artistName = "a", plainLyrics = " \n ").hasPlainLyrics)
    }

    @Test
    fun `a track can carry both lyric representations`() {
        val track = LrcLibTrack(
            id = 1,
            trackName = "t",
            artistName = "a",
            plainLyrics = "hello",
            syncedLyrics = "[00:01.00] hello",
        )
        assertTrue(track.hasPlainLyrics)
        assertTrue(track.hasSyncedLyrics)
    }

    @Test
    fun `lyrics lines keep word timings verbatim`() {
        val line = LyricsLine(
            timeMs = 1_500L,
            text = "hello",
            durationMs = 900L,
            words = listOf(LyricsWord(timeMs = 1_500L, text = "hel", durationMs = 300L)),
        )
        assertEquals(1_500L, line.timeMs)
        assertEquals(900L, line.durationMs)
        assertEquals(1, line.words.size)
        assertEquals(300L, line.words.first().durationMs)
    }

    @Test
    fun `lyrics result defaults to the UNKNOWN source`() {
        assertEquals(LyricsSource.UNKNOWN, LyricsResult(lines = emptyList()).source)
    }
}
