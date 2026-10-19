package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [buildDetailExternalLinks] — the merge behind the detail header's
 * external-link chips: server external urls first, then TMDB/IMDb constructed
 * from the provider ids via the existing pure resolvers
 * ([resolveTmdbId] / [resolveImdbId], whose own orders are pinned by
 * [TmdbIdResolverTest] / [PluginDetailPresentationTest]), deduped by host —
 * plus the row's admission decision [detailExternalLinksVisible] and the
 * [externalLinkHost] key it dedupes on.
 */
class DetailExternalLinksTest {

    private fun detail(
        providerIds: Map<String, String> = emptyMap(),
        externalUrls: List<ExternalUrl> = emptyList(),
        mediaType: MediaType = MediaType.MOVIE,
    ) = MediaDetail(
        item = MediaItem(id = "m1", name = "Movie", mediaType = mediaType),
        providerIds = providerIds,
        externalUrls = externalUrls,
    )

    private fun build(
        providerIds: Map<String, String> = emptyMap(),
        externalUrls: List<ExternalUrl> = emptyList(),
        mediaType: MediaType = MediaType.MOVIE,
        tmdbPathSegment: String = "movie",
    ) = buildDetailExternalLinks(
        detail = detail(providerIds, externalUrls, mediaType),
        mediaType = tmdbPathSegment,
        tmdbLabel = "TMDB",
        imdbLabel = "IMDb",
    )

    // ── Server urls come first, unchanged ──────────────────────────────

    @Test
    fun `server external urls are kept first in server order`() {
        val links = build(
            externalUrls = listOf(
                ExternalUrl(name = "Official Site", url = "https://example.com/movie"),
                ExternalUrl(name = "YouTube", url = "https://www.youtube.com/watch?v=abc"),
            ),
        )
        assertEquals(
            listOf(
                ExternalUrl(name = "Official Site", url = "https://example.com/movie"),
                ExternalUrl(name = "YouTube", url = "https://www.youtube.com/watch?v=abc"),
            ),
            links,
        )
    }

    @Test
    fun `unrelated server hosts are all kept alongside constructed links`() {
        val links = build(
            providerIds = mapOf("tmdb" to "123"),
            externalUrls = listOf(
                ExternalUrl(name = "Official Site", url = "https://example.com/movie"),
                ExternalUrl(name = "YouTube", url = "https://www.youtube.com/watch?v=abc"),
            ),
        )
        assertEquals(3, links.size)
        assertEquals("Official Site", links[0].name)
        assertEquals("YouTube", links[1].name)
        assertEquals("TMDB", links[2].name)
    }

    // ── Constructed TMDB / IMDb links ──────────────────────────────────

    @Test
    fun `tmdb and imdb links are constructed from provider ids`() {
        val links = build(
            providerIds = mapOf("tmdb" to "123", "imdb" to "tt0117731"),
        )
        assertEquals(
            listOf(
                ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/123"),
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt0117731"),
            ),
            links,
        )
    }

    @Test
    fun `tmdbid provider key is honored for the tmdb link`() {
        val links = build(providerIds = mapOf("tmdbid" to "67890"))
        assertEquals(
            listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/67890")),
            links,
        )
    }

    @Test
    fun `imdbid provider key is honored for the imdb link`() {
        val links = build(providerIds = mapOf("imdbid" to "tt0117731"))
        assertEquals(
            listOf(ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt0117731")),
            links,
        )
    }

    @Test
    fun `constructed tmdb url uses the passed media type path segment`() {
        val links = build(
            providerIds = mapOf("tmdb" to "42"),
            mediaType = MediaType.SERIES,
            tmdbPathSegment = "tv",
        )
        assertEquals("https://www.themoviedb.org/tv/42", links.single().url)
    }

    @Test
    fun `non-numeric tmdb provider id with no fallback yields no tmdb link`() {
        assertTrue(build(providerIds = mapOf("tmdb" to "abc-not-a-number")).isEmpty())
    }

    @Test
    fun `blank imdb provider id yields no imdb link`() {
        assertTrue(build(providerIds = mapOf("imdb" to " ")).isEmpty())
    }

    @Test
    fun `tmdb id scanned off a server url does not duplicate that url`() {
        // resolveTmdbId's fallback extracts the id from the server's own
        // themoviedb url; the constructed twin must lose the host dedupe.
        val links = build(
            externalUrls = listOf(
                ExternalUrl(name = "The Movie Database", url = "https://www.themoviedb.org/movie/55512"),
            ),
        )
        assertEquals(
            listOf(ExternalUrl(name = "The Movie Database", url = "https://www.themoviedb.org/movie/55512")),
            links,
        )
    }

    // ── Host dedupe ────────────────────────────────────────────────────

    @Test
    fun `server link outranks the constructed twin for the same host`() {
        val links = build(
            providerIds = mapOf("tmdb" to "123", "imdb" to "tt999"),
            externalUrls = listOf(
                ExternalUrl(name = "The Movie Database", url = "https://www.themoviedb.org/movie/9"),
            ),
        )
        assertEquals(
            listOf(
                ExternalUrl(name = "The Movie Database", url = "https://www.themoviedb.org/movie/9"),
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt999"),
            ),
            links,
        )
    }

    @Test
    fun `www and bare hosts dedupe to one entry`() {
        val links = build(
            providerIds = mapOf("imdb" to "tt999"),
            externalUrls = listOf(
                ExternalUrl(name = "IMDb", url = "https://imdb.com/title/tt123"),
            ),
        )
        assertEquals(
            listOf(ExternalUrl(name = "IMDb", url = "https://imdb.com/title/tt123")),
            links,
        )
    }

    @Test
    fun `identical duplicate server urls collapse to one chip`() {
        val links = build(
            externalUrls = listOf(
                ExternalUrl(name = "YouTube", url = "https://www.youtube.com/watch?v=abc"),
                ExternalUrl(name = "YouTube", url = "https://youtube.com/watch?v=abc"),
            ),
        )
        assertEquals(1, links.size)
        assertEquals("YouTube", links.single().name)
    }

    @Test
    fun `distinct hosts on the same site family are kept separate`() {
        // themoviedb.org vs image.tmdb.org-style subdomains: only exact hosts
        // (minus www) collide, so a different subdomain stays.
        val links = build(
            externalUrls = listOf(
                ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1"),
                ExternalUrl(name = "TMDB Forum", url = "https://forum.themoviedb.org/t/1"),
            ),
        )
        assertEquals(2, links.size)
    }

    // ── Empty outcomes ─────────────────────────────────────────────────

    @Test
    fun `no provider ids and no server urls yields an empty list`() {
        assertTrue(build().isEmpty())
    }

    @Test
    fun `empty server url entries are preserved verbatim (no host parsing gates them)`() {
        // A malformed server entry (no scheme) has no host; the merge keeps it
        // keyed on the whole url rather than dropping it.
        val links = build(
            externalUrls = listOf(ExternalUrl(name = "Broken", url = "not-a-url")),
        )
        assertEquals(
            listOf(ExternalUrl(name = "Broken", url = "not-a-url")),
            links,
        )
    }

    // ── externalLinkHost ───────────────────────────────────────────────

    @Test
    fun `host is lowercased and stripped of the www prefix`() {
        assertEquals("imdb.com", externalLinkHost("https://WWW.IMDb.com/title/tt123"))
    }

    @Test
    fun `host excludes path and query`() {
        assertEquals("themoviedb.org", externalLinkHost("https://www.themoviedb.org/tv/987?lang=en"))
    }

    @Test
    fun `scheme-less url has no host`() {
        assertNull(externalLinkHost("not-a-url"))
        assertNull(externalLinkHost("https://"))
    }

    // ── detailExternalLinksVisible (admission) ─────────────────────────

    @Test
    fun `hidden when the preference is off even with links on a remote origin`() {
        assertFalse(
            detailExternalLinksVisible(
                showExternalLinks = false,
                origin = DetailOrigin.REMOTE,
                links = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1")),
            ),
        )
    }

    @Test
    fun `hidden for a local offline-mode origin even with links and preference on`() {
        assertFalse(
            detailExternalLinksVisible(
                showExternalLinks = true,
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                links = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1")),
            ),
        )
    }

    @Test
    fun `hidden for a local remote-failure origin`() {
        assertFalse(
            detailExternalLinksVisible(
                showExternalLinks = true,
                origin = DetailOrigin.LOCAL_REMOTE_FAILURE,
                links = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1")),
            ),
        )
    }

    @Test
    fun `hidden when the merged list is empty`() {
        assertFalse(
            detailExternalLinksVisible(
                showExternalLinks = true,
                origin = DetailOrigin.REMOTE,
                links = emptyList(),
            ),
        )
    }

    @Test
    fun `shown on a remote origin with links and preference on`() {
        assertTrue(
            detailExternalLinksVisible(
                showExternalLinks = true,
                origin = DetailOrigin.REMOTE,
                links = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1")),
            ),
        )
    }

    @Test
    fun `null origin counts as remote (snapshot not yet landed is defensive-remote)`() {
        assertTrue(
            detailExternalLinksVisible(
                showExternalLinks = true,
                origin = null,
                links = listOf(ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/1")),
            ),
        )
    }
}
