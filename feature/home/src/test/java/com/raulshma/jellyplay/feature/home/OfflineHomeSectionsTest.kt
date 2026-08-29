package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the offline-derived home sections — the data behind the offline
 * home, which renders the normal home content list fed with these sections
 * (issue #147).
 */
class OfflineHomeSectionsTest {

    private val titles = OfflineHomeSectionTitles(
        continueWatching = "Continue Watching",
        recentlyDownloaded = "Recently Downloaded",
        movies = "Movies",
        series = "Series",
        music = "Music",
    )

    private fun offline(
        id: String,
        type: MediaType = MediaType.MOVIE,
        playedPercentage: Double = 0.0,
        createdAt: Long = 0L,
        lastPlayedDate: String? = null,
    ) = OfflineMediaItem(
        id = id,
        name = id,
        mediaType = type,
        playedPercentage = playedPercentage,
        createdAt = createdAt,
        lastPlayedDate = lastPlayedDate,
    )

    @Test
    fun `partitions the library into continue-watching recent movies series music`() {
        val sections = buildOfflineHomeSections(
            listOf(
                offline("cw", playedPercentage = 40.0, lastPlayedDate = "2026-01-02"),
                offline("recent1", createdAt = 200L),
                offline("recent2", createdAt = 100L),
                offline("movie", type = MediaType.MOVIE),
                offline("show", type = MediaType.SERIES),
                offline("track", type = MediaType.AUDIO),
            ),
            titles,
        )

        assertEquals(
            listOf("offline_continue_watching", "offline_recently_downloaded", "offline_movies", "offline_series", "offline_music"),
            sections.map { it.id },
        )
        assertTrue(sections.all { it.type == HomeSectionType.DOWNLOADED })
        assertEquals(listOf("cw"), sections[0].items.map { it.id })
        // Recent row is newest-download first; with fewer than ten downloads it
        // holds the whole library (same behavior the dedicated screen had).
        assertEquals(listOf("recent1", "recent2"), sections[1].items.take(2).map { it.id })
        assertEquals(6, sections[1].items.size)
        // All defaults are movies, so the Movies row holds every non-CW item.
        assertEquals(listOf("cw", "recent1", "recent2", "movie"), sections[2].items.map { it.id })
        assertEquals(listOf("show"), sections[3].items.map { it.id })
        assertEquals(listOf("track"), sections[4].items.map { it.id })
    }

    @Test
    fun `empty partitions are omitted`() {
        val sections = buildOfflineHomeSections(
            listOf(offline("movie")),
            titles,
        )

        assertEquals(listOf("offline_recently_downloaded", "offline_movies"), sections.map { it.id })
    }

    @Test
    fun `recent row is capped at ten items, newest first`() {
        val library = (1..15).map { offline("m$it", createdAt = it.toLong()) }

        val recent = buildOfflineHomeSections(library, titles)
            .first { it.id == "offline_recently_downloaded" }

        assertEquals((15 downTo 6).map { "m$it" }, recent.items.map { it.id })
    }

    @Test
    fun `watched and fully-fresh items stay out of continue watching`() {
        val sections = buildOfflineHomeSections(
            listOf(
                offline("watched", playedPercentage = 100.0),
                offline("fresh", playedPercentage = 0.0),
                offline("progress", playedPercentage = 40.0),
            ),
            titles,
        )

        // Only the mid-watch item lands in Continue Watching.
        assertEquals("offline_continue_watching", sections.first().id)
        assertEquals(listOf("progress"), sections.first().items.map { it.id })
    }

    @Test
    fun `empty library yields no sections`() {
        assertTrue(buildOfflineHomeSections(emptyList(), titles).isEmpty())
    }

    @Test
    fun `mode filter separates music home from video home`() {
        val library = listOf(
            offline("movie"),
            offline("track", type = MediaType.AUDIO),
            offline("album", type = MediaType.ALBUM),
        )

        val video = filterOfflineByMode(library, HomeMode.VIDEO)
        val music = filterOfflineByMode(library, HomeMode.MUSIC)

        assertEquals(listOf("movie"), video.map { it.id })
        assertEquals(listOf("track", "album"), music.map { it.id })
    }
}
