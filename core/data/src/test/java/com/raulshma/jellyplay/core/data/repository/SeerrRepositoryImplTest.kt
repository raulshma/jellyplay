package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeerrRepositoryImplTest {

    private val seerrApiClient: SeerrApiClient = mockk(relaxed = true)
    private val seerrPreferencesStore: SeerrPreferencesStore = mockk(relaxed = true)

    private lateinit var repository: SeerrRepositoryImpl

    private val validPrefs = SeerrPreferences(
        enabled = true,
        serverUrl = "https://seerr.example.com",
        apiKey = "test-api-key",
    )

    @Before
    fun setup() {
        every { seerrPreferencesStore.preferences } returns flowOf(validPrefs)
        repository = SeerrRepositoryImpl(seerrApiClient, seerrPreferencesStore)
    }

    @Test
    fun `testConnection fails when credentials not configured`() = runTest {
        every { seerrPreferencesStore.preferences } returns flowOf(
            SeerrPreferences(enabled = true, serverUrl = "", apiKey = "")
        )
        repository = SeerrRepositoryImpl(seerrApiClient, seerrPreferencesStore)

        val result = repository.testConnection()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("required") == true)
    }

    @Test
    fun `testConnection delegates to apiClient`() = runTest {
        val response = SeerrStatusResponse(version = "1.0.0")
        coEvery { seerrApiClient.testConnection("https://seerr.example.com", "test-api-key") } returns
            Result.success(response)

        val result = repository.testConnection()

        assertTrue(result.isSuccess)
        assertEquals("1.0.0", result.getOrNull()!!.version)
    }

    @Test
    fun `search fails when not configured`() = runTest {
        every { seerrPreferencesStore.preferences } returns flowOf(
            SeerrPreferences(enabled = true, serverUrl = "", apiKey = "")
        )
        repository = SeerrRepositoryImpl(seerrApiClient, seerrPreferencesStore)

        val result = repository.search("test query")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not configured") == true)
    }

    @Test
    fun `isConnected delegates to preferences store`() = runTest {
        every { seerrPreferencesStore.isConnected } returns flowOf(true)

        val result = repository.isConnected().firstOrNull()
        assertEquals(true, result)
    }

    @Test
    fun `isEnabled reflects preferences`() = runTest {
        every { seerrPreferencesStore.preferences } returns flowOf(validPrefs.copy(enabled = true))

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
}
