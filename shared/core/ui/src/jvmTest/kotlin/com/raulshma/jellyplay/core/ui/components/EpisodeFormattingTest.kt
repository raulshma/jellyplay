package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class EpisodeFormattingTest {

    private fun season(
        series: String? = "Breaking Bad",
        seasonNumber: Int? = null,
        indexNumber: Int? = 1,
    ) = MediaItem(
        id = "season-1",
        name = "Season 1",
        mediaType = MediaType.SEASON,
        seriesName = series,
        seasonNumber = seasonNumber,
        indexNumber = indexNumber,
    )

    @Test
    fun `seasonContextTitle uses indexNumber as the number`() {
        // Jellyfin season items carry their number in IndexNumber (mapped to
        // indexNumber); ParentIndexNumber (mapped to seasonNumber) is null for
        // seasons, so this is the real data shape for a season.
        val item = season(series = "Breaking Bad", indexNumber = 1)
        assertEquals("S01 - Breaking Bad", item.seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle falls back to seasonNumber when indexNumber is absent`() {
        // Defensive fallback for non-Jellyfin sources that populate seasonNumber.
        val item = season(series = "Stranger Things", seasonNumber = 2, indexNumber = null)
        assertEquals("S02 - Stranger Things", item.seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle prefers indexNumber when both are present`() {
        val item = season(series = "Stranger Things", seasonNumber = 2, indexNumber = 3)
        assertEquals("S03 - Stranger Things", item.seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle pads numbers to two digits`() {
        assertEquals("S01 - Breaking Bad", season(indexNumber = 1).seasonContextTitle())
        assertEquals("S10 - Breaking Bad", season(indexNumber = 10).seasonContextTitle())
        assertEquals("S100 - Breaking Bad", season(indexNumber = 100).seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle is null for non-seasons`() {
        val movie = MediaItem(id = "m", name = "Arrival", mediaType = MediaType.MOVIE)
        assertNull(movie.seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle is null when series name is blank`() {
        assertNull(season(series = "   ").seasonContextTitle())
        assertNull(season(series = "").seasonContextTitle())
    }

    @Test
    fun `seasonContextTitle is null when the number is unknown`() {
        assertNull(season(seasonNumber = null, indexNumber = null).seasonContextTitle())
    }

    @Test
    fun `displayTitle returns season context for seasons and plain name otherwise`() {
        assertEquals("S01 - Breaking Bad", season(indexNumber = 1).displayTitle())
        assertEquals("Arrival", MediaItem(id = "m", name = "Arrival", mediaType = MediaType.MOVIE).displayTitle())
        assertEquals("Season 1", season(series = null).displayTitle())
    }

    @Test
    fun `seriesImageFallback resolves series poster for seasons only`() {
        val seasonItem = MediaItem(
            id = "season-1",
            name = "Season 1",
            mediaType = MediaType.SEASON,
            seriesName = "Breaking Bad",
            seriesId = "series-42",
        )
        val movie = MediaItem(id = "m", name = "Arrival", mediaType = MediaType.MOVIE, seriesId = "series-9")

        assertEquals(
            listOf("https://serv/img/series-42/primary"),
            seasonItem.seriesImageFallback { id -> "https://serv/img/$id/primary" },
        )
        assertEquals(emptyList<String>(), movie.seriesImageFallback { id -> "https://serv/img/$id/primary" })

        val noSeriesId = seasonItem.copy(seriesId = null)
        assertEquals(emptyList<String>(), noSeriesId.seriesImageFallback { id -> "https://serv/img/$id/primary" })
    }

    // ── episodeCode + episodeCardCode: the plain-string SxxExx core ─────────

    @Test
    fun `episodeCode renders the tight padded pair by default`() {
        assertEquals("S01E01", episodeCode(1, 1))
        assertEquals("S10E02", episodeCode(10, 2))
        assertEquals("S100E42", episodeCode(100, 42), "three-digit numbers are not truncated")
    }

    @Test
    fun `episodeCode unpadded renders bare numbers`() {
        assertEquals("S1E1", episodeCode(1, 1, padded = false))
        assertEquals("S2E24", episodeCode(2, 24, padded = false))
    }

    @Test
    fun `episodeCode separator joins the season and episode legs`() {
        assertEquals("S01 E01", episodeCode(1, 1, separator = " "))
        assertEquals("S1 E1", episodeCode(1, 1, padded = false, separator = " "))
        assertEquals("S01E01", episodeCode(1, 1), "default is the tight form")
    }

    @Test
    fun `episodeCode handles the single-number legs`() {
        assertEquals("E01", episodeCode(null, 1))
        assertEquals("E1", episodeCode(null, 1, padded = false))
        assertEquals("S01", episodeCode(1, null))
        assertEquals("S3", episodeCode(3, null, padded = false))
    }

    @Test
    fun `episodeCode is null without any number`() {
        assertNull(episodeCode(null, null))
    }

    @Test
    fun `episodeCardCode pads the episode but not the season`() {
        assertEquals("S1 E01", episodeCardCode(1, 1, separator = " "), "the chip/poster-footer form")
        assertEquals("S1E01", episodeCardCode(1, 1), "the wide-card subtitle form")
        assertEquals("S10 E02", episodeCardCode(10, 2, separator = " "))
        assertEquals("E01", episodeCardCode(null, 1))
        assertEquals("S1", episodeCardCode(1, null))
        assertNull(episodeCardCode(null, null))
    }

    @Test
    fun `episodeContextLine tag agrees with the plain core`() {
        // The styled context line wraps episodeCode's tight padded form.
        val line = episodeContextLine(MediaType.EPISODE, "Breaking Bad", 1, 5)
        assertEquals("S01E05", line.toString().substringBefore(" · "))
        assertEquals("S01E05 · Breaking Bad", line.toString())
    }

    // ── episodePlayerSubtitle: the player chrome's shared subtitle ──────────

    @Test
    fun `player subtitle joins series and unpadded code`() {
        assertEquals("The Show · S1E5", episodePlayerSubtitle("The Show", 1, 5))
    }

    @Test
    fun `player subtitle renders the code alone without a series name`() {
        assertEquals("S2E10", episodePlayerSubtitle(null, 2, 10))
        assertEquals("S2E10", episodePlayerSubtitle("   ", 2, 10), "blank names are suppressed, not joined")
    }

    @Test
    fun `player subtitle renders the series alone without a full pair`() {
        assertEquals("The Show", episodePlayerSubtitle("The Show", 1, null))
        assertEquals("The Show", episodePlayerSubtitle("The Show", null, 5))
        assertEquals("The Show", episodePlayerSubtitle("The Show", null, null))
    }

    @Test
    fun `player subtitle is null when nothing is available`() {
        assertNull(episodePlayerSubtitle(null, null, null))
        assertNull(episodePlayerSubtitle("  ", 1, null))
        assertNull(episodePlayerSubtitle("  ", null, 5))
    }
}
