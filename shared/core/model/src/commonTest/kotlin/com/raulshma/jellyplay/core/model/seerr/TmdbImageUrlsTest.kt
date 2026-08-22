package com.raulshma.jellyplay.core.model.seerr

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class TmdbImageUrlsTest {

    @Test
    fun `buildPosterUrl builds w500 poster url`() {
        assertEquals(
buildPosterUrl("/poster.jpg"),
"${TmdbImageUrls.POSTER_W500}/poster.jpg",
)
    }

    @Test
    fun `buildPosterUrl returns null for null path`() {
        assertNull(buildPosterUrl(null))
    }

    @Test
    fun `buildPosterUrl returns null for blank path`() {
        assertNull(buildPosterUrl(""))
        assertNull(buildPosterUrl("   "))
    }

    @Test
    fun `builders pass through absolute urls untouched`() {
        // Season/episode artwork may arrive as full URLs: the season-detail
        // endpoint rewrites stills to image.tmdb.org originals, and the TVDB
        // metadata provider serves artworks.thetvdb.com URLs.
        val tmdbStill = "https://image.tmdb.org/t/p/original/still.jpg"
        assertEquals(tmdbStill, buildStillUrl(tmdbStill))
        assertEquals(tmdbStill, buildPosterUrl(tmdbStill))

        val tvdbArt = "https://artworks.thetvdb.com/banners/episodes/123/456.jpg"
        assertEquals(tvdbArt, buildStillUrl(tvdbArt))
        assertEquals(tvdbArt, buildPosterUrl(tvdbArt))
    }

    @Test
    fun `buildStillUrl builds w500 url for bare path and null for blank`() {
        assertEquals(
buildStillUrl("/still.jpg"),
"${TmdbImageUrls.POSTER_W500}/still.jpg",
)
        assertNull(buildStillUrl(null))
        assertNull(buildStillUrl(""))
    }

    @Test
    fun `buildBackdropUrl builds w1280 backdrop url`() {
        assertEquals(
buildBackdropUrl("/backdrop.jpg"),
"${TmdbImageUrls.BACKDROP_W1280}/backdrop.jpg",
)
    }

    @Test
    fun `buildBackdropUrl returns null for null or blank`() {
        assertNull(buildBackdropUrl(null))
        assertNull(buildBackdropUrl(""))
    }

    @Test
    fun `buildProfileUrl builds h632 profile url`() {
        assertEquals(
buildProfileUrl("/profile.jpg"),
"${TmdbImageUrls.PROFILE_H632}/profile.jpg",
)
    }

    @Test
    fun `buildProfileUrl returns null for null or blank`() {
        assertNull(buildProfileUrl(null))
        assertNull(buildProfileUrl("   "))
    }
}
