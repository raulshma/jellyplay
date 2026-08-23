package com.raulshma.jellyplay.core.model.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrModelsTest {

    // region SeerrSearchItem
    @Test
    fun `SeerrSearchItem displayName prefers title over name`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", title = "Title", name = "Name")
        assertEquals("Title", item.displayName)
    }

    @Test
    fun `SeerrSearchItem displayName falls back to name when title absent`() {
        val item = SeerrSearchItem(id = 1, mediaType = "tv", name = "Name")
        assertEquals("Name", item.displayName)
    }

    @Test
    fun `SeerrSearchItem displayName is empty when both absent`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie")
        assertEquals("", item.displayName)
    }

    @Test
    fun `SeerrSearchItem year derives from releaseDate for movies`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", releaseDate = "2021-07-09")
        assertEquals(2021, item.year)
    }

    @Test
    fun `SeerrSearchItem year derives from firstAirDate for tv`() {
        val item = SeerrSearchItem(id = 1, mediaType = "tv", firstAirDate = "2008-01-20")
        assertEquals(2008, item.year)
    }

    @Test
    fun `SeerrSearchItem year is null when dates absent`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie")
        assertNull(item.year)
    }

    @Test
    fun `SeerrSearchItem year is null when date not numeric`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", releaseDate = "abcd-ef-gh")
        assertNull(item.year)
    }

    @Test
    fun `SeerrSearchItem posterUrl builds from posterPath`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", posterPath = "/abc.jpg")
        assertEquals("${TmdbImageUrls.POSTER_W500}/abc.jpg", item.posterUrl)
    }

    @Test
    fun `SeerrSearchItem posterUrl null when posterPath null`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie")
        assertNull(item.posterUrl)
    }

    @Test
    fun `SeerrSearchItem backdropUrl builds from backdropPath`() {
        val item = SeerrSearchItem(id = 1, mediaType = "movie", backdropPath = "/def.jpg")
        assertEquals("${TmdbImageUrls.BACKDROP_W1280}/def.jpg", item.backdropUrl)
    }
    // endregion

    // region SeerrMediaStatus
    @Test
    fun `SeerrMediaStatus fromValue returns matching enum`() {
        assertEquals(SeerrMediaStatus.UNKNOWN, SeerrMediaStatus.fromValue(1))
        assertEquals(SeerrMediaStatus.PENDING, SeerrMediaStatus.fromValue(2))
        assertEquals(SeerrMediaStatus.PROCESSING, SeerrMediaStatus.fromValue(3))
        assertEquals(SeerrMediaStatus.PARTIALLY_AVAILABLE, SeerrMediaStatus.fromValue(4))
        assertEquals(SeerrMediaStatus.AVAILABLE, SeerrMediaStatus.fromValue(5))
        assertEquals(SeerrMediaStatus.DELETED, SeerrMediaStatus.fromValue(6))
    }

    @Test
    fun `SeerrMediaStatus fromValue falls back to UNKNOWN for unknown value`() {
        assertEquals(SeerrMediaStatus.UNKNOWN, SeerrMediaStatus.fromValue(99))
        assertEquals(SeerrMediaStatus.UNKNOWN, SeerrMediaStatus.fromValue(0))
    }
    // endregion

    // region SeerrRequestStatus
    @Test
    fun `SeerrRequestStatus fromValue returns matching enum`() {
        assertEquals(SeerrRequestStatus.PENDING, SeerrRequestStatus.fromValue(1))
        assertEquals(SeerrRequestStatus.APPROVED, SeerrRequestStatus.fromValue(2))
        assertEquals(SeerrRequestStatus.DECLINED, SeerrRequestStatus.fromValue(3))
        assertEquals(SeerrRequestStatus.FAILED, SeerrRequestStatus.fromValue(4))
        assertEquals(SeerrRequestStatus.COMPLETED, SeerrRequestStatus.fromValue(5))
    }

    @Test
    fun `SeerrRequestStatus fromValue falls back to PENDING for unknown value`() {
        assertEquals(SeerrRequestStatus.PENDING, SeerrRequestStatus.fromValue(99))
        assertEquals(SeerrRequestStatus.PENDING, SeerrRequestStatus.fromValue(0))
    }
    // endregion

    // region SeerrCurrentUser permissions
    @Test
    fun `SeerrCurrentUser isAdmin true when ADMIN bit set`() {
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_ADMIN)
        assertTrue(user.isAdmin)
    }

    @Test
    fun `SeerrCurrentUser isAdmin false when ADMIN bit clear`() {
        val user = SeerrCurrentUser(permissions = 0L)
        assertFalse(user.isAdmin)
    }

    @Test
    fun `SeerrCurrentUser canManageRequests true when MANAGE_REQUESTS bit set`() {
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_MANAGE_REQUESTS)
        assertTrue(user.canManageRequests)
    }

    @Test
    fun `SeerrCurrentUser canManageRequests true when isAdmin`() {
        // admin implies all permission flags true
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_ADMIN)
        assertTrue(user.canManageRequests)
        assertTrue(user.canViewRequests)
        assertTrue(user.canRequestAdvanced)
    }

    @Test
    fun `SeerrCurrentUser canViewRequests true when REQUEST_VIEW bit set`() {
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_REQUEST_VIEW)
        assertTrue(user.canViewRequests)
        assertFalse(user.canManageRequests)
        assertFalse(user.canRequestAdvanced)
    }

    @Test
    fun `SeerrCurrentUser canRequestAdvanced true when REQUEST_ADVANCED bit set`() {
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_REQUEST_ADVANCED)
        assertTrue(user.canRequestAdvanced)
    }

    @Test
    fun `SeerrCurrentUser with no permissions has all capabilities false`() {
        val user = SeerrCurrentUser(permissions = 0L)
        assertFalse(user.isAdmin)
        assertFalse(user.canManageRequests)
        assertFalse(user.canViewRequests)
        assertFalse(user.canRequestAdvanced)
    }

    @Test
    fun `SeerrCurrentUser combined permissions are independently testable`() {
        val combined = SeerrCurrentUser.PERMISSION_MANAGE_REQUESTS or SeerrCurrentUser.PERMISSION_REQUEST_ADVANCED
        val user = SeerrCurrentUser(permissions = combined)
        assertTrue(user.canManageRequests)
        assertTrue(user.canRequestAdvanced)
        assertFalse(user.canViewRequests)
        assertFalse(user.isAdmin)
    }
    // endregion

    // region withPendingRequest
    @Test
    fun `withPendingRequest matches movie by detail id and flips status to PENDING`() {
        val item = SeerrSearchItem(id = 42, mediaType = "movie")
        val details = SeerrMovieDetails(
            id = 42,
            mediaInfo = SeerrMediaInfo(tmdbId = 42, status = SeerrMediaStatus.UNKNOWN.value),
        )

        val flipped = details.withPendingRequest(item)

        assertEquals(SeerrMediaStatus.PENDING.value, flipped.mediaInfo?.status)
        // The existing mediaInfo is preserved (same tmdb id), not replaced.
        assertEquals(42, flipped.mediaInfo?.tmdbId)
    }

    @Test
    fun `withPendingRequest synthesizes absent movie mediaInfo with the item tmdbId`() {
        // Overseerr omits mediaInfo for never-requested media — the flip must
        // still produce a PENDING mediaInfo so the action button updates.
        val item = SeerrSearchItem(id = 42, mediaType = "movie")
        val details = SeerrMovieDetails(id = 42)

        val flipped = details.withPendingRequest(item)

        assertEquals(SeerrMediaStatus.PENDING.value, flipped.mediaInfo?.status)
        assertEquals(42, flipped.mediaInfo?.tmdbId)
    }

    @Test
    fun `withPendingRequest matches tv by detail id and flips status to PENDING`() {
        val item = SeerrSearchItem(id = 7, mediaType = "tv")
        val details = SeerrTvDetails(id = 7)

        val flipped = details.withPendingRequest(item)

        assertEquals(SeerrMediaStatus.PENDING.value, flipped.mediaInfo?.status)
        assertEquals(7, flipped.mediaInfo?.tmdbId)
    }

    @Test
    fun `withPendingRequest leaves non-matching movie untouched`() {
        val item = SeerrSearchItem(id = 999, mediaType = "movie")
        val details = SeerrMovieDetails(
            id = 42,
            mediaInfo = SeerrMediaInfo(tmdbId = 42, status = SeerrMediaStatus.AVAILABLE.value),
        )

        assertEquals(details, details.withPendingRequest(item))
    }

    @Test
    fun `withPendingRequest leaves non-matching tv untouched`() {
        val item = SeerrSearchItem(id = 999, mediaType = "tv")
        val details = SeerrTvDetails(id = 7)

        assertEquals(details, details.withPendingRequest(item))
    }
    // endregion

    // region profile/poster/still URL getters on details
    @Test
    fun `SeerrMovieDetails posterUrl and backdropUrl build from paths`() {
        val details = SeerrMovieDetails(posterPath = "/m.jpg", backdropPath = "/b.jpg")
        assertEquals("${TmdbImageUrls.POSTER_W500}/m.jpg", details.posterUrl)
        assertEquals("${TmdbImageUrls.BACKDROP_W1280}/b.jpg", details.backdropUrl)
    }

    @Test
    fun `SeerrMovieDetails posterUrl null when path null`() {
        val details = SeerrMovieDetails()
        assertNull(details.posterUrl)
        assertNull(details.backdropUrl)
    }

    @Test
    fun `SeerrTvDetails posterUrl and backdropUrl build from paths`() {
        val details = SeerrTvDetails(posterPath = "/tv.jpg", backdropPath = "/tvb.jpg")
        assertEquals("${TmdbImageUrls.POSTER_W500}/tv.jpg", details.posterUrl)
        assertEquals("${TmdbImageUrls.BACKDROP_W1280}/tvb.jpg", details.backdropUrl)
    }

    @Test
    fun `SeerrCast profileUrl builds from profilePath`() {
        val cast = SeerrCast(name = "Actor", profilePath = "/cast.jpg")
        assertEquals("${TmdbImageUrls.PROFILE_H632}/cast.jpg", cast.profileUrl)
    }

    @Test
    fun `SeerrAggregateCast profileUrl builds from profilePath`() {
        val cast = SeerrAggregateCast(name = "Actor", profilePath = "/agg.jpg")
        assertEquals("${TmdbImageUrls.PROFILE_H632}/agg.jpg", cast.profileUrl)
    }

    @Test
    fun `SeerrSeason posterUrl builds from posterPath`() {
        val season = SeerrSeason(name = "S1", posterPath = "/s1.jpg", seasonNumber = 1)
        assertEquals("${TmdbImageUrls.POSTER_W500}/s1.jpg", season.posterUrl)
    }

    @Test
    fun `SeerrEpisode stillUrl builds from stillPath`() {
        val episode = SeerrEpisode(name = "Pilot", stillPath = "/still.jpg")
        assertEquals("${TmdbImageUrls.POSTER_W500}/still.jpg", episode.stillUrl)
    }
    // endregion

    // region getFullUrl edge cases
    @Test
    fun `SeerrRadarrSettings getFullUrl prefers externalUrl and trims trailing slash`() {
        val settings = SeerrRadarrSettings(
            id = 1, name = "Radarr", hostname = "h", port = 1, apiKey = "k",
            externalUrl = "http://ext.example.com/"
        )
        assertEquals("http://ext.example.com", settings.getFullUrl())
    }

    @Test
    fun `SeerrRadarrSettings getFullUrl falls through when externalUrl blank`() {
        val settings = SeerrRadarrSettings(
            id = 1, name = "Radarr", hostname = "host", port = 7878, apiKey = "k",
            useSsl = false, externalUrl = "   "
        )
        assertEquals("http://host:7878", settings.getFullUrl())
    }

    @Test
    fun `SeerrRadarrSettings getFullUrl strips surrounding slashes from baseUrl`() {
        val settings = SeerrRadarrSettings(
            id = 1, name = "Radarr", hostname = "host", port = 7878, apiKey = "k",
            useSsl = false, baseUrl = "/some/base/"
        )
        assertEquals("http://host:7878/some/base", settings.getFullUrl())
    }

    @Test
    fun `SeerrRadarrSettings getFullUrl with empty baseUrl has no path segment`() {
        val settings = SeerrRadarrSettings(
            id = 1, name = "Radarr", hostname = "host", port = 7878, apiKey = "k",
            useSsl = false, baseUrl = "///"
        )
        assertEquals("http://host:7878", settings.getFullUrl())
    }

    @Test
    fun `SeerrSonarrSettings getFullUrl with SSL and baseUrl`() {
        val settings = SeerrSonarrSettings(
            id = 1, name = "Sonarr", hostname = "sonarr", port = 8989, apiKey = "k",
            useSsl = true, baseUrl = "sonarr"
        )
        assertEquals("https://sonarr:8989/sonarr", settings.getFullUrl())
    }
    // endregion
}
