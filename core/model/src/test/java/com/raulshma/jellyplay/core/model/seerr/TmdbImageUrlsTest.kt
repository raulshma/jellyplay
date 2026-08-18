package com.raulshma.jellyplay.core.model.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImageUrlsTest {

    @Test
    fun `buildPosterUrl builds w500 poster url`() {
        assertEquals(
            "${TmdbImageUrls.POSTER_W500}/poster.jpg",
            buildPosterUrl("/poster.jpg")
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
    fun `buildBackdropUrl builds w1280 backdrop url`() {
        assertEquals(
            "${TmdbImageUrls.BACKDROP_W1280}/backdrop.jpg",
            buildBackdropUrl("/backdrop.jpg")
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
            "${TmdbImageUrls.PROFILE_H632}/profile.jpg",
            buildProfileUrl("/profile.jpg")
        )
    }

    @Test
    fun `buildProfileUrl returns null for null or blank`() {
        assertNull(buildProfileUrl(null))
        assertNull(buildProfileUrl("   "))
    }
}
