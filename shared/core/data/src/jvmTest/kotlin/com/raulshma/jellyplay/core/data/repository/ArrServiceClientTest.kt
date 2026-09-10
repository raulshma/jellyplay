package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the Radarr/Sonarr dispatch seam ([ArrServiceClient]): each adapter
 * forwards the bound [ArrServerConfig]'s baseUrl + apiKey and the call's own
 * arguments to exactly its injected client, and [ArrServiceClient.postCommand]
 * forwards only its client's subset of the parameter union — a kind-mismatched
 * parameter is silently dropped (never cross-wired to the other client).
 */
class ArrServiceClientTest {

    private val server = ArrServerConfig(
        id = "srv-1",
        baseUrl = "https://radarr.example",
        apiKey = "key",
        name = "main",
        kind = ArrServiceKind.RADARR,
    )
    private val radarr: RadarrApiClient = mockk(relaxed = true)
    private val sonarr: SonarrApiClient = mockk(relaxed = true)
    private val radarrClient = RadarrServiceClient(radarr, server)
    private val sonarrClient = SonarrServiceClient(sonarr, server)

    private val deleteOptions = ArrQueueDeleteOptions(
        removeFromClient = true,
        blocklist = true,
        skipRedownload = false,
    )

    /**
     * Every non-command surface method once, with distinct arguments, so the
     * forwarding assertions below can tell the calls apart.
     */
    private suspend fun ArrServiceClient.exerciseForwardingSurface() {
        getQueue()
        deleteQueueItem(42, deleteOptions)
        deleteQueueItems(listOf(1, 2), deleteOptions)
        grabQueueItem(7)
        importQueueItem("guid-9")
        getCalendar("2026-01-01", "2026-01-31")
        getBlocklist()
        deleteBlocklistItem(3)
        deleteBlocklistItems(listOf(3, 4))
        testConnection()
    }

    @Test
    fun radarr_adapter_forwardsServerCredentialsAndOwnArgs() = runTest {
        radarrClient.exerciseForwardingSurface()

        coVerify {
            radarr.getQueue("https://radarr.example", "key")
            radarr.deleteQueueItem("https://radarr.example", "key", 42, deleteOptions)
            radarr.deleteQueueItems("https://radarr.example", "key", listOf(1, 2), deleteOptions)
            radarr.grabQueueItem("https://radarr.example", "key", 7)
            radarr.importQueueItem("https://radarr.example", "key", "guid-9")
            radarr.getCalendar("https://radarr.example", "key", "2026-01-01", "2026-01-31")
            radarr.getBlocklist("https://radarr.example", "key")
            radarr.deleteBlocklistItem("https://radarr.example", "key", 3)
            radarr.deleteBlocklistItems("https://radarr.example", "key", listOf(3, 4))
            radarr.testConnection("https://radarr.example", "key")
        }
        verify { sonarr wasNot Called }
    }

    @Test
    fun sonarr_adapter_forwardsServerCredentialsAndOwnArgs() = runTest {
        sonarrClient.exerciseForwardingSurface()

        coVerify {
            sonarr.getQueue("https://radarr.example", "key")
            sonarr.deleteQueueItem("https://radarr.example", "key", 42, deleteOptions)
            sonarr.deleteQueueItems("https://radarr.example", "key", listOf(1, 2), deleteOptions)
            sonarr.grabQueueItem("https://radarr.example", "key", 7)
            sonarr.importQueueItem("https://radarr.example", "key", "guid-9")
            sonarr.getCalendar("https://radarr.example", "key", "2026-01-01", "2026-01-31")
            sonarr.getBlocklist("https://radarr.example", "key")
            sonarr.deleteBlocklistItem("https://radarr.example", "key", 3)
            sonarr.deleteBlocklistItems("https://radarr.example", "key", listOf(3, 4))
            sonarr.testConnection("https://radarr.example", "key")
        }
        verify { radarr wasNot Called }
    }

    @Test
    fun radarr_postCommand_forwardsOnlyRadarrsParameterSubset() = runTest {
        val command = ArrCommand(id = 1, name = "SearchMovie", status = "queued")
        coEvery {
            radarr.postCommand(any(), any(), any(), any(), any())
        } returns Result.success(command)

        val result = radarrClient.postCommand(
            commandName = ArrCommandName.SEARCH_MOVIE,
            movieIds = listOf(7),
            episodeIds = listOf(9),
            // Sonarr's parameters: silently dropped by the Radarr adapter.
            seriesId = 99,
            seasonNumber = 3,
        )

        assertEquals(command, result.getOrThrow())
        coVerify(exactly = 1) {
            radarr.postCommand(
                "https://radarr.example", "key", ArrCommandName.SEARCH_MOVIE,
                movieIds = listOf(7), episodeIds = listOf(9),
            )
        }
    }

    @Test
    fun sonarr_postCommand_forwardsOnlySonarrsParameterSubset() = runTest {
        val command = ArrCommand(id = 2, name = "SeriesSearch", status = "queued")
        coEvery {
            sonarr.postCommand(any(), any(), any(), any(), any(), any())
        } returns Result.success(command)

        val result = sonarrClient.postCommand(
            commandName = ArrCommandName.SEARCH_SERIES,
            episodeIds = listOf(11),
            seriesId = 99,
            seasonNumber = 3,
            // Radarr's parameter: silently dropped by the Sonarr adapter.
            movieIds = listOf(7),
        )

        assertEquals(command, result.getOrThrow())
        coVerify(exactly = 1) {
            sonarr.postCommand(
                "https://radarr.example", "key", ArrCommandName.SEARCH_SERIES,
                seriesId = 99, episodeIds = listOf(11), seasonNumber = 3,
            )
        }
    }

    @Test
    fun failures_passThroughUnchanged() = runTest {
        val boom = IOException("connection refused")
        coEvery { sonarr.testConnection(any(), any()) } returns Result.failure(boom)

        val result = sonarrClient.testConnection()

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
        assertFailsWith<IOException> { result.getOrThrow() }
    }
}
