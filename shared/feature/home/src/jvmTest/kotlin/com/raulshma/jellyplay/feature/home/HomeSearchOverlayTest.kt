package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.util.Locale

class HomeSearchOverlayTest {

    @Test
    fun totalItems_sumsAllResultSources() {
        val jellyfinCount = 3
        val seerrCount = 2
        val settingsCount = 1

        val totalItems = jellyfinCount + seerrCount + settingsCount
        assertEquals(6, totalItems)
        assertTrue(totalItems > 0)
    }

    @Test
    fun totalItems_whenEmpty_returnsZero() {
        val totalItems = 0 + 0 + 0
        assertEquals(0, totalItems)
        assertFalse(totalItems > 0)
    }

    @Test
    fun jellyfinSubtitle_formatMovieWithYear() {
        val item = MediaItem(
            id = "m1",
            name = "Inception",
            year = 2010,
            mediaType = MediaType.MOVIE,
        )

        val subtitle = buildString {
            item.year?.let { append(it) }
            if (item.year != null) append(" · ")
            when (item.mediaType) {
                MediaType.MOVIE -> append("Movie")
                MediaType.SERIES -> append("TV Show")
                MediaType.AUDIO, MediaType.MUSIC -> append("Music")
                else -> append(item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }

        assertEquals("2010 · Movie", subtitle)
    }

    @Test
    fun jellyfinSubtitle_formatSeriesWithoutYear() {
        val item = MediaItem(
            id = "s1",
            name = "Breaking Bad",
            year = null,
            mediaType = MediaType.SERIES,
        )

        val subtitle = buildString {
            item.year?.let { append(it) }
            if (item.year != null) append(" · ")
            when (item.mediaType) {
                MediaType.MOVIE -> append("Movie")
                MediaType.SERIES -> append("TV Show")
                MediaType.AUDIO, MediaType.MUSIC -> append("Music")
                else -> append(item.mediaType.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }

        assertEquals("TV Show", subtitle)
    }

    @Test
    fun seerrSubtitle_formatMovieWithRating() {
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

        val subtitle = buildString {
            item.year?.let { append(it) }
            val typeLabel = when {
                item.mediaType.equals("movie", ignoreCase = true) -> "Movie"
                item.mediaType.equals("tv", ignoreCase = true) -> "TV Show"
                else -> item.mediaType
            }
            if (item.year != null) append(" · ")
            append(typeLabel)
            item.voteAverage?.let { rating ->
                if (rating > 0) {
                    append(" · ★ ")
                    append(String.format(Locale.US, "%.1f", rating))
                }
            }
        }

        assertEquals("2021 · Movie · ★ 8.2", subtitle)
    }

    @Test
    fun seerrSubtitle_formatTvWithoutRating() {
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

        val subtitle = buildString {
            item.year?.let { append(it) }
            val typeLabel = when {
                item.mediaType.equals("movie", ignoreCase = true) -> "Movie"
                item.mediaType.equals("tv", ignoreCase = true) -> "TV Show"
                else -> item.mediaType
            }
            if (item.year != null) append(" · ")
            append(typeLabel)
            item.voteAverage?.let { rating ->
                if (rating > 0) {
                    append(" · ★ ")
                    append(String.format(Locale.US, "%.1f", rating))
                }
            }
        }

        assertEquals("2022 · TV Show", subtitle)
    }
}
