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
}
