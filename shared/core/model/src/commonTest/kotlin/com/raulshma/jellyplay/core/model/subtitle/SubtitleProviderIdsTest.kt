package com.raulshma.jellyplay.core.model.subtitle

import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Verifies [SubtitleProviderIds] — the resolver that turns a [MediaDetail]'s
 * provider-ids map + external URLs into a [SubtitleQuery] the external subtitle
 * providers (Wyzie/OpenSubtitles) can search against.
 */
class SubtitleProviderIdsTest {

    private fun detail(
        item: MediaItem,
        providerIds: Map<String, String> = emptyMap(),
        externalUrls: List<ExternalUrl> = emptyList(),
    ) = MediaDetail(
        item = item,
        providerIds = providerIds,
        externalUrls = externalUrls,
    )

    private val movie = MediaItem(id = "1", name = "The Martian", mediaType = MediaType.MOVIE)

    private val episode = MediaItem(
        id = "2",
        name = "Chapter One",
        mediaType = MediaType.EPISODE,
        seriesName = "Stranger Things",
        seasonNumber = 1,
        episodeNumber = 1,
    )

    @Test
    fun `tmdbId reads from providerIds tmdb key`() {
        val d = detail(movie, providerIds = mapOf("tmdb" to "286217"))
        assertEquals(286217, SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId falls back to tmdbid key`() {
        val d = detail(movie, providerIds = mapOf("tmdbid" to "999"))
        assertEquals(999, SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId scrapes themoviedb URL as last resort`() {
        val d = detail(movie, externalUrls = listOf(
            ExternalUrl("IMDb", "https://www.imdb.com/title/tt3659388"),
            ExternalUrl("TMDB", "https://www.themoviedb.org/movie/286217"),
        ))
        assertEquals(286217, SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId returns null when absent`() {
        val d = detail(movie)
        assertNull(SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId rejects zero as a sentinel emitted for unmatched items`() {
        // Jellyfin emits "0" for items without a TMDB match; passing id=0 to
        // Wyzie/OpenSubtitles yields a 400, so the resolver must drop it.
        val d = detail(movie, providerIds = mapOf("tmdb" to "0"))
        assertNull(SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId rejects negative values`() {
        val d = detail(movie, providerIds = mapOf("tmdbid" to "-1"))
        assertNull(SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `tmdbId ignores zero and scrapes a valid id from externalUrls`() {
        val d = detail(
            movie,
            providerIds = mapOf("tmdb" to "0"),
            externalUrls = listOf(ExternalUrl("TMDB", "https://www.themoviedb.org/movie/286217")),
        )
        assertEquals(286217, SubtitleProviderIds.tmdbId(d.providerIds, d.externalUrls))
    }

    @Test
    fun `imdbId reads from providerIds with tt prefix preserved`() {
        val d = detail(movie, providerIds = mapOf("imdb" to "tt3659388"))
        assertEquals(
SubtitleProviderIds.imdbId(d.providerIds, d.externalUrls),
"tt3659388",
)
    }

    @Test
    fun `imdbId scrapes imdb URL as last resort`() {
        val d = detail(movie, externalUrls = listOf(
            ExternalUrl("IMDb", "https://www.imdb.com/title/tt3659388/"),
        ))
        assertEquals(
SubtitleProviderIds.imdbId(d.providerIds, d.externalUrls),
"tt3659388",
)
    }

    @Test
    fun `buildQuery for a movie with tmdb id omits season episode`() {
        val d = detail(movie, providerIds = mapOf("tmdb" to "286217"))
        val q = SubtitleProviderIds.buildQuery(d)
        assertEquals(286217, q.tmdbId)
        assertNull(q.season)
        assertNull(q.episode)
        // No title fallback needed when a provider id exists.
        assertNull(q.query)
    }

    @Test
    fun `buildQuery for an episode includes season and episode`() {
        val d = detail(episode, providerIds = mapOf("tmdb" to "51812", "imdb" to "tt4574334"))
        val q = SubtitleProviderIds.buildQuery(d)
        assertEquals(51812, q.tmdbId)
        assertEquals(
q.imdbId,
"tt4574334",
)
        assertEquals(1, q.season)
        assertEquals(1, q.episode)
    }

    @Test
    fun `buildQuery falls back to title query when no provider ids`() {
        val d = detail(movie)
        val q = SubtitleProviderIds.buildQuery(d)
        assertNull(q.tmdbId)
        assertNull(q.imdbId)
        assertEquals(
q.query,
"The Martian",
)
    }

    @Test
    fun `buildQuery episode fallback includes series name and SxxExx`() {
        val d = detail(episode)
        val q = SubtitleProviderIds.buildQuery(d)
        assertNull(q.tmdbId)
        assertEquals(
q.query,
"Stranger Things S01E01",
)
    }
}
