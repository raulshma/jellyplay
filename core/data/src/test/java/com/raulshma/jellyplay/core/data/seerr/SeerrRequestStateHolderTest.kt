package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrKeyword
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeerrRequestStateHolderTest {

    private val delegate: SeerrRequestDelegate = mockk(relaxed = true)

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun holder(scope: CoroutineScope): SeerrRequestStateHolder =
        SeerrRequestStateHolder(scope, delegate)

    // region requestMedia
    @Test
    fun `requestMedia sets loading then success on success`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))
        val h = holder(this)

        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"))
        advanceUntilIdle()

        val result = h.requestResult.value
        assertNotNull(result)
        assertEquals(true, result!!.success)
        assertFalse(result.isLoading)
        assertNull(result.error)
    }

    @Test
    fun `requestMedia sets error on failure`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("nope"))
        val h = holder(this)

        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"))
        advanceUntilIdle()

        val result = h.requestResult.value!!
        assertNull(result.success)
        assertEquals("nope", result.error)
    }

    @Test
    fun `requestMedia uses default error message when exception message null`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException())
        val h = holder(this)

        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"))
        advanceUntilIdle()

        assertEquals("Request failed", h.requestResult.value!!.error)
    }

    @Test
    fun `requestMedia forwards item mediaType and id to delegate`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))
        val h = holder(this)

        h.requestMedia(
            SeerrSearchItem(id = 42, mediaType = "tv"),
            seasons = listOf(1),
            serverId = 9,
            profileId = 3,
            rootFolder = "/tv",
            tags = listOf(7),
        )
        advanceUntilIdle()

        coVerify {
            delegate.requestMedia(
                mediaType = "tv", tmdbId = 42, seasons = listOf(1),
                serverId = 9, profileId = 3, rootFolder = "/tv", tags = listOf(7),
            )
        }
    }
    // endregion

    // region clearRequestResult / setRequestResult
    @Test
    fun `clearRequestResult nulls out requestResult`() = runTest {
        val h = holder(this)
        h.setRequestResult(SeerrRequestResult(success = true))
        assertEquals(true, h.requestResult.value?.success)

        h.clearRequestResult()
        assertNull(h.requestResult.value)
    }

    @Test
    fun `setRequestResult replaces current value`() = runTest {
        val h = holder(this)
        h.setRequestResult(SeerrRequestResult(success = false, error = "e"))
        h.setRequestResult(SeerrRequestResult(success = true))

        assertEquals(true, h.requestResult.value?.success)
        assertNull(h.requestResult.value?.error)
    }
    // endregion

    // region loadServiceDetails
    @Test
    fun `loadServiceDetails populates radarr servers and clears loading flag`() = runTest {
        val radarr = listOf(SeerrRadarrServiceDetail(id = 1, name = "R"))
        coEvery { delegate.fetchServiceDetails("movie") } returns
            SeerrServiceDetailsResult(radarrServers = radarr)
        val h = holder(this)

        h.loadServiceDetails("movie")
        advanceUntilIdle()

        assertEquals(radarr, h.radarrServers.value)
        assertTrue(h.sonarrServers.value.isEmpty())
        assertFalse(h.isLoadingServices.value)
    }

    @Test
    fun `loadServiceDetails populates sonarr servers and clears loading flag`() = runTest {
        val sonarr = listOf(SeerrSonarrServiceDetail(id = 2, name = "S"))
        coEvery { delegate.fetchServiceDetails("tv") } returns
            SeerrServiceDetailsResult(sonarrServers = sonarr)
        val h = holder(this)

        h.loadServiceDetails("tv")
        advanceUntilIdle()

        assertEquals(sonarr, h.sonarrServers.value)
        assertTrue(h.radarrServers.value.isEmpty())
        assertFalse(h.isLoadingServices.value)
    }

    @Test
    fun `loadServiceDetails clears loading flag after success`() = runTest {
        val radarr = listOf(SeerrRadarrServiceDetail(id = 1, name = "R"))
        coEvery { delegate.fetchServiceDetails("movie") } returns
            SeerrServiceDetailsResult(radarrServers = radarr)
        val h = holder(this)

        h.loadServiceDetails("movie")
        advanceUntilIdle()

        assertEquals(radarr, h.radarrServers.value)
        assertFalse(h.isLoadingServices.value)
    }
    // endregion

    // region loadTvSeasons
    @Test
    fun `loadTvSeasons populates tvSeasons`() = runTest {
        coEvery { delegate.fetchTvDetails(5) } returns SeerrTvDetails(
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "S1")),
        )
        val h = holder(this)

        h.loadTvSeasons(5)
        advanceUntilIdle()

        assertEquals(listOf(SeerrSeason(seasonNumber = 1, name = "S1")), h.tvSeasons.value)
        assertFalse(h.tvIsAnime.value)
    }

    @Test
    fun `loadTvSeasons flags anime via tmdb keyword`() = runTest {
        coEvery { delegate.fetchTvDetails(5) } returns SeerrTvDetails(
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "S1")),
            keywords = listOf(SeerrKeyword(id = 210024, name = "anime")),
        )
        val h = holder(this)

        h.loadTvSeasons(5)
        advanceUntilIdle()

        assertTrue(h.tvIsAnime.value)
    }

    @Test
    fun `loadTvSeasons resets to empty before fetching`() = runTest {
        val h = holder(this)
        coEvery { delegate.fetchTvDetails(5) } returns SeerrTvDetails(
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "S1")),
        )
        h.loadTvSeasons(5)
        advanceUntilIdle()
        assertTrue(h.tvSeasons.value.isNotEmpty())

        coEvery { delegate.fetchTvDetails(6) } returns null
        h.loadTvSeasons(6)
        advanceUntilIdle()

        assertTrue(h.tvSeasons.value.isEmpty())
    }
    // endregion

    // region prefetchDetails
    @Test
    fun `prefetchDetails invokes onDone callback after completion`() = runTest {
        coEvery { delegate.prefetchDetails(any(), any()) } returns Unit
        val h = holder(this)

        var called = false
        h.prefetchDetails(1, "movie") { called = true }
        advanceUntilIdle()

        assertTrue(called)
        coVerify { delegate.prefetchDetails(1, "movie") }
    }
    // endregion
}
