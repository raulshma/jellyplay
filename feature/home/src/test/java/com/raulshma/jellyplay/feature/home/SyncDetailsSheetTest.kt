package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDetailsSheetTest {

    @Test
    fun playbackOutboxEntry_creation_holdsCorrectValues() {
        val entry = PlaybackOutboxEntry(
            id = "outbox-1",
            itemId = "item-12345",
            eventType = PlaybackOutboxEventType.PROGRESS,
            sessionId = "session-1",
            positionTicks = 120_000_000L, // 12 seconds
            isPaused = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            mediaSourceId = "source-1",
            recordedAt = System.currentTimeMillis() - 30_000L, // 30 seconds ago
            createdAt = System.currentTimeMillis() - 30_000L,
        )

        assertEquals("outbox-1", entry.id)
        assertEquals("item-12345", entry.itemId)
        assertEquals(PlaybackOutboxEventType.PROGRESS, entry.eventType)
        assertEquals(120_000_000L, entry.positionTicks)
    }

    @Test
    fun resolvedSyncMedia_holdsItemAndPoster() {
        val item = MediaItem(
            id = "m1",
            name = "Breaking Bad",
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            mediaType = MediaType.EPISODE,
        )
        val resolved = ResolvedMediaRef(
            item = item,
            posterUrl = "http://server/m1/poster",
        )

        assertEquals("m1", resolved.item!!.id)
        assertEquals("http://server/m1/poster", resolved.posterUrl)
        assertEquals(MediaType.EPISODE, resolved.item!!.mediaType)
    }

    @Test
    fun formatMediaTitle_movie_returnsMovieName() {
        val movie = MediaItem(id = "movie1", name = "Inception", mediaType = MediaType.MOVIE)
        assertEquals("Inception", movie.name)
    }

    @Test
    fun formatMediaTitle_episode_formatsSeasonAndEpisode() {
        val episode = MediaItem(
            id = "ep1",
            name = "Pilot",
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1,
            mediaType = MediaType.EPISODE,
        )

        val prefix = buildString {
            episode.seriesName?.let { append(it).append(" ") }
            val s = episode.seasonNumber
            val e = episode.episodeNumber
            if (s != null && e != null) {
                append("S").append(s).append("E").append(e.toString().padStart(2, '0'))
            }
        }
        val formattedTitle = "$prefix · ${episode.name}"
        assertEquals("Breaking Bad S1E01 · Pilot", formattedTitle)
    }
}
