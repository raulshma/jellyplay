package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrDiscoveryError
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ArrRepositoryImplTest {

    private val radarrApiClient: RadarrApiClient = mockk(relaxed = true)
    private val sonarrApiClient: SonarrApiClient = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)
    private val arrPreferencesStore: ArrPreferencesStore = mockk(relaxed = true)

    // Stand-in for the production @ApplicationScope (never cancelled, same
    // lifetime discipline the singleton assumes).
    private val testScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    private lateinit var repository: ArrRepositoryImpl

    @Before
    fun setup() {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(ArrPreferences())
        coEvery { seerrRepository.getRadarrSettings() } returns Result.success(emptyList())
        coEvery { seerrRepository.getSonarrSettings() } returns Result.success(emptyList())
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
    }

    @Test
    fun `resolveServers returns empty summary when Seerr has no servers and no manual entries`() = runTest {
        val summary = repository.resolveServers().getOrThrow()
        assertTrue(summary.isEmpty)
    }

    @Test
    fun `resolveServers merges manual servers into summary`() = runTest {
        val manualRadarr = ArrServerConfig(
            id = "manual-r-1", baseUrl = "https://radarr.local", apiKey = "k",
            name = "Manual Radarr", kind = ArrServiceKind.RADARR, isManual = true,
        )
        val manualSonarr = ArrServerConfig(
            id = "manual-s-1", baseUrl = "https://sonarr.local", apiKey = "k",
            name = "Manual Sonarr", kind = ArrServiceKind.SONARR, isManual = true,
        )
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(manualRadarr, manualSonarr)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)

        val summary = repository.resolveServers().getOrThrow()
        assertEquals(listOf(manualRadarr), summary.radarrServers)
        assertEquals(listOf(manualSonarr), summary.sonarrServers)
    }

    @Test
    fun `resolveServers dedups auto-discovered and manual by baseUrl, manual wins`() = runTest {
        // Discovered server resolves to https://radarr.local:7878 (useSsl + port 7878);
        // the manual override must target the same canonical URL to be deduped.
        val manual = ArrServerConfig(
            id = "manual-x", baseUrl = "https://radarr.local:7878", apiKey = "manual-key",
            name = "Manual Override", kind = ArrServiceKind.RADARR, isManual = true,
        )
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true, manualServers = listOf(manual)),
        )
        coEvery { seerrRepository.getRadarrSettings() } returns Result.success(
            listOf(radarrSettings(id = 1, hostname = "radarr.local", apiKey = "discovered-key")),
        )

        val summary = repository.resolveServers().getOrThrow()
        assertEquals(1, summary.radarrServers.size)
        // Manual first in the concat order, so dedup keeps it.
        assertEquals("manual-x", summary.radarrServers[0].id)
        assertEquals("manual-key", summary.radarrServers[0].apiKey)
        // Successful discovery → no error surfaced.
        assertNull(summary.discoveryError)
    }

    @Test
    fun `resolveServers skips discovered servers with blank apiKey`() = runTest {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true),
        )
        coEvery { seerrRepository.getRadarrSettings() } returns Result.success(
            listOf(radarrSettings(id = 2, hostname = "radarr2.local", apiKey = "")),
        )

        val summary = repository.resolveServers().getOrThrow()
        assertTrue(summary.radarrServers.isEmpty())
        // Blank apiKey is a data problem, not a discovery failure.
        assertNull(summary.discoveryError)
    }

    @Test
    fun `resolveServers sets discoveryError NoAdminPermission when Seerr returns 403`() = runTest {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true),
        )
        // /settings/* is admin-only; a non-admin Seerr account gets 403.
        coEvery { seerrRepository.getRadarrSettings() } returns Result.failure(
            ApiException.fromSeerrHttp(httpCode = 403, message = "HTTP 403: Forbidden"),
        )

        val summary = repository.resolveServers().getOrThrow()
        assertTrue(summary.radarrServers.isEmpty())
        assertEquals(ArrDiscoveryError.NoAdminPermission, summary.discoveryError)
    }

    @Test
    fun `resolveServers sets discoveryError Other when Seerr call throws non-auth error`() = runTest {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true),
        )
        coEvery { seerrRepository.getRadarrSettings() } returns Result.failure(
            ApiException.fromSeerrHttp(httpCode = 500, message = "HTTP 500: boom"),
        )

        val summary = repository.resolveServers().getOrThrow()
        assertTrue(summary.radarrServers.isEmpty())
        assertTrue(summary.discoveryError is ArrDiscoveryError.Other)
        assertEquals("HTTP 500: boom", (summary.discoveryError as ArrDiscoveryError.Other).message)
    }

    @Test
    fun `resolveServers maps full SeerrRadarrSettings to ArrServerConfig via getFullUrl`() = runTest {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true),
        )
        coEvery { seerrRepository.getRadarrSettings() } returns Result.success(
            listOf(radarrSettings(id = 7, hostname = "radarr.local", apiKey = "key-7", baseUrl = "/radarr")),
        )

        val summary = repository.resolveServers().getOrThrow()
        assertEquals(1, summary.radarrServers.size)
        val srv = summary.radarrServers[0]
        assertEquals("radarr-7", srv.id)
        assertEquals("https://radarr.local:7878/radarr", srv.baseUrl)
        assertEquals("key-7", srv.apiKey)
        assertEquals("Radarr 7", srv.name)
        assertEquals(ArrServiceKind.RADARR, srv.kind)
        assertFalse(srv.isManual)
        assertNull(summary.discoveryError)
    }

    @Test
    fun `resolveServers degrades to empty when Seerr discovery fails`() = runTest {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = true),
        )
        coEvery { seerrRepository.getRadarrSettings() } returns Result.failure(RuntimeException("boom"))

        val result = repository.resolveServers()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty)
    }

    @Test
    fun `refreshQueue fans out across servers and concatenates results`() = runTest {
        val radarrSrv = ArrServerConfig("r", "https://r.local", "k", "R", ArrServiceKind.RADARR)
        val sonarrSrv = ArrServerConfig("s", "https://s.local", "k", "S", ArrServiceKind.SONARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv, sonarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        val radarrItem = ArrQueueItem(queueId = 1, title = "R1", status = ArrDownloadStatus.DOWNLOADING, tmdbId = 11)
        val sonarrItem = ArrQueueItem(queueId = 2, title = "S1", status = ArrDownloadStatus.QUEUED, tvdbId = 22)
        coEvery { radarrApiClient.getQueue("https://r.local", "k") } returns Result.success(listOf(radarrItem))
        coEvery { sonarrApiClient.getQueue("https://s.local", "k") } returns Result.success(listOf(sonarrItem))

        repository.refreshQueue()
        val queue = repository.queue().first()
        assertEquals(2, queue.size)
        assertTrue(queue.any { it.tmdbId == 11 })
        assertTrue(queue.any { it.tvdbId == 22 })
    }

    @Test
    fun `refreshQueue swallows per-server failures and returns the survivors`() = runTest {
        val srv1 = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
        val srv2 = ArrServerConfig("r2", "https://r2.local", "k", "R2", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(srv1, srv2)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        coEvery { radarrApiClient.getQueue("https://r1.local", "k") } returns Result.failure(RuntimeException("down"))
        val survivor = ArrQueueItem(queueId = 9, title = "OK", status = ArrDownloadStatus.DOWNLOADING, tmdbId = 99)
        coEvery { radarrApiClient.getQueue("https://r2.local", "k") } returns Result.success(listOf(survivor))

        repository.refreshQueue()
        val queue = repository.queue().first()
        assertEquals(1, queue.size)
        assertEquals(99, queue[0].tmdbId)
    }

    @Test
    fun `getQueueForTmdb returns matching item after refresh`() = runTest {
        val srv = ArrServerConfig("r", "https://r.local", "k", "R", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(srv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        coEvery { radarrApiClient.getQueue(any(), any()) } returns Result.success(
            listOf(ArrQueueItem(queueId = 1, title = "x", status = ArrDownloadStatus.DOWNLOADING, tmdbId = 777)),
        )

        val found = repository.getQueueForTmdb(777)
        assertEquals(777, found?.tmdbId)
        assertNull(repository.getQueueForTmdb(999))
    }

    @Test
    fun `buildBaseUrl prefers externalUrl`() {
        val url = ArrRepositoryImpl.buildBaseUrl(
            externalUrl = "https://radarr.example.com",
            useSsl = false, hostname = "x", port = 1, baseUrl = null,
        )
        assertEquals("https://radarr.example.com", url)
    }

    @Test
    fun `buildBaseUrl builds from hostname port and scheme`() {
        val url = ArrRepositoryImpl.buildBaseUrl(
            externalUrl = null, useSsl = true, hostname = "radarr.local", port = 7878, baseUrl = "/radarr",
        )
        assertEquals("https://radarr.local:7878/radarr", url)
    }

    @Test
    fun `buildBaseUrl returns null when hostname blank`() {
        assertNull(
            ArrRepositoryImpl.buildBaseUrl(null, false, "", 1, null),
        )
    }

    @Test
    fun `canonicalBaseUrl lowercases and strips trailing slash`() {
        assertEquals("https://radarr.local", ArrRepositoryImpl.canonicalBaseUrl("https://radarr.local/"))
        assertEquals("https://radarr.local", ArrRepositoryImpl.canonicalBaseUrl("HTTPS://Radarr.Local"))
    }

    // ── Management actions ─────────────────────────────────────────────────

    @Test
    fun `deleteQueueItem routes to owning server by id and kind`() = runTest {
        val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        coEvery { radarrApiClient.deleteQueueItem(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { radarrApiClient.getQueue(any(), any()) } returns Result.success(emptyList())
        val item = ArrQueueItem(
            queueId = 5, title = "x", status = ArrDownloadStatus.DOWNLOADING,
            tmdbId = 1, serverId = "r1", serverKind = ArrServiceKind.RADARR,
        )

        val result = repository.deleteQueueItem(item, ArrQueueDeleteOptions())
        assertTrue(result.isSuccess)
        coVerify { radarrApiClient.deleteQueueItem("https://r1.local", "k", 5, any()) }
    }

    @Test
    fun `deleteQueueItem routes Sonarr items to sonarr client`() = runTest {
        val sonarrSrv = ArrServerConfig("s1", "https://s1.local", "k", "S1", ArrServiceKind.SONARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(sonarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        coEvery { sonarrApiClient.deleteQueueItem(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { sonarrApiClient.getQueue(any(), any()) } returns Result.success(emptyList())
        val item = ArrQueueItem(
            queueId = 9, title = "ep", status = ArrDownloadStatus.DOWNLOADING,
            serverId = "s1", serverKind = ArrServiceKind.SONARR,
        )

        val result = repository.deleteQueueItem(item, ArrQueueDeleteOptions(blocklist = true))
        assertTrue(result.isSuccess)
        coVerify { sonarrApiClient.deleteQueueItem(any(), any(), 9, any()) }
    }

    @Test
    fun `deleteQueueItem fails when owning server not configured`() = runTest {
        val item = ArrQueueItem(
            queueId = 1, title = "x", status = ArrDownloadStatus.QUEUED,
            serverId = "gone", serverKind = ArrServiceKind.RADARR,
        )
        val result = repository.deleteQueueItem(item, ArrQueueDeleteOptions())
        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteBlocklistItem refreshes blocklist after success`() = runTest {
        val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        coEvery { radarrApiClient.deleteBlocklistItem(any(), any(), any()) } returns Result.success(Unit)
        coEvery { radarrApiClient.getBlocklist(any(), any(), any(), any()) } returns Result.success(emptyList())
        val item = ArrBlocklistItem(id = 3, title = "blk", serverId = "r1", serverKind = ArrServiceKind.RADARR)

        val result = repository.deleteBlocklistItem(item)
        assertTrue(result.isSuccess)
        coVerify { radarrApiClient.deleteBlocklistItem("https://r1.local", "k", 3) }
    }

    @Test
    fun `searchForTmdb resolves tmdb to radarr id then sends SearchMovie`() = runTest {
        val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        // tmdbId 555 resolves to Radarr internal movie id 42.
        coEvery { radarrApiClient.findMovieIdByTmdb("https://r1.local", "k", 555) } returns Result.success(42)
        coEvery {
            radarrApiClient.postCommand(any(), any(), any(), any(), any())
        } returns Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(id = 1, name = "SearchMovie", status = "queued"))

        val result = repository.searchForTmdb(555, ArrServiceKind.RADARR)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        // postCommand must be called with the resolved internal id, not the tmdbId.
        coVerify {
            radarrApiClient.findMovieIdByTmdb("https://r1.local", "k", 555)
            radarrApiClient.postCommand(
                "https://r1.local", "k", ArrCommandName.SEARCH_MOVIE, movieIds = listOf(42), episodeIds = null,
            )
        }
    }

    @Test
    fun `searchForTmdb falls back to MissingMoviesSearch when movie not tracked`() = runTest {
        val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
        // tmdbId not tracked → lookup returns null → fall back to global search.
        coEvery { radarrApiClient.findMovieIdByTmdb(any(), any(), any()) } returns Result.success(null)
        coEvery {
            radarrApiClient.postCommand(any(), any(), any(), any(), any())
        } returns Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(id = 1, name = "MissingMoviesSearch", status = "queued"))

        val result = repository.searchForTmdb(555, ArrServiceKind.RADARR)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        coVerify {
            radarrApiClient.postCommand(
                "https://r1.local", "k", ArrCommandName.MISSING_SEARCH, movieIds = null, episodeIds = null,
            )
        }
    }

    // ── redownloadMedia (delete & re-download flow) ────────────────────────

    private val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
    private val sonarrSrv = ArrServerConfig("s1", "https://s1.local", "k", "S1", ArrServiceKind.SONARR)

    private fun setupRadarrOnly() {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(radarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
    }

    private fun setupSonarrOnly() {
        every { arrPreferencesStore.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(sonarrSrv)),
        )
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)
    }

    @Test
    fun `redownloadMedia movie deletes file then verifies monitors and searches`() = runTest {
        setupRadarrOnly()
        val movieInfo = com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo(
            id = 42, movieFileId = 9001, hasFile = true, monitored = false,
        )
        // First getMovieForTmdb returns the movie with a file; subsequent
        // calls (the verify re-query) return hasFile=false. Use a counter so
        // MockK returns the right value per call order.
        var callCount = 0
        coEvery { radarrApiClient.getMovieForTmdb(any(), any(), any()) } answers {
            callCount++
            Result.success(if (callCount == 1) movieInfo else movieInfo.copy(hasFile = false, movieFileId = 0))
        }
        coEvery { radarrApiClient.deleteMovieFile("https://r1.local", "k", 9001) } returns Result.success(Unit)
        coEvery { radarrApiClient.monitorMovies("https://r1.local", "k", listOf(42), true) } returns Result.success(Unit)
        coEvery { radarrApiClient.postCommand(any(), any(), any(), any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(1, "SearchMovie", "queued"))

        val result = repository.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
        val steps = result.steps.associateBy { it.step }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE]?.status)
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.VERIFY_DELETED]?.status)
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.MONITOR]?.status)
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.SEARCH]?.status)
        assertTrue(result.isComplete)
        coVerify { radarrApiClient.deleteMovieFile("https://r1.local", "k", 9001) }
    }

    @Test
    fun `redownloadMedia movie skips monitor when already monitored`() = runTest {
        setupRadarrOnly()
        val movieInfo = com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo(
            id = 42, movieFileId = 9001, hasFile = true, monitored = true,
        )
        coEvery { radarrApiClient.getMovieForTmdb("https://r1.local", "k", 555) } returns Result.success(movieInfo)
        coEvery { radarrApiClient.deleteMovieFile(any(), any(), any()) } returns Result.success(Unit)
        coEvery { radarrApiClient.getMovieForTmdb(any(), any(), any()) } returns Result.success(movieInfo.copy(hasFile = false, movieFileId = 0))
        coEvery { radarrApiClient.postCommand(any(), any(), any(), any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(1, "SearchMovie", "queued"))

        val result = repository.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
        val monitor = result.steps.first { it.step == com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.MONITOR }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SKIPPED, monitor.status)
        coVerify(exactly = 0) { radarrApiClient.monitorMovies(any(), any(), any(), any()) }
    }

    @Test
    fun `redownloadMedia movie aborts when delete fails`() = runTest {
        setupRadarrOnly()
        val movieInfo = com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo(
            id = 42, movieFileId = 9001, hasFile = true, monitored = false,
        )
        coEvery { radarrApiClient.getMovieForTmdb(any(), any(), any()) } returns Result.success(movieInfo)
        coEvery { radarrApiClient.deleteMovieFile(any(), any(), any()) } returns
            Result.failure(ApiException.fromHttp(409, "Root folder missing"))

        val result = repository.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
        val deleteStep = result.steps.first { it.step == com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.FAILED, deleteStep.status)
        assertFalse(result.isComplete)
        // No subsequent steps should run.
        assertEquals(1, result.steps.size)
    }

    @Test
    fun `redownloadMedia movie skips delete when no file present`() = runTest {
        setupRadarrOnly()
        val movieInfo = com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo(
            id = 42, movieFileId = 0, hasFile = false, monitored = false,
        )
        coEvery { radarrApiClient.getMovieForTmdb(any(), any(), any()) } returns Result.success(movieInfo)
        coEvery { radarrApiClient.monitorMovies(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { radarrApiClient.postCommand(any(), any(), any(), any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(1, "SearchMovie", "queued"))

        val result = repository.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
        val deleteStep = result.steps.first { it.step == com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SKIPPED, deleteStep.status)
        // Flow continues (file was already gone — not an error).
        assertTrue(result.isComplete)
        coVerify(exactly = 0) { radarrApiClient.deleteMovieFile(any(), any(), any()) }
    }

    @Test
    fun `redownloadMedia episode resolves series then episode then deletes file`() = runTest {
        setupSonarrOnly()
        val episodeInfo = com.raulshma.jellyplay.core.network.arr.SonarrEpisodeInfo(
            id = 7, episodeFileId = 500, hasFile = true, monitored = false, seasonNumber = 2,
        )
        coEvery { sonarrApiClient.findSeriesByTvdb("https://s1.local", "k", 123) } returns Result.success(10)
        var callCount = 0
        coEvery { sonarrApiClient.getEpisodeInfo(any(), any(), any(), any(), any()) } answers {
            callCount++
            Result.success(if (callCount == 1) episodeInfo else episodeInfo.copy(hasFile = false, episodeFileId = 0))
        }
        coEvery { sonarrApiClient.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
        coEvery { sonarrApiClient.monitorEpisodes("https://s1.local", "k", listOf(7), true) } returns Result.success(Unit)
        coEvery { sonarrApiClient.postCommand(any(), any(), any(), any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(1, "EpisodeSearch", "queued"))

        val result = repository.redownloadMedia(0, ArrServiceKind.SONARR, tvdbId = 123, seasonNumber = 2, episodeNumber = 5).getOrThrow()
        val steps = result.steps.associateBy { it.step }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE]?.status)
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.MONITOR]?.status)
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.SEARCH]?.status)
        assertTrue(result.isComplete)
        coVerify {
            sonarrApiClient.deleteEpisodeFile("https://s1.local", "k", 500)
            sonarrApiClient.postCommand("https://s1.local", "k", ArrCommandName.SEARCH_EPISODES, seriesId = null, episodeIds = listOf(7))
        }
    }

    @Test
    fun `redownloadMedia episode not found shows diagnostic with season summaries`() = runTest {
        setupSonarrOnly()
        coEvery { sonarrApiClient.findSeriesByTvdb("https://s1.local", "k", 123) } returns Result.success(10)
        coEvery { sonarrApiClient.getEpisodeInfo(any(), any(), any(), any(), any()) } returns Result.success(null)
        coEvery {
            sonarrApiClient.getSeasonSummaries("https://s1.local", "k", 10)
        } returns Result.success(
            listOf(
                com.raulshma.jellyplay.core.network.arr.SonarrSeasonSummary(0, listOf(1, 2, 3)),
                com.raulshma.jellyplay.core.network.arr.SonarrSeasonSummary(1, (1..12).toList()),
            )
        )

        val result = repository.redownloadMedia(0, ArrServiceKind.SONARR, tvdbId = 123, seasonNumber = 5, episodeNumber = 12).getOrThrow()
        val deleteStep = result.steps.first { it.step == com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.FAILED, deleteStep.status)
        val msg = deleteStep.message!!
        assertTrue("diagnostic should list Sonarr seasons: $msg", msg.contains("S0 (eps 1–3)"))
        assertTrue("diagnostic should list S1 range: $msg", msg.contains("S1 (eps 1–12)"))
        assertTrue("diagnostic should name the requested episode: $msg", msg.contains("E12"))
        assertFalse(result.isComplete)
    }

    @Test
    fun `redownloadMedia verify-deleted surfaces WARNING when re-query returns null`() = runTest {
        setupSonarrOnly()
        val episodeInfo = com.raulshma.jellyplay.core.network.arr.SonarrEpisodeInfo(
            id = 7, episodeFileId = 500, hasFile = true, monitored = true, seasonNumber = 2,
        )
        coEvery { sonarrApiClient.findSeriesByTvdb("https://s1.local", "k", 123) } returns Result.success(10)
        var callCount = 0
        // 1st getEpisodeInfo → episode present; 2nd (verify re-query) → null.
        coEvery { sonarrApiClient.getEpisodeInfo(any(), any(), any(), any(), any()) } answers {
            callCount++
            Result.success(if (callCount == 1) episodeInfo else null)
        }
        coEvery { sonarrApiClient.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
        coEvery { sonarrApiClient.postCommand(any(), any(), any(), any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.arr.ArrCommand(1, "EpisodeSearch", "queued"))

        val result = repository.redownloadMedia(0, ArrServiceKind.SONARR, tvdbId = 123, seasonNumber = 2, episodeNumber = 5).getOrThrow()
        val steps = result.steps.associateBy { it.step }
        // Verify should be WARNING (inconclusive), not SUCCESS — the prior bug.
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.WARNING, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.VERIFY_DELETED]?.status)
        // WARNING is not a hard gate; flow should continue to completion.
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.SUCCESS, steps[com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.SEARCH]?.status)
        assertTrue(result.isComplete)
    }

    @Test
    fun `redownloadMedia fails fast when no relevant server configured`() = runTest {
        // No servers configured at all.
        every { arrPreferencesStore.preferences } returns MutableStateFlow(ArrPreferences())
        repository = ArrRepositoryImpl(radarrApiClient, sonarrApiClient, seerrRepository, arrPreferencesStore, testScope)

        val result = repository.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
        val deleteStep = result.steps.first { it.step == com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep.DELETE_FILE }
        assertEquals(com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus.FAILED, deleteStep.status)
        assertFalse(result.isComplete)
    }

    private fun radarrSettings(id: Int, hostname: String, apiKey: String, baseUrl: String? = null) =
        SeerrRadarrSettings(
            id = id, name = "Radarr $id", hostname = hostname, port = 7878, apiKey = apiKey,
            useSsl = true, baseUrl = baseUrl,
        )
}
