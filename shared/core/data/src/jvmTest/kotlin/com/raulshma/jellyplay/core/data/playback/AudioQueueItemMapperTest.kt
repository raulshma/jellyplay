package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NameGuidPair
import com.raulshma.jellyplay.core.model.PlaylistItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the queue-item mappers' field mapping (pure, facade-internal):
 *
 *  - runTimeTicks → durationMs via 10,000-ticks-per-ms integer division, and a
 *    null runTimeTicks collapses to 0ms (unknown duration, not a crash);
 *  - artist resolves through the fallback chain albumArtist → first
 *    artistItems name → "" (never null);
 *  - album falls back to the caller-provided albumFallback only when the item
 *    carries none;
 *  - normalizationGain passes through untouched;
 *  - the PlaylistItem variant maps artist null → "" and always a null imageUrl.
 */
class AudioQueueItemMapperTest {

    private fun track(
        id: String = "t1",
        albumArtist: String? = null,
        artistItems: List<NameGuidPair> = emptyList(),
        album: String? = null,
        runTimeTicks: Long? = null,
        normalizationGain: Float? = null,
    ) = MediaItem(
        id = id,
        name = "Track $id",
        mediaType = MediaType.AUDIO,
        albumArtist = albumArtist,
        artistItems = artistItems,
        album = album,
        runTimeTicks = runTimeTicks,
        normalizationGain = normalizationGain,
    )

    // ── duration: ticks → ms ────────────────────────────────────────────

    @Test
    fun `runTimeTicks converts to milliseconds by dividing by 10_000`() {
        val item = track(runTimeTicks = 10_000_000L).toAudioQueueItem(imageUrl = null)

        assertEquals(1_000L, item.durationMs)
    }

    @Test
    fun `tick conversion truncates partial milliseconds`() {
        // 12_345_678 ticks = 1234.5678 ms → integer division keeps 1234.
        val item = track(runTimeTicks = 12_345_678L).toAudioQueueItem(imageUrl = null)

        assertEquals(1_234L, item.durationMs)
    }

    @Test
    fun `null runTimeTicks maps to 0ms`() {
        val item = track(runTimeTicks = null).toAudioQueueItem(imageUrl = null)

        assertEquals(0L, item.durationMs)
    }

    // ── artist fallback chain ───────────────────────────────────────────

    @Test
    fun `albumArtist wins the artist fallback chain`() {
        val item = track(
            albumArtist = "Album Artist",
            artistItems = listOf(NameGuidPair("Track Artist", "a1")),
        ).toAudioQueueItem(imageUrl = null)

        assertEquals("Album Artist", item.artist)
    }

    @Test
    fun `first artistItems name is the fallback when albumArtist is absent`() {
        val item = track(
            artistItems = listOf(
                NameGuidPair("First Artist", "a1"),
                NameGuidPair("Second Artist", "a2"),
            ),
        ).toAudioQueueItem(imageUrl = null)

        assertEquals("First Artist", item.artist)
    }

    @Test
    fun `artist falls back to empty string when nothing is set`() {
        val item = track().toAudioQueueItem(imageUrl = null)

        assertEquals("", item.artist)
    }

    // ── album fallback ──────────────────────────────────────────────────

    @Test
    fun `item album wins over the caller-provided albumFallback`() {
        val item = track(album = "Real Album")
            .toAudioQueueItem(imageUrl = null, albumFallback = "Queue Album")

        assertEquals("Real Album", item.album)
    }

    @Test
    fun `albumFallback is used when the item has no album`() {
        val item = track(album = null)
            .toAudioQueueItem(imageUrl = null, albumFallback = "Queue Album")

        assertEquals("Queue Album", item.album)
    }

    @Test
    fun `album stays null when neither the item nor the fallback has one`() {
        val item = track(album = null).toAudioQueueItem(imageUrl = null, albumFallback = null)

        assertEquals(null, item.album)
    }

    // ── identity + passthrough fields ───────────────────────────────────

    @Test
    fun `id name imageUrl and normalizationGain pass through`() {
        val item = track(id = "song-9", normalizationGain = -1.5f)
            .toAudioQueueItem(imageUrl = "http://host/img.jpg")

        assertEquals("song-9", item.id)
        assertEquals("Track song-9", item.name)
        assertEquals("http://host/img.jpg", item.imageUrl)
        assertEquals(-1.5f, item.normalizationGain)
        // Never carries a media source id — resolved later at playback time.
        assertEquals(null, item.mediaSourceId)
    }

    // ── PlaylistItem variant ────────────────────────────────────────────

    @Test
    fun `PlaylistItem maps artist null to empty string`() {
        val item = PlaylistItem(
            id = "p1",
            name = "Saved Song",
            artist = null,
            album = "Saved Album",
            runTimeTicks = 30_000_000L,
        ).toAudioQueueItem()

        assertEquals("p1", item.id)
        assertEquals("Saved Song", item.name)
        assertEquals("", item.artist)
        assertEquals("Saved Album", item.album)
        assertEquals(3_000L, item.durationMs)
        // Playlists carry no artwork or media source of their own.
        assertEquals(null, item.imageUrl)
        assertEquals(null, item.mediaSourceId)
        assertEquals(null, item.normalizationGain)
    }

    @Test
    fun `PlaylistItem keeps a present artist and null duration`() {
        val item = PlaylistItem(
            id = "p2",
            name = "Known Artist Song",
            artist = "Someone",
            runTimeTicks = null,
        ).toAudioQueueItem()

        assertEquals("Someone", item.artist)
        assertEquals(0L, item.durationMs)
        assertEquals(null, item.album)
    }
}
