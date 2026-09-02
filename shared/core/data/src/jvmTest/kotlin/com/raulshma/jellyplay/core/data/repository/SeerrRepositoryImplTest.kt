package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.api.TmdbApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class SeerrRepositoryImplTest {

    private val seerrApiClient: SeerrApiClient = mockk(relaxed = true)
    private val tmdbApiClient: TmdbApiClient = mockk(relaxed = true)
    private val seerrPreferencesStore: SeerrPreferencesStore = mockk(relaxed = true)
    private val secureCredentialsStore: SeerrSecureCredentialsStore = mockk(relaxed = true)

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

    private val validPrefs = SeerrPreferences(
        enabled = true,
        serverUrl = "https://seerr.example.com",
        authMethod = SeerrAuthMethod.API_KEY,
    )

    @BeforeTest
    fun setup() {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(validPrefs)
        every { secureCredentialsStore.getApiKey() } returns "test-api-key"
        every { secureCredentialsStore.getSessionCookie() } returns ""
        coEvery { seerrApiClient.getRequestCount(any(), any()) } returns Result.success(SeerrRequestCount())
        coEvery { seerrApiClient.getCurrentUser(any(), any()) } returns Result.success(SeerrCurrentUser(permissions = 2L))
        repository = SeerrRepositoryImpl(seerrApiClient, tmdbApiClient, seerrPreferencesStore, secureCredentialsStore, homeSession, sessionCacheRegistry, repoScope)
    }

    @Test
    fun `testConnection fails when credentials not configured`() = runTest {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(
            SeerrPreferences(enabled = true, serverUrl = "")
        )
        every { secureCredentialsStore.getApiKey() } returns ""
        repository = SeerrRepositoryImpl(seerrApiClient, tmdbApiClient, seerrPreferencesStore, secureCredentialsStore, homeSession, sessionCacheRegistry, repoScope)

        val result = repository.testConnection()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("required") == true)
    }

    @Test
    fun `testConnection delegates to apiClient`() = runTest {
        val response = SeerrStatusResponse(version = "1.0.0")
        coEvery {
            seerrApiClient.testConnection("https://seerr.example.com", SeerrCredentials.ApiKey("test-api-key"))
        } returns Result.success(response)

        val result = repository.testConnection()

        assertTrue(result.isSuccess)
        assertEquals("1.0.0", result.getOrNull()!!.version)
    }

    @Test
    fun `search fails when not configured`() = runTest {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(
            SeerrPreferences(enabled = true, serverUrl = "")
        )
        every { secureCredentialsStore.getApiKey() } returns ""
        repository = SeerrRepositoryImpl(seerrApiClient, tmdbApiClient, seerrPreferencesStore, secureCredentialsStore, homeSession, sessionCacheRegistry, repoScope)

        val result = repository.search("test query")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not configured") == true)
    }

    @Test
    fun `isConnected delegates to preferences store`() = runTest {
        every { seerrPreferencesStore.isConnected } returns MutableStateFlow(true)

        val result = repository.isConnected().firstOrNull()
        assertEquals(true, result)
    }

    @Test
    fun `isEnabled reflects preferences`() = runTest {
        every { seerrPreferencesStore.preferences } returns MutableStateFlow(validPrefs.copy(enabled = true))

        val result = repository.isEnabled().firstOrNull()
        assertEquals(true, result)
    }

    @Test
    fun `getMovieDetails caches result`() = runTest {
        val details = mockk<com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails>(relaxed = true)
        coEvery { seerrApiClient.getMovieDetails(any(), any(), 123) } returns Result.success(details)

        val first = repository.getMovieDetails(123)
        val second = repository.getMovieDetails(123)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        coVerify(exactly = 1) { seerrApiClient.getMovieDetails(any(), any(), 123) }
    }

    @Test
    fun `getTvDetails caches result`() = runTest {
        val details = mockk<com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails>(relaxed = true)
        coEvery { seerrApiClient.getTvDetails(any(), any(), 456) } returns Result.success(details)

        val first = repository.getTvDetails(456)
        val second = repository.getTvDetails(456)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        coVerify(exactly = 1) { seerrApiClient.getTvDetails(any(), any(), 456) }
    }

    @Test
    fun `getTmdbReviews caches result`() = runTest {
        val review = TmdbReview(id = "r1", author = "Reviewer")
        coEvery { tmdbApiClient.getReviews(123, MediaType.MOVIE) } returns Result.success(listOf(review))

        val first = repository.getTmdbReviews(123, MediaType.MOVIE)
        val second = repository.getTmdbReviews(123, MediaType.MOVIE)

        assertTrue(first.isSuccess)
        assertEquals(listOf(review), second.getOrNull())
        coVerify(exactly = 1) { tmdbApiClient.getReviews(123, MediaType.MOVIE) }
    }
}
