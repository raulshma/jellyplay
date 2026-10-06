package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.network.api.JellyPlayAnimeMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the jellyfin-plugin-jellyplay detail presentation helpers (ADR 0010)
 * — the provider-id resolutions the three plugin enrichments gate on and the
 * anime-marker → badge fold the seasons section renders. Pure tables, same
 * convention as the [resolveTmdbId] / [MissingEpisodeBadge] test surfaces.
 */
class PluginDetailPresentationTest {

    // ── resolveImdbId ───────────────────────────────────────────────────

    @Test
    fun `imdb provider id wins`() {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("imdb" to "tt0111161", "imdbid" to "tt0000001"),
        )
        assertEquals("tt0111161", resolveImdbId(detail))
    }

    @Test
    fun `imdbid fallback key is honored`() {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("imdbid" to "tt0111161"),
        )
        assertEquals("tt0111161", resolveImdbId(detail))
    }

    @Test
    fun `imdb url is the last resort`() {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            externalUrls = listOf(
                ExternalUrl(name = "Official", url = "https://example.com/movie"),
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt1375666/"),
            ),
        )
        assertEquals("tt1375666", resolveImdbId(detail))
    }

    @Test
    fun `blank provider id falls through to the url`() {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("imdb" to ""),
            externalUrls = listOf(ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt0111161/")),
        )
        assertEquals("tt0111161", resolveImdbId(detail))
    }

    @Test
    fun `no imdb anywhere resolves null`() {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
        )
        assertNull(resolveImdbId(detail))
    }

    // ── resolveProviderSeriesId ─────────────────────────────────────────

    @Test
    fun `anilist is preferred over mal and tvdb`() {
        val detail = MediaDetail(
            item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("tvdb" to "76543", "mal" to "1", "anilist" to "21"),
        )
        assertEquals("21", resolveProviderSeriesId(detail))
    }

    @Test
    fun `mal beats anidb and tvdb when anilist is absent`() {
        val detail = MediaDetail(
            item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("tvdb" to "76543", "anidb" to "23", "mal" to "1"),
        )
        assertEquals("1", resolveProviderSeriesId(detail))
    }

    @Test
    fun `tvdb is the final fallback`() {
        val detail = MediaDetail(
            item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("tvdb" to "76543"),
        )
        assertEquals("76543", resolveProviderSeriesId(detail))
    }

    @Test
    fun `blank ids are skipped`() {
        val detail = MediaDetail(
            item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("anilist" to "", "mal" to " ", "tvdb" to "76543"),
        )
        assertEquals("76543", resolveProviderSeriesId(detail))
    }

    @Test
    fun `no provider series id resolves null`() {
        val detail = MediaDetail(
            item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("tmdb" to "1234"),
        )
        assertNull(resolveProviderSeriesId(detail))
    }

    // ── animeBadges ─────────────────────────────────────────────────────

    @Test
    fun `marker types fold to badge kinds`() {
        val badges = animeBadges(
            listOf(
                JellyPlayAnimeMarker(type = "filler", episodeNumber = 3),
                JellyPlayAnimeMarker(type = "mixed", episodeNumber = 5),
                JellyPlayAnimeMarker(type = "recap", episodeNumber = 1),
            ),
        )
        assertEquals(
            mapOf(
                3 to AnimeBadgeKind.FILLER,
                5 to AnimeBadgeKind.MIXED,
                1 to AnimeBadgeKind.RECAP,
            ),
            badges,
        )
    }

    @Test
    fun `canon and unknown types render no badge`() {
        val badges = animeBadges(
            listOf(
                JellyPlayAnimeMarker(type = "canon", episodeNumber = 2),
                JellyPlayAnimeMarker(type = "something-new", episodeNumber = 9),
            ),
        )
        assertEquals(emptyMap(), badges)
    }

    @Test
    fun `type matching is case-insensitive and later duplicates win`() {
        val badges = animeBadges(
            listOf(
                JellyPlayAnimeMarker(type = "Filler", episodeNumber = 4),
                JellyPlayAnimeMarker(type = "RECAP", episodeNumber = 4),
            ),
        )
        assertEquals(mapOf(4 to AnimeBadgeKind.RECAP), badges)
    }
}
