package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the PRODUCTION result-row subtitle builders
 * ([jellyfinResultSubtitle] / [seerrResultSubtitle]). The previous suite
 * re-implemented the buildStrings inline and asserted the copies — a
 * production regression passed it untouched.
 */
class HomeSearchOverlayTest {

    private val movieLabel = "Movie"
    private val seriesLabel = "TV Show"
    private val musicLabel = "Music"

    // ── jellyfinResultSubtitle ─────────────────────────────────────────────

    @Test
    fun jellyfinSubtitle_movieWithYear() {
        val item = MediaItem(
            id = "m1",
            name = "Inception",
            year = 2010,
            mediaType = MediaType.MOVIE,
        )

        assertEquals("2010 · Movie", jellyfinResultSubtitle(item, movieLabel, seriesLabel, musicLabel))
    }

    @Test
    fun jellyfinSubtitle_seriesWithoutYear() {
        val item = MediaItem(
            id = "s1",
            name = "Breaking Bad",
            year = null,
            mediaType = MediaType.SERIES,
        )

        assertEquals("TV Show", jellyfinResultSubtitle(item, movieLabel, seriesLabel, musicLabel))
    }

    @Test
    fun jellyfinSubtitle_musicUsesMusicLabel() {
        val item = MediaItem(
            id = "a1",
            name = "Track",
            year = 1999,
            mediaType = MediaType.AUDIO,
        )

        assertEquals("1999 · Music", jellyfinResultSubtitle(item, movieLabel, seriesLabel, musicLabel))
    }

    @Test
    fun jellyfinSubtitle_otherTypeFallsBackToOwnName() {
        val item = MediaItem(
            id = "b1",
            name = "Album",
            year = null,
            mediaType = MediaType.ALBUM,
        )

        assertEquals("Album", jellyfinResultSubtitle(item, movieLabel, seriesLabel, musicLabel))
    }

    // ── seerrResultSubtitle ────────────────────────────────────────────────

    @Test
    fun seerrSubtitle_movieWithRating() {
        val item = SeerrSearchItem(
            id = 101,
            mediaType = "movie",
            title = "Dune",
            name = null,
            releaseDate = "2021-10-22",
            firstAirDate = null,
            posterPath = "/dune.jpg",
            overview = "Dune movie overview",
            voteAverage = 8.2f,
        )

        assertEquals("2021 · Movie · ★ 8.2", seerrResultSubtitle(item, movieLabel, seriesLabel))
    }

    @Test
    fun seerrSubtitle_tvWithZeroRating_omitsStarSegment() {
        val item = SeerrSearchItem(
            id = 102,
            mediaType = "tv",
            title = null,
            name = "Severance",
            releaseDate = null,
            firstAirDate = "2022-02-18",
            posterPath = "/severance.jpg",
            overview = "Severance tv overview",
            voteAverage = 0.0f,
        )

        assertEquals("2022 · TV Show", seerrResultSubtitle(item, movieLabel, seriesLabel))
    }

    @Test
    fun seerrSubtitle_nullRating_omitsStarSegment() {
        val item = SeerrSearchItem(
            id = 103,
            mediaType = "tv",
            title = null,
            name = "No Votes",
            releaseDate = null,
            firstAirDate = "2020-01-01",
            posterPath = null,
            overview = null,
            voteAverage = null,
        )

        assertEquals("2020 · TV Show", seerrResultSubtitle(item, movieLabel, seriesLabel))
    }

    @Test
    fun seerrSubtitle_unknownMediaType_passesThrough() {
        val item = SeerrSearchItem(
            id = 104,
            mediaType = "anime",
            title = null,
            name = "Cowboy Bebop",
            releaseDate = null,
            firstAirDate = "1998-04-03",
            posterPath = null,
            overview = null,
            voteAverage = null,
        )

        assertEquals("1998 · anime", seerrResultSubtitle(item, movieLabel, seriesLabel))
    }
}
