package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeerrRepositoryImplExtendedTest {

    private val seerrApiClient: SeerrApiClient = mockk(relaxed = true)
    private val tmdbApiClient: TmdbApiClient = mockk(relaxed = true)
    private val seerrPreferencesStore: SeerrPreferencesStore = mockk(relaxed = true)
    private val secureCredentialsStore: SeerrSecureCredentialsStore = mockk(relaxed = true)

    private val validPrefs = SeerrPreferences(
        enabled = true,
        serverUrl = "https://seerr.example.com",
        authMethod = SeerrAuthMethod.API_KEY,
    )

    // Real HomeSession over a permanently-null session flow + the registry
    // that owns identity reactions; this suite never switches identity, so
    // CacheIdentity.UNKNOWN is the detail cache's key surface.
    private val sessionApiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient = mockk {
        every { session } returns MutableStateFlow(null)
    }
    private val homeSession = com.raulshma.jellyplay.core.data.session.HomeSession(
        sessionApiClient,
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        ),
    )
    private val sessionCacheRegistry = com.raulshma.jellyplay.core.data.session.SessionCacheRegistry(
        homeSession,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
    )
    private val repoScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    private lateinit var repository: SeerrRepositoryImpl

    @Before
    fun setup() {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(validPrefs)
        every { seerrPreferencesStore.isConnected } returns MutableStateFlow(true)
        every { secureCredentialsStore.getApiKey() } returns "test-api-key"
        every { secureCredentialsStore.getSessionCookie() } returns ""
        coEvery { seerrApiClient.getRequestCount(any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { seerrApiClient.getCurrentUser(any(), any()) } returns
            Result.success(SeerrCurrentUser(permissions = 2L))
        repository = SeerrRepositoryImpl(seerrApiClient, tmdbApiClient, seerrPreferencesStore, secureCredentialsStore, homeSession, sessionCacheRegistry, repoScope)
    }

    private fun rebuildWith(prefs: SeerrPreferences, apiKey: String = "test-api-key", cookie: String = "") {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(prefs)
        every { secureCredentialsStore.getApiKey() } returns apiKey
        every { secureCredentialsStore.getSessionCookie() } returns cookie
        repository = SeerrRepositoryImpl(seerrApiClient, tmdbApiClient, seerrPreferencesStore, secureCredentialsStore, homeSession, sessionCacheRegistry, repoScope)
    }

    // region credentials resolution by authMethod
    @Test
    fun `API_KEY method uses api key credential`() = runTest {
        coEvery { seerrApiClient.getMovieDetails(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getMovieDetails(1)

        coVerify { seerrApiClient.getMovieDetails(any(), SeerrCredentials.ApiKey("test-api-key"), 1) }
    }

    @Test
    fun `JELLYFIN method uses session cookie credential`() = runTest {
        rebuildWith(validPrefs.copy(authMethod = SeerrAuthMethod.JELLYFIN), apiKey = "", cookie = "session=abc")
        coEvery { seerrApiClient.getMovieDetails(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getMovieDetails(1)

        coVerify { seerrApiClient.getMovieDetails(any(), SeerrCredentials.SessionCookie("session=abc"), 1) }
    }

    @Test
    fun `LOCAL method uses session cookie credential`() = runTest {
        rebuildWith(validPrefs.copy(authMethod = SeerrAuthMethod.LOCAL), apiKey = "", cookie = "session=xyz")
        coEvery { seerrApiClient.getMovieDetails(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getMovieDetails(1)

        coVerify { seerrApiClient.getMovieDetails(any(), SeerrCredentials.SessionCookie("session=xyz"), 1) }
    }

    @Test
    fun `API_KEY with blank api key fails`() = runTest {
        rebuildWith(validPrefs.copy(authMethod = SeerrAuthMethod.API_KEY), apiKey = "")

        val result = repository.getMovieDetails(1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not configured") == true)
    }

    @Test
    fun `JELLYFIN with blank cookie fails`() = runTest {
        rebuildWith(validPrefs.copy(authMethod = SeerrAuthMethod.JELLYFIN), apiKey = "", cookie = "")

        val result = repository.getMovieDetails(1)

        assertTrue(result.isFailure)
    }

    @Test
    fun `blank server url fails`() = runTest {
        rebuildWith(validPrefs.copy(serverUrl = ""))

        val result = repository.getMovieDetails(1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not configured") == true)
    }
    // endregion

    // region getRatings routing
    @Test
    fun `getRatings movie routes to getMovieRatingsCombined`() = runTest {
        coEvery { seerrApiClient.getMovieRatingsCombined(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getRatings(1, "movie")

        coVerify { seerrApiClient.getMovieRatingsCombined(any(), any(), 1) }
        coVerify(exactly = 0) { seerrApiClient.getTvRatings(any(), any(), any()) }
    }

    @Test
    fun `getRatings tv routes to getTvRatings`() = runTest {
        coEvery { seerrApiClient.getTvRatings(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getRatings(1, "tv")

        coVerify { seerrApiClient.getTvRatings(any(), any(), 1) }
        coVerify(exactly = 0) { seerrApiClient.getMovieRatingsCombined(any(), any(), any()) }
    }

    @Test
    fun `getRatings caches by tmdbId and mediaType`() = runTest {
        coEvery { seerrApiClient.getMovieRatingsCombined(any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))

        repository.getRatings(1, "movie")
        repository.getRatings(1, "movie")

        coVerify(exactly = 1) { seerrApiClient.getMovieRatingsCombined(any(), any(), 1) }
    }
    // endregion

    // region getRecommendations / getSimilar routing + mediaType backfill
    @Test
    fun `getRecommendations movie routes to movie endpoint and backfills mediaType`() = runTest {
        val response = SeerrSearchResponse(
            results = listOf(
                SeerrSearchItem(id = 10, mediaType = ""),
                SeerrSearchItem(id = 11, mediaType = "movie"),
            )
        )
        coEvery { seerrApiClient.getMovieRecommendations(any(), any(), 1) } returns Result.success(response)

        val result = repository.getRecommendations(1, MediaType.MOVIE)

        assertTrue(result.isSuccess)
        assertEquals("movie", result.getOrThrow().results[0].mediaType)
        assertEquals("movie", result.getOrThrow().results[1].mediaType)
    }

    @Test
    fun `getRecommendations tv routes to tv endpoint and backfills mediaType`() = runTest {
        val response = SeerrSearchResponse(
            results = listOf(SeerrSearchItem(id = 20, mediaType = ""))
        )
        coEvery { seerrApiClient.getTvRecommendations(any(), any(), 2) } returns Result.success(response)

        val result = repository.getRecommendations(2, MediaType.SERIES)

        assertTrue(result.isSuccess)
        assertEquals("tv", result.getOrThrow().results[0].mediaType)
    }

    @Test
    fun `getRecommendations unsupported media type fails`() = runTest {
        val result = repository.getRecommendations(3, MediaType.EPISODE)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported") == true)
    }

    @Test
    fun `getSimilar movie routes to movie endpoint`() = runTest {
        coEvery { seerrApiClient.getMovieSimilar(any(), any(), 1) } returns
            Result.success(SeerrSearchResponse())

        repository.getSimilar(1, MediaType.MOVIE)

        coVerify { seerrApiClient.getMovieSimilar(any(), any(), 1) }
    }

    @Test
    fun `getSimilar tv routes to tv endpoint`() = runTest {
        coEvery { seerrApiClient.getTvSimilar(any(), any(), 2) } returns
            Result.success(SeerrSearchResponse())

        repository.getSimilar(2, MediaType.SERIES)

        coVerify { seerrApiClient.getTvSimilar(any(), any(), 2) }
    }

    @Test
    fun `getSimilar unsupported media type fails`() = runTest {
        val result = repository.getSimilar(3, MediaType.EPISODE)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported") == true)
    }
    // endregion

    // region discover mediaType backfill
    @Test
    fun `getDiscoverMovies backfills movie mediaType on blank items`() = runTest {
        val response = SeerrSearchResponse(results = listOf(SeerrSearchItem(id = 1, mediaType = "")))
        coEvery { seerrApiClient.getDiscoverMovies(any(), any(), any(), any()) } returns Result.success(response)

        val result = repository.getDiscoverMovies(1, null)

        assertEquals("movie", result.getOrThrow().results[0].mediaType)
    }

    @Test
    fun `getDiscoverTv backfills tv mediaType on blank items`() = runTest {
        val response = SeerrSearchResponse(results = listOf(SeerrSearchItem(id = 1, mediaType = "")))
        coEvery { seerrApiClient.getDiscoverTv(any(), any(), any(), any()) } returns Result.success(response)

        val result = repository.getDiscoverTv(1, null)

        assertEquals("tv", result.getOrThrow().results[0].mediaType)
    }

    @Test
    fun `getDiscoverMovies preserves existing non-blank mediaType`() = runTest {
        val response = SeerrSearchResponse(
            results = listOf(SeerrSearchItem(id = 1, mediaType = "tv")) // unusual but tests preservation
        )
        coEvery { seerrApiClient.getDiscoverMovies(any(), any(), any(), any()) } returns Result.success(response)

        val result = repository.getDiscoverMovies(1, null)

        assertEquals("tv", result.getOrThrow().results[0].mediaType)
    }
    // endregion

    // region request lifecycle methods delegation
    @Test
    fun `approveRequest delegates with credentials`() = runTest {
        coEvery { seerrApiClient.approveRequest(any(), any(), 5) } returns
            Result.success(mockk(relaxed = true))

        repository.approveRequest(5)

        coVerify { seerrApiClient.approveRequest(any(), SeerrCredentials.ApiKey("test-api-key"), 5) }
    }

    @Test
    fun `declineRequest delegates with credentials`() = runTest {
        coEvery { seerrApiClient.declineRequest(any(), any(), 6) } returns
            Result.success(mockk(relaxed = true))

        repository.declineRequest(6)

        coVerify { seerrApiClient.declineRequest(any(), any(), 6) }
    }

    @Test
    fun `retryRequest delegates with credentials`() = runTest {
        coEvery { seerrApiClient.retryRequest(any(), any(), 7) } returns
            Result.success(mockk(relaxed = true))

        repository.retryRequest(7)

        coVerify { seerrApiClient.retryRequest(any(), any(), 7) }
    }

    @Test
    fun `deleteRequest delegates with credentials`() = runTest {
        coEvery { seerrApiClient.deleteRequest(any(), any(), 8) } returns Result.success(Unit)

        repository.deleteRequest(8)

        coVerify { seerrApiClient.deleteRequest(any(), any(), 8) }
    }

    @Test
    fun `deleteMedia delegates with is4k flag`() = runTest {
        coEvery { seerrApiClient.deleteMedia(any(), any(), 9, true) } returns Result.success(Unit)

        repository.deleteMedia(9, is4k = true)

        coVerify { seerrApiClient.deleteMedia(any(), any(), 9, true) }
    }

    @Test
    fun `requestMedia delegates all params`() = runTest {
        coEvery { seerrApiClient.requestMedia(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk<SeerrMediaRequest>(relaxed = true))

        repository.requestMedia(
            tmdbId = 100, mediaType = "tv", seasons = listOf(1),
            serverId = 2, profileId = 3, rootFolder = "/tv", tags = listOf(4),
        )

        coVerify {
            seerrApiClient.requestMedia(
                any(), any(), "tv", 100, seasons = listOf(1),
                serverId = 2, profileId = 3, rootFolder = "/tv", tags = listOf(4),
            )
        }
    }

    @Test
    fun `editRequest delegates all params`() = runTest {
        coEvery {
            seerrApiClient.editRequest(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(mockk(relaxed = true))

        repository.editRequest(
            id = 1, mediaType = "movie", mediaId = 50,
            serverId = 1, profileId = 2, rootFolder = "/m", tags = listOf(3), seasons = null,
        )

        coVerify {
            seerrApiClient.editRequest(any(), any(), 1, "movie", 50, 1, 2, "/m", listOf(3), null)
        }
    }
    // endregion

    // region getCurrentUser / isAdmin
    @Test
    fun `getCurrentUser exposes currentUser flow and writes fetched user`() = runTest {
        val user = SeerrCurrentUser(id = 5, permissions = SeerrCurrentUser.PERMISSION_ADMIN)
        coEvery { seerrApiClient.getCurrentUser(any(), any()) } returns Result.success(user)

        repository.getCurrentUser()

        assertEquals(5, repository.currentUser.value?.id)
    }

    @Test
    fun `isAdmin derives from currentUser canManageRequests`() = runTest {
        val user = SeerrCurrentUser(permissions = SeerrCurrentUser.PERMISSION_MANAGE_REQUESTS)
        coEvery { seerrApiClient.getCurrentUser(any(), any()) } returns Result.success(user)

        repository.getCurrentUser()

        assertEquals(true, repository.isAdmin().first())
    }
    // endregion

    // region settings + service detail delegation
    @Test
    fun `getRadarrSettings delegates`() = runTest {
        coEvery { seerrApiClient.getRadarrSettings(any(), any()) } returns Result.success(emptyList())

        repository.getRadarrSettings()

        coVerify { seerrApiClient.getRadarrSettings(any(), SeerrCredentials.ApiKey("test-api-key")) }
    }

    @Test
    fun `getSonarrSettings delegates`() = runTest {
        coEvery { seerrApiClient.getSonarrSettings(any(), any()) } returns Result.success(emptyList())

        repository.getSonarrSettings()

        coVerify { seerrApiClient.getSonarrSettings(any(), any()) }
    }

    @Test
    fun `getServiceRadarrDetail delegates`() = runTest {
        coEvery { seerrApiClient.getServiceRadarrDetail(any(), any(), 1) } returns
            Result.success(mockk(relaxed = true))

        repository.getServiceRadarrDetail(1)

        coVerify { seerrApiClient.getServiceRadarrDetail(any(), any(), 1) }
    }

    @Test
    fun `getServiceSonarrDetail delegates`() = runTest {
        coEvery { seerrApiClient.getServiceSonarrDetail(any(), any(), 2) } returns
            Result.success(mockk(relaxed = true))

        repository.getServiceSonarrDetail(2)

        coVerify { seerrApiClient.getServiceSonarrDetail(any(), any(), 2) }
    }

    @Test
    fun `getTvDetails caches result separately from getMovieDetails`() = runTest {
        coEvery { seerrApiClient.getTvDetails(any(), any(), 789) } returns
            Result.success(mockk<SeerrTvDetails>(relaxed = true))

        repository.getTvDetails(789)
        repository.getTvDetails(789)

        coVerify(exactly = 1) { seerrApiClient.getTvDetails(any(), any(), 789) }
    }

    @Test
    fun `getTvSeasonDetails caches result`() = runTest {
        coEvery { seerrApiClient.getTvSeasonDetails(any(), any(), 100, 1) } returns
            Result.success(mockk(relaxed = true))

        repository.getTvSeasonDetails(100, 1)
        repository.getTvSeasonDetails(100, 1)

        coVerify(exactly = 1) { seerrApiClient.getTvSeasonDetails(any(), any(), 100, 1) }
    }
    // endregion
}
