package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServer
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class SeerrRequestDelegateTest {

    private val repository: SeerrRepository = mockk(relaxed = true)
    private lateinit var delegate: SeerrRequestDelegate

    @BeforeTest
    fun setup() {
        delegate = SeerrRequestDelegate(repository)
    }

    // region requestMedia
    @Test
    fun `requestMedia delegates to repository with all params`() = runTest {
        val expected = SeerrMediaRequest(id = 7)
        coEvery {
            repository.requestMedia(
                mediaType = "tv",
                tmdbId = 123,
                seasons = listOf(1, 2),
                serverId = 5,
                profileId = 9,
                rootFolder = "/tv",
                tags = listOf(1),
            )
        } returns Result.success(expected)

        val result = delegate.requestMedia(
            mediaType = "tv",
            tmdbId = 123,
            seasons = listOf(1, 2),
            serverId = 5,
            profileId = 9,
            rootFolder = "/tv",
            tags = listOf(1),
        )

        assertTrue(result.isSuccess)
        assertEquals(7, result.getOrThrow().id)
    }

    @Test
    fun `requestMedia propagates failure from repository`() = runTest {
        coEvery { repository.requestMedia(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))

        val result = delegate.requestMedia(mediaType = "movie", tmdbId = 1)

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
    // endregion

    // region fetchServiceDetails
    @Test
    fun `fetchServiceDetails movie fetches radarr servers and details`() = runTest {
        val servers = listOf(SeerrServiceServer(id = 1), SeerrServiceServer(id = 2))
        coEvery { repository.getServiceRadarrServers() } returns Result.success(servers)
        val detail1 = SeerrRadarrServiceDetail(id = 1, name = "R1")
        val detail2 = SeerrRadarrServiceDetail(id = 2, name = "R2")
        coEvery { repository.getServiceRadarrDetail(1) } returns Result.success(detail1)
        coEvery { repository.getServiceRadarrDetail(2) } returns Result.success(detail2)

        val result = delegate.fetchServiceDetails("movie")

        assertEquals(listOf(detail1, detail2), result.radarrServers)
        assertTrue(result.sonarrServers.isEmpty())
        coVerify(exactly = 0) { repository.getServiceSonarrServers() }
    }

    @Test
    fun `fetchServiceDetails movie filters out null details and returns empty when servers list null`() = runTest {
        coEvery { repository.getServiceRadarrServers() } returns Result.success(emptyList())

        val result = delegate.fetchServiceDetails("movie")

        assertTrue(result.radarrServers.isEmpty())
        assertTrue(result.sonarrServers.isEmpty())
    }

    @Test
    fun `fetchServiceDetails movie returns empty when service servers call fails`() = runTest {
        coEvery { repository.getServiceRadarrServers() } returns Result.failure(RuntimeException("x"))

        val result = delegate.fetchServiceDetails("movie")

        assertTrue(result.radarrServers.isEmpty())
    }

    @Test
    fun `fetchServiceDetails movie filters out failed detail fetches`() = runTest {
        coEvery { repository.getServiceRadarrServers() } returns
            Result.success(listOf(SeerrServiceServer(id = 1), SeerrServiceServer(id = 2)))
        coEvery { repository.getServiceRadarrDetail(1) } returns
            Result.success(SeerrRadarrServiceDetail(id = 1, name = "ok"))
        coEvery { repository.getServiceRadarrDetail(2) } returns Result.failure(RuntimeException("bad"))

        val result = delegate.fetchServiceDetails("movie")

        assertEquals(1, result.radarrServers.size)
        assertEquals(1, result.radarrServers[0].id)
    }

    @Test
    fun `fetchServiceDetails tv fetches sonarr servers and details`() = runTest {
        val servers = listOf(SeerrServiceServer(id = 10))
        coEvery { repository.getServiceSonarrServers() } returns Result.success(servers)
        val detail = SeerrSonarrServiceDetail(id = 10, name = "S10")
        coEvery { repository.getServiceSonarrDetail(10) } returns Result.success(detail)

        val result = delegate.fetchServiceDetails("tv")

        assertEquals(listOf(detail), result.sonarrServers)
        assertTrue(result.radarrServers.isEmpty())
        coVerify(exactly = 0) { repository.getServiceRadarrServers() }
    }

    @Test
    fun `fetchServiceDetails tv returns empty when sonarr servers null`() = runTest {
        coEvery { repository.getServiceSonarrServers() } returns Result.failure(RuntimeException("nope"))

        val result = delegate.fetchServiceDetails("tv")

        assertTrue(result.sonarrServers.isEmpty())
    }
    // endregion

    // region fetchTvSeasons
    @Test
    fun `fetchTvSeasons returns seasons with positive seasonNumber`() = runTest {
        val seasons = listOf(
            SeerrSeason(seasonNumber = 0, name = "Specials"),
            SeerrSeason(seasonNumber = 1, name = "S1"),
            SeerrSeason(seasonNumber = 2, name = "S2"),
        )
        coEvery { repository.getTvDetails(99) } returns Result.success(SeerrTvDetails(seasons = seasons))

        val result = delegate.fetchTvSeasons(99)

        assertEquals(listOf(1, 2), result.map { it.seasonNumber })
    }

    @Test
    fun `fetchTvSeasons returns empty when getTvDetails fails`() = runTest {
        coEvery { repository.getTvDetails(99) } returns Result.failure(RuntimeException("err"))

        val result = delegate.fetchTvSeasons(99)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchTvSeasons returns empty when seasons list empty`() = runTest {
        coEvery { repository.getTvDetails(99) } returns Result.success(SeerrTvDetails(seasons = emptyList()))

        val result = delegate.fetchTvSeasons(99)

        assertTrue(result.isEmpty())
    }
    // endregion

    // region prefetchDetails
    @Test
    fun `prefetchDetails movie triggers movie details, ratings, recommendations, similar`() = runTest {
        coEvery { repository.getMovieDetails(1) } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getRatings(1, "movie") } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getRecommendations(1, MediaType.MOVIE) } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getSimilar(1, MediaType.MOVIE) } returns Result.success(mockk(relaxed = true))

        delegate.prefetchDetails(1, "movie")

        coVerify { repository.getMovieDetails(1) }
        coVerify { repository.getRatings(1, "movie") }
        coVerify { repository.getRecommendations(1, MediaType.MOVIE) }
        coVerify { repository.getSimilar(1, MediaType.MOVIE) }
        coVerify(exactly = 0) { repository.getTvDetails(any()) }
    }

    @Test
    fun `prefetchDetails tv triggers tv details, ratings, recommendations, similar`() = runTest {
        coEvery { repository.getTvDetails(2) } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getRatings(2, "tv") } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getRecommendations(2, MediaType.SERIES) } returns Result.success(mockk(relaxed = true))
        coEvery { repository.getSimilar(2, MediaType.SERIES) } returns Result.success(mockk(relaxed = true))

        delegate.prefetchDetails(2, "tv")

        coVerify { repository.getTvDetails(2) }
        coVerify { repository.getRatings(2, "tv") }
        coVerify { repository.getRecommendations(2, MediaType.SERIES) }
        coVerify { repository.getSimilar(2, MediaType.SERIES) }
        coVerify(exactly = 0) { repository.getMovieDetails(any()) }
    }

    @Test
    fun `prefetchDetails swallows exceptions without throwing`() = runTest {
        coEvery { repository.getMovieDetails(1) } returns Result.failure(RuntimeException("fail"))

        delegate.prefetchDetails(1, "movie") // should not throw
    }
    // endregion
}
