package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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
        playbackPositionTicks: Long? = null,
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
        playbackPositionTicks = playbackPositionTicks,
    )

    private fun episode(
        id: String,
        seriesId: String,
        seasonNumber: Int? = 1,
        episodeNumber: Int,
        playedPercentage: Double = 0.0,
        isPlayed: Boolean = false,
        lastPlayedDate: String? = null,
        playbackPositionTicks: Long? = null,
    ) = offline(
        id = id,
        type = MediaType.EPISODE,
        playedPercentage = playedPercentage,
        lastPlayedDate = lastPlayedDate,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        isPlayed = isPlayed,
        playbackPositionTicks = playbackPositionTicks,
    )

    @Test
    fun `partitions the library into continue-watching recent movies series music`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("cw", playedPercentage = 40.0, lastPlayedDate = "2026-01-02", playbackPositionTicks = 40L),
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
                offline("serial", type = MediaType.SERIES, playedPercentage = 40.0, lastPlayedDate = "2026-01-05", playbackPositionTicks = 40L),
                offline("movie-progress", type = MediaType.MOVIE, playedPercentage = 20.0, lastPlayedDate = "2026-01-01", playbackPositionTicks = 20L),
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
                // Played: the local mirror clears the position when the watched
                // threshold flips (the server zeroes it on mark-played).
                offline("watched", playedPercentage = 100.0, isPlayed = true),
                // Watched-threshold-crossed but position not yet cleared: the
                // 95% rule excludes it, matching the server's MaxResumePct.
                offline("nearly-done", playedPercentage = 96.0, playbackPositionTicks = 96L),
                // A few seconds scrubbed in: below the minimum-progress floor
                // (the server's MinResumePct analog).
                offline("barely-started", playedPercentage = 0.4, playbackPositionTicks = 1L),
                offline("fresh", playedPercentage = 0.0),
                offline("progress", playedPercentage = 40.0, playbackPositionTicks = 40L),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        // Only the genuinely resumable item lands in Continue Watching —
        // the server's IsResumable rule: position > 0, not played, not
        // past the watched threshold, past the minimum-progress floor.
        assertEquals("offline_continue_watching", sections.first().id)
        assertEquals(listOf("progress"), sections.first().items.map { it.id })
    }

    @Test
    fun `in-progress episodes join continue watching and sort by last played`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("movie-progress", playedPercentage = 30.0, lastPlayedDate = "2026-01-01", playbackPositionTicks = 30L)),
            episodes = listOf(
                episode("e2", seriesId = "s1", episodeNumber = 2, playedPercentage = 60.0, lastPlayedDate = "2026-01-03", playbackPositionTicks = 60L),
                episode("e1", seriesId = "s1", episodeNumber = 1, playedPercentage = 10.0, playbackPositionTicks = 10L),
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
    fun `continue watching orders mixed-offset timestamps chronologically`() {
        // A UTC-stamped play at 09:30Z is LATER than a +02:00-stamped play at
        // 10:00 (08:00Z); lexicographic string ordering would reverse them.
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("plus", playedPercentage = 40.0, playbackPositionTicks = 40L, lastPlayedDate = "2026-01-10T10:00:00+02:00"),
                offline("z", playedPercentage = 40.0, playbackPositionTicks = 40L, lastPlayedDate = "2026-01-10T09:30:00Z"),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
        )

        val cw = sections.first { it.id == "offline_continue_watching" }
        assertEquals(listOf("z", "plus"), cw.items.map { it.id })
    }

    @Test
    fun `continue watching is capped at twenty items`() {
        val library = (1..25).map { i ->
            offline("m$i", playedPercentage = 50.0, playbackPositionTicks = 5L, lastPlayedDate = "2026-01-%02d".format(i))
        }

        val cw = buildOfflineHomeSections(library, emptyList(), titles, defaultPrefs)
            .first { it.id == "offline_continue_watching" }

        assertEquals(20, cw.items.size)
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

    //region Next Up — the local mirror of Jellyfin's server-side rule
    // (TVSeriesManager + NextUpService) over the downloaded episodes.

    @Test
    fun `next up picks the first unplayed episode per series in watch order`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // s2 arrives before s1 in the input; row order must still follow
                // the most recent watch activity across the series.
                episode("s2e1", seriesId = "s2", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("s2e2", seriesId = "s2", episodeNumber = 2),
                episode("s1e1", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-08"),
                episode("s1e2", seriesId = "s1", episodeNumber = 2),
                episode("s1e3", seriesId = "s1", episodeNumber = 3),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        // s1's anchor was watched most recently (Jan 8) so its next episode
        // leads — the server sorts Next Up by the anchor's last-played date.
        assertEquals(listOf("s1e2", "s2e2"), nextUp.items.map { it.id })
        // Next Up keeps its online type so the content list renders it through
        // the same wide-card row.
        assertEquals(HomeSectionType.NEXT_UP, nextUp.type)
    }

    @Test
    fun `next up takes the episode after the highest played one, never an earlier gap`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // E1 was never played; E2 was. The server anchors at the
                // HIGHEST played episode (E2) and serves E3 — it never rewinds
                // to an unplayed episode the user already watched past.
                episode("e1", seriesId = "s", episodeNumber = 1),
                episode("e2", seriesId = "s", episodeNumber = 2, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e3", seriesId = "s", episodeNumber = 3),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("e3"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up skips a series whose next episode is already in progress`() {
        // Server rule: EnableResumable=false — a mid-watch candidate belongs
        // to Continue Watching, so the series yields no Next Up entry rather
        // than surfacing a second card for the same episode.
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("e1", seriesId = "s", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e2", seriesId = "s", episodeNumber = 2, playedPercentage = 5.0, playbackPositionTicks = 50L),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertTrue(sections.none { it.id == "offline_next_up" })
        assertEquals(listOf("e2"), sections.first { it.id == "offline_continue_watching" }.items.map { it.id })
    }

    @Test
    fun `next up ignores finished series and resumable candidates`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("a1", seriesId = "allWatched", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("a2", seriesId = "allWatched", episodeNumber = 2, playedPercentage = 96.0, playbackPositionTicks = 96L),
                episode("b1", seriesId = "nearly", episodeNumber = 1, playedPercentage = 94.99, playbackPositionTicks = 94L, lastPlayedDate = "2026-01-06"),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        // allWatched: everything played/resumable — no Next Up entry.
        // nearly: its only episode is mid-watch (94.99%) → resumable →
        // Continue Watching, not Next Up.
        assertTrue(sections.none { it.id == "offline_next_up" })
        assertEquals(listOf("b1"), sections.first { it.id == "offline_continue_watching" }.items.map { it.id })
    }

    @Test
    fun `next up excludes specials and unseasoned episodes`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // Season 0 (specials) and a null season are not Next Up
                // material — the server's ParentIndexNumber != 0 filter.
                episode("sp1", seriesId = "s", seasonNumber = 0, episodeNumber = 1),
                episode("unseasoned", seriesId = "s", seasonNumber = null, episodeNumber = 2),
                episode("e1", seriesId = "s", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e2", seriesId = "s", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("e2"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up respects season and episode ordering`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                // Deliberately out of order in the input list.
                episode("s2e1", seriesId = "s", seasonNumber = 2, episodeNumber = 1),
                episode("s1e2", seriesId = "s", seasonNumber = 1, episodeNumber = 2, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("s1e3", seriesId = "s", seasonNumber = 1, episodeNumber = 3),
                episode("s1e1", seriesId = "s", seasonNumber = 1, episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-04"),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("s1e3"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up drops series without any watch activity`() {
        // The server derives Next Up series from user data rows — a series
        // nothing was ever played from never surfaces.
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("e1", seriesId = "s1", episodeNumber = 1),
                episode("f1", seriesId = "s2", episodeNumber = 1),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertTrue(sections.none { it.id == "offline_next_up" })
    }

    @Test
    fun `next up skips excluded series`() {
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("e0", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-01"),
                episode("e1", seriesId = "s1", episodeNumber = 2),
                episode("f0", seriesId = "s2", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-02"),
                episode("f1", seriesId = "s2", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(nextUpExcludedSeriesIds = setOf("s1")),
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("f1"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up date cutoff drops stale series`() {
        // Activity in January 2026; maxDays=1 → cutoff ~today → both series
        // too old for Next Up. maxDays=0 (no cutoff) keeps them.
        val episodes = listOf(
            episode("e1", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
            episode("e2", seriesId = "s1", episodeNumber = 2),
        )

        assertTrue(
            buildOfflineHomeSections(emptyList(), episodes, titles, defaultPrefs.copy(nextUpMaxDays = 1))
                .none { it.id == "offline_next_up" },
        )
        assertEquals(
            listOf("e2"),
            buildOfflineHomeSections(emptyList(), episodes, titles, defaultPrefs.copy(nextUpMaxDays = 0))
                .first { it.id == "offline_next_up" }.items.map { it.id },
        )
    }

    @Test
    fun `next up ranks a series with only resumable activity by its watch date`() {
        // s1's only activity is a mid-watch (resumable, unplayed) E1 — its
        // fresh E2 must outrank s2's entry (played Jan 5) because s1's watch
        // happened later (Jan 20), not sink to the tail for lacking a PLAYED
        // anchor.
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("s1e1", seriesId = "s1", episodeNumber = 1, playedPercentage = 40.0, playbackPositionTicks = 40L, lastPlayedDate = "2026-01-20"),
                episode("s1e2", seriesId = "s1", episodeNumber = 2),
                episode("s2e1", seriesId = "s2", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("s2e2", seriesId = "s2", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertEquals(
            listOf("s1e2", "s2e2"),
            sections.first { it.id == "offline_next_up" }.items.map { it.id },
        )
    }

    @Test
    fun `next up orders mixed-offset timestamps chronologically`() {
        // utc's 09:30Z play (Jan 10) is LATER than local's 10:00+02:00 play
        // (08:00Z); lexicographic string ordering would reverse the entries.
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("l1", seriesId = "local", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-10T10:00:00+02:00"),
                episode("l2", seriesId = "local", episodeNumber = 2),
                episode("u1", seriesId = "utc", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-10T09:30:00Z"),
                episode("u2", seriesId = "utc", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs,
        )

        assertEquals(
            listOf("u2", "l2"),
            sections.first { it.id == "offline_next_up" }.items.map { it.id },
        )
    }

    @Test
    fun `rewatching pass appends the next played episode after the most recently played one`() {
        // All three episodes played; the user most recently replayed E1
        // (Jan 5). Rewatch entry = the first PLAYED episode after E1 = E2.
        val sections = buildOfflineHomeSections(
            library = emptyList(),
            episodes = listOf(
                episode("e1", seriesId = "s", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e2", seriesId = "s", episodeNumber = 2, isPlayed = true, lastPlayedDate = "2026-01-03"),
                episode("e3", seriesId = "s", episodeNumber = 3, isPlayed = true, lastPlayedDate = "2026-01-04"),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(nextUpRewatching = true),
        )

        val nextUp = sections.first { it.id == "offline_next_up" }
        assertEquals(listOf("e2"), nextUp.items.map { it.id })
    }

    @Test
    fun `next up row is capped at twenty items`() {
        val episodes = (1..25).map { i ->
            episode("e$i", seriesId = "s$i", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-%02d".format(i))
        }.flatMap { listOf(it, episode("n${it.id}", seriesId = it.seriesId!!, episodeNumber = 2)) }

        val nextUp = buildOfflineHomeSections(emptyList(), episodes, titles, defaultPrefs)
            .first { it.id == "offline_next_up" }

        assertEquals(20, nextUp.items.size)
    }

    @Test
    fun `hidden cw items are filtered from continue watching`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("keep", playedPercentage = 40.0, playbackPositionTicks = 40L),
                offline("hide-me", playedPercentage = 50.0, playbackPositionTicks = 50L),
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
            library = listOf(offline("cw", playedPercentage = 40.0, playbackPositionTicks = 40L), offline("movie")),
            episodes = listOf(
                episode("e0", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-01"),
                episode("e1", seriesId = "s1", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(continueWatchingEnabled = false, nextUpEnabled = false),
        )

        assertEquals(listOf("offline_recently_downloaded", "offline_movies"), sections.map { it.id })
    }

    @Test
    fun `merge mode folds next up into continue watching and drops the row`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("cw-movie", playedPercentage = 40.0, lastPlayedDate = "2026-01-02", playbackPositionTicks = 40L)),
            episodes = listOf(
                episode("e1", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-05"),
                episode("e2", seriesId = "s1", episodeNumber = 2, playedPercentage = 45.0, playbackPositionTicks = 45L),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(mergeCwAndNextUp = true),
        )

        assertEquals(listOf("offline_continue_watching"), sections.filter { it.id in setOf("offline_continue_watching", "offline_next_up") }.map { it.id })
        val cw = sections.first { it.id == "offline_continue_watching" }
        // e2 is mid-watch so it is resumable — it arrives via Continue
        // Watching (the series yields no Next Up entry for it) and must not
        // appear twice.
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

    //region Section order

    @Test
    fun `continue watching and next up sort by the user's global section order`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("cw", playedPercentage = 40.0, playbackPositionTicks = 40L)),
            episodes = listOf(
                episode("e0", seriesId = "s1", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-01"),
                episode("e1", seriesId = "s1", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs.copy(
                sectionOrder = listOf(
                    HomeSectionType.NEXT_UP,
                    HomeSectionType.LATEST_MEDIA,
                    HomeSectionType.CONTINUE_WATCHING,
                    HomeSectionType.RECENTLY_ADDED,
                    HomeSectionType.RECOMMENDATIONS,
                ),
            ),
        )

        // Next Up leads because the user's order puts it first; the offline-only
        // rows (Recently Downloaded, Movies) keep their fixed tail order after
        // the ordered pair.
        assertEquals(
            listOf("offline_next_up", "offline_continue_watching", "offline_recently_downloaded", "offline_movies"),
            sections.map { it.id },
        )
    }

    @Test
    fun `rows absent from the order list keep their build order`() {
        val sections = listOf(
            sectionOf("offline_continue_watching", HomeSectionType.CONTINUE_WATCHING),
            sectionOf("offline_next_up", HomeSectionType.NEXT_UP),
            sectionOf("offline_recently_downloaded", HomeSectionType.DOWNLOADED),
        )

        // Defensive: an order list lacking the offline rows' types (the store
        // normalizes to all configurable types, so this shouldn't occur) must
        // not reshuffle anything.
        val ordered = orderOfflineSections(
            sections,
            sectionOrder = listOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
        )

        assertEquals(sections, ordered)
    }

    private fun sectionOf(id: String, type: HomeSectionType) =
        HomeSection(id = id, title = id, type = type, items = emptyList())

    //endregion

    //region cached-layout mirror (#147: "literally the home layout, filtered for downloaded")

    private fun cachedItem(id: String) = com.raulshma.jellyplay.core.model.MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
    )

    @Test
    fun `cached layout mirrors types titles order and filters items to downloads`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("dl1"),
                offline("show", type = MediaType.SERIES),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
            cachedLayout = listOf(
                HomeSection(
                    id = "srv_latest_tv",
                    title = "Latest TV",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("show"), cachedItem("not-downloaded")),
                    libraryId = "lib-tv",
                    collectionType = "tvshows",
                ),
                HomeSection(
                    id = "srv_cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = listOf(cachedItem("irrelevant")),
                ),
                HomeSection(
                    id = "srv_latest_movies",
                    title = "Latest Movies",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("dl1")),
                    libraryId = "lib-movies",
                    collectionType = "movies",
                ),
            ),
        )

        // Same-type rows keep their snapshot relative order (Latest TV before
        // Latest Movies — a generic derivation could never produce this);
        // CW empty (nothing in progress) so its row drops; non-downloaded
        // members filtered out.
        assertEquals(
            listOf("offline_srv_latest_tv", "offline_srv_latest_movies"),
            sections.map { it.id },
        )
        assertEquals(listOf("Latest TV", "Latest Movies"), sections.map { it.title })
        assertEquals(HomeSectionType.LATEST_MEDIA, sections[0].type)
        assertEquals("lib-tv", sections[0].libraryId)
        assertEquals("tvshows", sections[0].collectionType)
        assertEquals(listOf("show"), sections[0].items.map { it.id })
    }

    @Test
    fun `cached layout swaps in the locally derived continue watching and next up`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("movie", playedPercentage = 40.0, lastPlayedDate = "2026-02-01", playbackPositionTicks = 40L)),
            episodes = listOf(
                // Series with watch activity (E1 played) → its fresh E2 is the
                // Next Up member. The CW member is the partially-watched MOVIE
                // from the library.
                episode("a1", seriesId = "sB", episodeNumber = 1, isPlayed = true, lastPlayedDate = "2026-01-30"),
                episode("b1", seriesId = "sB", episodeNumber = 2),
            ),
            titles = titles,
            prefs = defaultPrefs,
            cachedLayout = listOf(
                HomeSection(
                    id = "srv_cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    // Snapshot members are NOT downloaded — the local
                    // derivation (from local playback progress) must win.
                    items = listOf(cachedItem("server-only-item")),
                ),
                HomeSection(
                    id = "srv_next_up",
                    title = "Next Up",
                    type = HomeSectionType.NEXT_UP,
                    items = listOf(cachedItem("server-only-item")),
                ),
            ),
        )

        assertEquals(
            listOf("offline_continue_watching", "offline_next_up"),
            sections.map { it.id },
        )
        assertEquals(listOf("movie"), sections[0].items.map { it.id })
        assertEquals(listOf("b1"), sections[1].items.map { it.id })
    }

    @Test
    fun `cached layout rows reorder by the user's current section order`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("dl1"),
                offline("show", type = MediaType.SERIES),
                // In-progress movie so the mirrored CW row survives with a
                // locally derived member.
                offline("movie40", playedPercentage = 40.0, lastPlayedDate = "2026-02-01", playbackPositionTicks = 40L),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs.copy(
                sectionOrder = listOf(
                    HomeSectionType.RECENTLY_ADDED,
                    HomeSectionType.CONTINUE_WATCHING,
                    HomeSectionType.LATEST_MEDIA,
                    HomeSectionType.NEXT_UP,
                    HomeSectionType.RECOMMENDATIONS,
                ),
            ),
            // Snapshot in NETWORK FETCH order (the repo persists before the
            // online ordering use case runs): Latest, CW, Recently Added.
            cachedLayout = listOf(
                HomeSection(
                    id = "srv_latest_tv",
                    title = "Latest TV",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("show")),
                    libraryId = "lib-tv",
                ),
                HomeSection(
                    id = "srv_cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = listOf(cachedItem("irrelevant")),
                ),
                HomeSection(
                    id = "srv_recent",
                    title = "Recently Added",
                    type = HomeSectionType.RECENTLY_ADDED,
                    items = listOf(cachedItem("dl1")),
                ),
            ),
        )

        // The user's order wins over the fetch order: Recently Added first,
        // then CW (locally derived member), then Latest TV. Every download is
        // covered by a mirrored row, so no fallback tail.
        assertEquals(
            listOf("offline_srv_recent", "offline_continue_watching", "offline_srv_latest_tv"),
            sections.map { it.id },
        )
    }

    @Test
    fun `cached layout fallback tail stays after the configured rows`() {
        val sections = buildOfflineHomeSections(
            library = listOf(
                offline("show", type = MediaType.SERIES),
                // Not in any snapshot row — its generic fallback row must
                // append AFTER the configured mirror rows.
                offline("uncovered"),
            ),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs.copy(
                sectionOrder = listOf(
                    HomeSectionType.LATEST_MEDIA,
                    HomeSectionType.CONTINUE_WATCHING,
                    HomeSectionType.NEXT_UP,
                    HomeSectionType.RECENTLY_ADDED,
                    HomeSectionType.RECOMMENDATIONS,
                ),
            ),
            cachedLayout = listOf(
                HomeSection(
                    id = "srv_latest_tv",
                    title = "Latest TV",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("show")),
                    libraryId = "lib-tv",
                ),
            ),
        )

        // Latest TV leads (user order puts LATEST_MEDIA first); the
        // offline-only DOWNLOADED rows have no order entry, so the fallback
        // tail keeps its fixed build order after every configured row. The
        // uncovered movie surfaces through both generic rows that can carry
        // it (Recently Downloaded and Movies).
        assertEquals(
            listOf("offline_srv_latest_tv", "offline_recently_downloaded", "offline_movies"),
            sections.map { it.id },
        )
    }

    @Test
    fun `cached layout honors current enablement and per-library overrides`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("dl1"), offline("show", type = MediaType.SERIES)),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs.copy(
                enabledSectionTypes = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.RECENTLY_ADDED),
                libraryOverrides = mapOf("lib-tv" to setOf(HomeSectionType.LATEST_MEDIA)),
            ),
            cachedLayout = listOf(
                HomeSection(
                    id = "srv_latest_tv",
                    title = "Latest TV",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("show")),
                    libraryId = "lib-tv",
                ),
                HomeSection(
                    id = "srv_recent",
                    title = "Recently Added",
                    type = HomeSectionType.RECENTLY_ADDED,
                    items = listOf(cachedItem("dl1")),
                ),
            ),
        )

        // Latest TV dropped twice over: globally enabled but disabled for its
        // library via the override; Recently Added survives. The "show"
        // download is not surfaced by any mirrored row, so the generic
        // fallback rows append for it (#147 coverage guarantee) — the movie
        // is already covered by the mirrored Recently Added row.
        assertEquals(
            listOf("offline_srv_recent", "offline_recently_downloaded", "offline_series"),
            sections.map { it.id },
        )
    }

    @Test
    fun `cached layout with no downloadable rows falls back to the generic rows`() {
        val sections = buildOfflineHomeSections(
            library = listOf(offline("movie")),
            episodes = emptyList(),
            titles = titles,
            prefs = defaultPrefs,
            cachedLayout = listOf(
                // Every snapshot member is absent from the offline store, and
                // LIVE TV is unplayable offline — the mirror yields nothing.
                HomeSection(
                    id = "srv_latest_tv",
                    title = "Latest TV",
                    type = HomeSectionType.LATEST_MEDIA,
                    items = listOf(cachedItem("not-downloaded")),
                ),
                HomeSection(
                    id = "srv_live",
                    title = "Live TV",
                    type = HomeSectionType.LIVE_TV,
                    items = listOf(cachedItem("channel")),
                ),
            ),
        )

        assertEquals(
            listOf("offline_recently_downloaded", "offline_movies"),
            sections.map { it.id },
        )
    }

    //endregion
}
