package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure [MediaItem.seriesImageFallback] contract: ONLY a SEASON with a
 * non-null [MediaItem.seriesId] produces a fallback list, and that list is
 * exactly `[resolve(seriesId)]` — the parent series' poster and nothing else.
 * Every other media type (and a series-less season) yields an empty list so
 * callers can pass the result unconditionally as `fallbackUrls`.
 *
 * The `remember*` composables in the same file
 * ([rememberSeriesImageFallback], [rememberEpisodeCardImage]) are
 * composition-bound and intentionally not covered here.
 */
class EpisodePosterResolverTest {

    private fun item(
        id: String,
        mediaType: MediaType,
        seriesId: String? = null,
    ) = MediaItem(id = id, name = "Item $id", mediaType = mediaType, seriesId = seriesId)

    @Test
    fun season_withSeriesId_fallsBackToParentSeriesPoster() {
        val resolvedIds = mutableListOf<String>()
        val fallbacks = item("season-1", MediaType.SEASON, seriesId = "series-7")
            .seriesImageFallback { id ->
                resolvedIds += id
                "url/$id/primary"
            }

        assertEquals(listOf("url/series-7/primary"), fallbacks)
        // The resolver is invoked with exactly the parent series id, once.
        assertEquals(listOf("series-7"), resolvedIds)
    }

    @Test
    fun season_withoutSeriesId_yieldsEmptyFallback() {
        val fallbacks = item("season-1", MediaType.SEASON, seriesId = null)
            .seriesImageFallback { "url/$it" }

        assertTrue(fallbacks.isEmpty(), "a season with no seriesId has nothing to fall back to")
    }

    @Test
    fun episode_neverFallsBack_evenWithSeriesId() {
        // The poster-card episode path is rememberEpisodeCardImage's job;
        // seriesImageFallback is season-only by contract.
        val fallbacks = item("ep-1", MediaType.EPISODE, seriesId = "series-7")
            .seriesImageFallback { "url/$it" }

        assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun movie_yieldsEmptyFallback() {
        val fallbacks = item("m-1", MediaType.MOVIE, seriesId = null)
            .seriesImageFallback { "url/$it" }

        assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun series_itself_yieldsEmptyFallback() {
        // A SERIES resolves to itself for detail routing; it is not a season,
        // so this helper must not inject its own id as a fallback.
        val fallbacks = item("series-7", MediaType.SERIES, seriesId = null)
            .seriesImageFallback { "url/$it" }

        assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun photo_yieldsEmptyFallback() {
        val fallbacks = item("p-1", MediaType.PHOTO, seriesId = null)
            .seriesImageFallback { "url/$it" }

        assertTrue(fallbacks.isEmpty())
    }
}
