package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrImdbRating
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrRtRating
import com.raulshma.jellyplay.core.model.seerr.SeerrTmdbRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the external-ratings ratings strip's pure decision surface: the [DetailExternalRatings]
 * folds (Seerr payload → plain values; TMDB-only fallback + constructed link),
 * the chip order the shared [RatingsRow] renders, and the two placement
 * admissions — standalone in the header metadata row vs merged into the plugin
 * ratings row's FlowRow (plugin sources first), including the
 * pluginSimilarSuppressesStock-mirror dedupe (suppression when the plugin row
 * exists).
 */
class DetailRatingsBlockTest {

    // ── DetailExternalRatings folds ────────────────────────────────────

    @Test
    fun `fromSeerr maps every leg and prefers the imdb critics score`() {
        val ratings = DetailExternalRatings.fromSeerr(
            SeerrRatings(
                rt = SeerrRtRating(criticsScore = 96, audienceScore = 88, url = "https://rottentomatoes.com/m/x"),
                imdb = SeerrImdbRating(criticsScore = 8.7f, rating = 8.5f, url = "https://imdb.com/title/tt1"),
                tmdb = SeerrTmdbRating(rating = 7.9f, url = "https://themoviedb.org/movie/1"),
            ),
        )
        assertEquals(96, ratings.critics)
        assertEquals(88, ratings.audience)
        assertEquals(8.7f, ratings.imdb)
        assertEquals(7.9f, ratings.tmdb)
        assertEquals("https://rottentomatoes.com/m/x", ratings.rtUrl)
        assertEquals("https://imdb.com/title/tt1", ratings.imdbUrl)
        assertEquals("https://themoviedb.org/movie/1", ratings.tmdbUrl)
        assertFalse(ratings.isEmpty)
    }

    @Test
    fun `fromSeerr falls back to the imdb rating when no critics score`() {
        val ratings = DetailExternalRatings.fromSeerr(
            SeerrRatings(imdb = SeerrImdbRating(rating = 8.1f)),
        )
        assertEquals(8.1f, ratings.imdb)
        assertNull(ratings.critics)
    }

    @Test
    fun `fromSeerr of an all-null payload is empty`() {
        assertTrue(DetailExternalRatings.fromSeerr(SeerrRatings()).isEmpty)
    }

    @Test
    fun `tmdbOnly carries the vote average and the constructed provider link`() {
        val ratings = DetailExternalRatings.tmdbOnly(
            voteAverage = 7.456f,
            tmdbPathSegment = "tv",
            tmdbId = 987,
        )
        assertEquals(7.456f, ratings.tmdb)
        assertEquals("https://www.themoviedb.org/tv/987", ratings.tmdbUrl)
        assertNull(ratings.critics)
        assertNull(ratings.imdb)
    }

    @Test
    fun `empty is true only when all four scores are null`() {
        assertTrue(DetailExternalRatings().isEmpty)
        assertFalse(DetailExternalRatings(tmdb = 0f).isEmpty)
        // A url alone renders nothing — still empty.
        assertTrue(DetailExternalRatings(tmdbUrl = "https://www.themoviedb.org/movie/1").isEmpty)
    }

    // ── Chip order (the shared RatingsRow's rendering contract) ────────

    @Test
    fun `chips render in RT-critics RT-audience IMDb TMDB order`() {
        val chips = externalRatingChipSources(
            DetailExternalRatings(
                critics = 96,
                audience = 88,
                imdb = 8.7f,
                tmdb = 7.9f,
                rtUrl = "rt",
                imdbUrl = "imdb",
                tmdbUrl = "tmdb",
            ),
        )
        assertEquals(4, chips.size)
        assertEquals(96, chips[0].critics)
        assertEquals(88, chips[1].audience)
        assertEquals(8.7f, chips[2].imdb)
        assertEquals(7.9f, chips[3].tmdb)
        // The RT url rides both RT chips; the others ride their own.
        assertEquals("rt", chips[0].rtUrl)
        assertEquals("rt", chips[1].rtUrl)
        assertEquals("imdb", chips[2].imdbUrl)
        assertEquals("tmdb", chips[3].tmdbUrl)
    }

    @Test
    fun `null sources drop out of the chip plan`() {
        val chips = externalRatingChipSources(
            DetailExternalRatings(tmdb = 7.0f, tmdbUrl = "tmdb"),
        )
        assertEquals(1, chips.size)
        assertEquals(7.0f, chips.single().tmdb)
    }

    @Test
    fun `an empty ratings payload yields an empty chip plan`() {
        assertTrue(externalRatingChipSources(DetailExternalRatings()).isEmpty())
    }

    // ── Placement: standalone in the header metadata row ───────────────

    @Test
    fun `standalone renders with pref on, chips resolved, no plugin row`() {
        assertTrue(
            externalRatingsStandaloneInHeader(
                showExternalRatings = true,
                hasPluginRatings = false,
                external = DetailExternalRatings(tmdb = 7.0f),
            ),
        )
    }

    @Test
    fun `standalone hides when the plugin row is present (merge owns the chips)`() {
        assertFalse(
            externalRatingsStandaloneInHeader(
                showExternalRatings = true,
                hasPluginRatings = true,
                external = DetailExternalRatings(tmdb = 7.0f),
            ),
        )
    }

    @Test
    fun `standalone hides when the preference is off`() {
        assertFalse(
            externalRatingsStandaloneInHeader(
                showExternalRatings = false,
                hasPluginRatings = false,
                external = DetailExternalRatings(tmdb = 7.0f),
            ),
        )
    }

    @Test
    fun `standalone hides when nothing resolved`() {
        assertFalse(
            externalRatingsStandaloneInHeader(
                showExternalRatings = true,
                hasPluginRatings = false,
                external = DetailExternalRatings(),
            ),
        )
    }

    // ── Placement: merged into the plugin row (plugin ordering) ────────────

    @Test
    fun `merge admits plugin row plus resolved chips without the plugin feature`() {
        // The plugin row renders (mdblist data landed) but the external-ratings
        // enrichment's probe did NOT come back live (e.g. the feature reg
        // raced): the chips merge, plugin sources first.
        assertTrue(
            externalRatingsMergedIntoPluginRow(
                hasPluginRatings = true,
                external = DetailExternalRatings(tmdb = 7.0f, imdb = 8.0f),
                externalSuppressedByPlugin = false,
            ),
        )
    }

    @Test
    fun `merge suppresses the external chips while the plugin row exists (dedupe)`() {
        assertFalse(
            externalRatingsMergedIntoPluginRow(
                hasPluginRatings = true,
                external = DetailExternalRatings(tmdb = 7.0f, imdb = 8.0f),
                externalSuppressedByPlugin = true,
            ),
        )
    }

    @Test
    fun `probe flag alone does not suppress — the plugin row must actually render`() {
        // The pluginSimilarSuppressesStock mirror's second term: a live probe
        // against an EMPTY plugin row (probed OK, no mdblist data) leaves the
        // strip in its header placement.
        assertTrue(
            externalRatingsStandaloneInHeader(
                showExternalRatings = true,
                hasPluginRatings = false,
                external = DetailExternalRatings(tmdb = 7.0f),
            ),
        )
        assertFalse(
            externalRatingsMergedIntoPluginRow(
                hasPluginRatings = false,
                external = DetailExternalRatings(tmdb = 7.0f),
                externalSuppressedByPlugin = true,
            ),
        )
    }

    @Test
    fun `merge hides when nothing resolved`() {
        assertFalse(
            externalRatingsMergedIntoPluginRow(
                hasPluginRatings = true,
                external = DetailExternalRatings(),
                externalSuppressedByPlugin = false,
            ),
        )
    }
}
