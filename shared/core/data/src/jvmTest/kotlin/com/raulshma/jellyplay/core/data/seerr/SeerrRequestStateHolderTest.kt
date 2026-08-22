package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class SeerrRequestStateHolderTest {

    private val delegate: SeerrRequestDelegate = mockk(relaxed = true)

    private fun holder(scope: CoroutineScope): SeerrRequestStateHolder =
        SeerrRequestStateHolder(scope, delegate)

    /** The holder's only state interface — read the current combined snapshot. */
    private suspend fun SeerrRequestStateHolder.snap(): SeerrRequestSnapshot =
        snapshot.first()

    // region requestMedia
    @Test
    fun `requestMedia sets loading then success on success`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))
        val h = holder(this)

        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"))
        advanceUntilIdle()

        val result = h.snap().requestResult
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

        val result = h.snap().requestResult!!
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

        assertEquals("Request failed", h.snap().requestResult!!.error)
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

    @Test
    fun `requestMedia invokes onSuccess with the resolved request on success`() = runTest {
        val resolved = SeerrMediaRequest(id = 7)
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(resolved)
        val h = holder(this)

        var received: SeerrMediaRequest? = null
        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie")) { request -> received = request }
        advanceUntilIdle()

        // The hook receives the delegate-resolved request verbatim, and the
        // terminal success result accompanies it (the holder sets the result
        // before invoking the hook).
        assertSame(resolved, received)
        assertEquals(true, h.snap().requestResult?.success)
    }

    @Test
    fun `requestMedia does not invoke onSuccess on failure`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("nope"))
        val h = holder(this)

        var invoked = false
        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"), onSuccess = { invoked = true })
        advanceUntilIdle()

        assertFalse(invoked)
        assertEquals("nope", h.snap().requestResult?.error)
    }
    // endregion

    // region snapshotIn
    @Test
    fun `snapshotIn seeds with the empty snapshot then reflects holder updates`() = runTest {
        val h = holder(this)
        val flow = h.snapshotIn(backgroundScope)
        assertEquals(SeerrRequestSnapshot(), flow.value)

        // Warm the WhileSubscribed sharing so upstream ticks propagate;
        // UNDISPATCHED makes the subscription itself synchronous.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { /* keep subscribed */ }
        }
        coEvery { delegate.fetchServiceDetails("movie") } returns
            SeerrServiceDetailsResult(radarrServers = listOf(SeerrRadarrServiceDetail(id = 1, name = "R")))

        h.loadServiceDetails("movie")

        // Await propagation through the stateIn hop instead of asserting
        // `.value` straight after `advanceUntilIdle`: the sharing's emission
        // can sit in the task queue and only run while the test body is
        // suspended. `first {}` suspends (driving the scheduler) until the
        // update lands, in both regimes.
        val final = flow.first { it.radarrServers.isNotEmpty() }
        advanceUntilIdle()

        assertEquals(1, final.radarrServers.single().id)
        assertFalse(final.isLoadingServices)
        assertFalse(flow.value.isLoadingServices)
    }
    // endregion

    // region clearRequestResult
    @Test
    fun `clearRequestResult nulls out requestResult`() = runTest {
        coEvery { delegate.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk(relaxed = true))
        val h = holder(this)
        h.requestMedia(SeerrSearchItem(id = 1, mediaType = "movie"))
        advanceUntilIdle()
        assertEquals(true, h.snap().requestResult?.success)

        h.clearRequestResult()
        assertNull(h.snap().requestResult)
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

        val snap = h.snap()
        assertEquals(radarr, snap.radarrServers)
        assertTrue(snap.sonarrServers.isEmpty())
        assertFalse(snap.isLoadingServices)
    }

    @Test
    fun `loadServiceDetails populates sonarr servers and clears loading flag`() = runTest {
        val sonarr = listOf(SeerrSonarrServiceDetail(id = 2, name = "S"))
        coEvery { delegate.fetchServiceDetails("tv") } returns
            SeerrServiceDetailsResult(sonarrServers = sonarr)
        val h = holder(this)

        h.loadServiceDetails("tv")
        advanceUntilIdle()

        val snap = h.snap()
        assertEquals(sonarr, snap.sonarrServers)
        assertTrue(snap.radarrServers.isEmpty())
        assertFalse(snap.isLoadingServices)
    }

    @Test
    fun `loadServiceDetails clears loading flag after success`() = runTest {
        val radarr = listOf(SeerrRadarrServiceDetail(id = 1, name = "R"))
        coEvery { delegate.fetchServiceDetails("movie") } returns
            SeerrServiceDetailsResult(radarrServers = radarr)
        val h = holder(this)

        h.loadServiceDetails("movie")
        advanceUntilIdle()

        assertEquals(radarr, h.snap().radarrServers)
        assertFalse(h.snap().isLoadingServices)
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

        val snap = h.snap()
        assertEquals(listOf(SeerrSeason(seasonNumber = 1, name = "S1")), snap.tvSeasons)
        assertFalse(snap.tvIsAnime)
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

        assertTrue(h.snap().tvIsAnime)
    }

    @Test
    fun `loadTvSeasons resets to empty before fetching`() = runTest {
        val h = holder(this)
        coEvery { delegate.fetchTvDetails(5) } returns SeerrTvDetails(
            seasons = listOf(SeerrSeason(seasonNumber = 1, name = "S1")),
        )
        h.loadTvSeasons(5)
        advanceUntilIdle()
        assertTrue(h.snap().tvSeasons.isNotEmpty())

        coEvery { delegate.fetchTvDetails(6) } returns null
        h.loadTvSeasons(6)
        advanceUntilIdle()

        assertTrue(h.snap().tvSeasons.isEmpty())
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
