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
 * (issue #147), including the downloaded-episode-backed Continue Watching and
 * Next Up rows.
 */
class OfflineHomeSectionsTest {

    private val titles = OfflineHomeSectionTitles(
        continueWatching = "Continue Watching",
        nextUp = "Next Up",
        recentlyDownloaded = "Recently Downloaded",
        movies = "Movies",
        series = "Series",
        music = "Music",
    )

    private val defaultPrefs = OfflineHomeSectionPrefs()

    private fun offline(
        id: String,
        type: MediaType = MediaType.MOVIE,
        playedPercentage: Double = 0.0,
        createdAt: Long = 0L,
        lastPlayedDate: String? = null,
        seriesId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        isPlayed: Boolean = false,
    ) = OfflineMediaItem(
        id = id,
        name = id,
        mediaType = type,
        playedPercentage = playedPercentage,
        createdAt = createdAt,
        lastPlayedDate = lastPlayedDate,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        isPlayed = isPlayed,
    )

    private fun episode(
        id: String,
        seriesId: String,
        seasonNumber: Int = 1,
        episodeNumber: Int,
        playedPercentage: Double = 0.0,
        isPlayed: Boolean = false,
        lastPlayedDate: String? = null,
    ) = offline(
        id = id,
        type = MediaType.EPISODE,
        playedPercentage = playedPercentage,
        lastPlayedDate = lastPlayedDate,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        isPlayed = isPlayed,
    )

    @Test
    fun `partitions the library into continue-watching recent movies series music`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("cw", playedPercentage = 40.0, lastPlayedDate = "2026-01-02"),
                offline("recent1", createdAt = 200L),
                offline("recent2", createdAt = 100L),
                offline("movie", type = MediaType.MOVIE),
                offline("show", type = MediaType.SERIES),
                offline("track", type = MediaType.AUDIO),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertEquals(
            listOf("offline_continue_watching", "offline_recently_downloaded", "offline_movies", "offline_series", "offline_music"),
            sections.map { it.id },
        )
        // Continue Watching keeps its online type so the content list renders
        // it through the same wide-card row; the poster-card rows stay
        // DOWNLOADED.
        assertEquals(HomeSectionType.CONTINUE_WATCHING, sections[0].type)
        assertTrue(sections.drop(1).all { it.type == HomeSectionType.DOWNLOADED })
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
            library = listOf(offline("movie")),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertEquals(listOf("offline_recently_downloaded", "offline_movies"), sections.map { it.id })
    }

    @Test
    fun `recent row is capped at ten items, newest first`() {
        val library = (1..15).map { offline("m$it", createdAt = it.toLong()) }

        val recent = buildOfflineHomeSections(library, emptyList(), titles, defaultPrefs)
            .first { it.id == "offline_recently_downloaded" }

        assertEquals((15 downTo 6).map { "m$it" }, recent.items.map { it.id })
    }

    @Test
    fun `series rows stay out of continue watching`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                // A SERIES row can carry a partial progress aggregate; the
                // resume points live on its episodes, not the series itself.
                offline("serial", type = MediaType.SERIES, playedPercentage = 40.0, lastPlayedDate = "2026-01-05"),
                offline("movie-progress", type = MediaType.MOVIE, playedPercentage = 20.0, lastPlayedDate = "2026-01-01"),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        val cw = sections.first { it.id == "offline_continue_watching" }
        assertEquals(listOf("movie-progress"), cw.items.map { it.id })
    }

    @Test
    fun `watched and fully-fresh items stay out of continue watching`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("watched", playedPercentage = 100.0),
                offline("fresh", playedPercentage = 0.0),
                offline("progress", playedPercentage = 40.0),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        // Only the mid-watch item lands in Continue Watching.
        assertEquals("offline_continue_watching", sections.first().id)
        assertEquals(listOf("progress"), sections.first().items.map { it.id })
    }

    @Test
    fun `in-progress episodes join continue watching and sort by last played`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("movie-progress", playedPercentage = 30.0, lastPlayedDate = "2026-01-01")),
            episodes = listOf(
                episode("e2", seriesId = "s1", episodeNumber = 2, playedPercentage = 60.0, lastPlayedDate = "2026-01-03"),
                episode("e1", seriesId = "s1", episodeNumber = 1, playedPercentage = 10.0),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val cw = sections.first { it.id == "offline_continue_watching" }
        // Most recently played first: the episode touched yesterday beats the
        // movie from last week; the untouched episode trails.
        assertEquals(listOf("e2", "movie-progress", "e1"), cw.items.map { it.id })
    }

    @Test
    fun `empty library and episodes yield no sections`() {
        assertTrue(buildOfflineHomeSections(emptyList(), emptyList(), titles, defaultPrefs).isEmpty())
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

    //region Next Up

    @Test
    fun `next up picks the first unfinished episode per series in watch order`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // s2 arrives before s1 in the input; row order must still follow
                // most recent watch activity across the series.
                episode("s2e1", seriesId = "s2", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("s2e2", seriesId = "s2", episodeNumber = 2),
                episode("s1e1", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-08"),
                episode("s1e2", seriesId = "s1", episodeNumber = 2, playedPercentage = 5.0),
                episode("s1e3", seriesId = "s1", episodeNumber = 3),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        // s1 was watched most recently (Jan 8) so its next episode leads.
        assertEquals(listOf("s1e2", "s2e2"), nextUp.items.map { it.id })
        // Next Up keeps its online type so the content list renders it through
        // the same wide-card row.
        assertEquals(HomeSectionType.NEXT_UP, nextUp.type)
    }

    @Test
    fun `next up ignores finished series and 95-percent-watched episodes`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("a1", seriesId = "allWatched", episodeNumber = 1, isPlayed = true),
                episode("a2", seriesId = "allWatched", episodeNumber = 2, playedPercentage = 96.0),
                episode("b1", seriesId = "nearly", episodeNumber = 1, playedPercentage = 94.99),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        // allWatched has no unfinished episode; "nearly" contributes its sole
        // episode, which is just under the watched threshold.
        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("b1"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up respects season and episode ordering`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // Deliberately out of order in the input list.
                episode("s2e1", seriesId = "s", seasonNumber = 2, episodeNumber = 1),
                episode("s1e2", seriesId = "s", seasonNumber = 1, episodeNumber = 2, isPlayed = true),
                episode("s1e3", seriesId = "s", seasonNumber = 1, episodeNumber = 3),
                episode("s1e1", seriesId = "s", seasonNumber = 1, episodeNumber = 1, isPlayed = true),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("s1e3"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up skips excluded series`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("e1", seriesId = "s1", episodeNumber = 1),
                episode("f1", seriesId = "s2", episodeNumber = 1),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(nextUpExcludedSeriesIds = setOf("s1")),
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("f1"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up row is capped at twenty items`() {
        val episodes = (1..25).map { i ->
            episode("e$i", seriesId = "s$i", episodeNumber = 1, lastPlayedDate = "2026-01-%02d".format(i))
        }

        val nextUp = buildOfflineHomeSections(emptyList(), episodes, titles, defaultPrefs)
            .first { it.id == "offline_next_up" }

        assertEquals(20, nextUp.items.size)
    }

    @Test
    fun `hidden cw items are filtered from continue watching`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("keep", playedPercentage = 40.0),
                offline("hide-me", playedPercentage = 50.0),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs.copy(hiddenCwItemIds = setOf("hide-me")),
        )

        val cw = sections.first { it.id == "offline_continue_watching" }
        assertEquals(listOf("keep"), cw.items.map { it.id })
    }

    @Test
    fun `disabled sections are omitted`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("cw", playedPercentage = 40.0), offline("movie")),
            episodes = listOf(episode("e1", seriesId = "s1", episodeNumber = 1)),
            titles = titles,
            prefs = defaultPrefs.copy(continueWatchingEnabled = false, nextUpEnabled = false),
        )

        assertEquals(listOf("offline_recently_downloaded", "offline_movies"), sections.map { it.id })
    }

    @Test
    fun `merge mode folds next up into continue watching and drops the row`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("cw-movie", playedPercentage = 40.0, lastPlayedDate = "2026-01-02")),
            episodes = listOf(
                episode("e1", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e2", seriesId = "s1", episodeNumber = 2, playedPercentage = 45.0),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(mergeCwAndNextUp = true),
        )

        assertEquals(listOf("offline_continue_watching"), sections.filter { it.id in setOf("offline_continue_watching", "offline_next_up") }.map { it.id })
        val cw = sections.first { it.id == "offline_continue_watching" }
        // Next Up items are appended after the CW items; e2 is mid-watch so it
        // is already in Continue Watching and must not appear twice.
        assertEquals(listOf("cw-movie", "e2"), cw.items.map { it.id })
    }

    @Test
    fun `episodes are dropped from offline rows in music mode via the mode filter`() {
        val episodes = filterOfflineByMode(
            listOf(episode("e1", seriesId = "s1", episodeNumber = 1)),
            HomeMode.MUSIC,
        )

        assertTrue(episodes.isEmpty())
        val sections = buildOfflineHomeSections(
            library = listOf(offline("track", type = MediaType.AUDIO)),
            episodes = episodes,
            titles = titles,
            prefs = defaultPrefs,
        )
        assertTrue(sections.none { it.items.any { item -> item.id == "e1" } })
    }

    //endregion
}
