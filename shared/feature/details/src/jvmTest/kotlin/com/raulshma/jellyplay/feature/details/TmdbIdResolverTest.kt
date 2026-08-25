package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tests [resolveTmdbId] — the TMDB id resolution order previously trapped as a
 * private method on [DetailViewModel] (only exercisable through the VM's async
 * surface via reflection). Now a top-level `internal` function with a direct,
 * synchronous test surface — the same extraction pattern as [SmartPlayResolver].
 *
 * Resolution order:
 *   1. `tmdb` provider id
 *   2. `tmdbid` provider id
 *   3. The first themoviedb.org external URL whose path contains a numeric id
 */
class TmdbIdResolverTest {

    private fun detail(
        providerIds: Map<String, String> = emptyMap(),
        externalUrls: List<ExternalUrl> = emptyList(),
    ) = MediaDetail(
        item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
        providerIds = providerIds,
        externalUrls = externalUrls,
    )

    // ── Resolution order ──────────────────────────────────────────────

    @Test
    fun `tmdb provider id is parsed and returned first`() {
        assertEquals(12345, resolveTmdbId(detail(providerIds = mapOf("tmdb" to "12345"))))
    }

    @Test
    fun `tmdbid provider id is parsed when tmdb is absent`() {
        assertEquals(67890, resolveTmdbId(detail(providerIds = mapOf("tmdbid" to "67890"))))
    }

    @Test
    fun `tmdb provider id takes precedence over tmdbid`() {
        // Both present → tmdb wins (first branch of the order).
        assertEquals(
            111,
            resolveTmdbId(detail(providerIds = mapOf("tmdb" to "111", "tmdbid" to "222"))),
        )
    }

    @Test
    fun `non-numeric tmdb provider id falls through to next source`() {
        // "abc" can't parse, so resolution continues to the external URL.
        assertEquals(
            55512,
            resolveTmdbId(
                detail(
                    providerIds = mapOf("tmdb" to "abc-not-a-number"),
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/55512"),
                    ),
                ),
            ),
        )
    }

    // ── External URL fallback ──────────────────────────────────────────

    @Test
    fun `themoviedb url extracts trailing numeric id`() {
        assertEquals(
            55512,
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/55512"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `themoviedb url extracts id followed by a path segment`() {
        // /movie/123/credits — regex must match a number bounded by / or end.
        assertEquals(
            123,
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://themoviedb.org/movie/123/credits"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `themoviedb url extracts id followed by a query string`() {
        assertEquals(
            987,
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/tv/987?lang=en"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `first themoviedb url wins when multiple are present`() {
        assertEquals(
            100,
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/100"),
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/movie/200"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `non-themoviedb urls are skipped even with a numeric path`() {
        // IMDb URL — should not match, falls through to null.
        assertNull(
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt123"),
                    ),
                ),
            ),
        )
    }

    // ── Null outcomes ─────────────────────────────────────────────────

    @Test
    fun `returns null when no provider ids and no themoviedb urls`() {
        assertNull(resolveTmdbId(detail()))
    }

    @Test
    fun `returns null for non-numeric provider id and no usable url`() {
        assertNull(resolveTmdbId(detail(providerIds = mapOf("tmdb" to "abc"))))
    }

    @Test
    fun `returns null for themoviedb url without a numeric id`() {
        // Host matches but the path has no digits → no regex hit.
        assertNull(
            resolveTmdbId(
                detail(
                    externalUrls = listOf(
                        ExternalUrl(name = "TMDB", url = "https://www.themoviedb.org/"),
                    ),
                ),
            ),
        )
    }
}
