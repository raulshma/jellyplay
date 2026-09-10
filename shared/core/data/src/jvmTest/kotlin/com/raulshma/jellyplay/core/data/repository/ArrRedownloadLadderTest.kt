package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrEpisodeInfo
import com.raulshma.jellyplay.core.network.arr.SonarrSeasonSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Table-driven pin of the ONE redownload step-ladder
 * ([ArrRepositoryImpl.redownloadLadder]) per service, driven through
 * [ArrRepositoryImpl.redownloadMedia]. Each row asserts the full ordered step
 * list (status + byte-exact message via data-class equality) and
 * `isComplete`, covering: not-tracked, no-file (SKIP), delete-fail gate,
 * verify-fail, already-monitored (SKIP), full success — plus the
 * service-specific branches (Radarr's continue-on-verify-FAIL quirk, Sonarr's
 * verify gate + inconclusive-re-query WARNINGs and the episode-not-found
 * diagnostics).
 */
class ArrRedownloadLadderTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val radarrSrv = ArrServerConfig("r1", "https://r1.local", "k", "R1", ArrServiceKind.RADARR)
    private val sonarrSrv = ArrServerConfig("s1", "https://s1.local", "k", "S1", ArrServiceKind.SONARR)

    /** Fresh mocks + repository per row, so stubs never leak between rows. */
    private fun newHarness(server: ArrServerConfig): Triple<RadarrApiClient, SonarrApiClient, ArrRepositoryImpl> {
        val radarr = mockk<RadarrApiClient>(relaxed = true)
        val sonarr = mockk<SonarrApiClient>(relaxed = true)
        val seerr = mockk<SeerrRepository>(relaxed = true)
        coEvery { seerr.getRadarrSettings() } returns Result.success(emptyList())
        coEvery { seerr.getSonarrSettings() } returns Result.success(emptyList())
        val prefs = mockk<ArrPreferencesStore>(relaxed = true)
        every { prefs.preferences } returns MutableStateFlow(
            ArrPreferences(useSeerrDiscovery = false, manualServers = listOf(server)),
        )
        return Triple(radarr, sonarr, ArrRepositoryImpl(radarr, sonarr, seerr, prefs, testScope))
    }

    private class Row(
        val name: String,
        val arrange: suspend (RadarrApiClient, SonarrApiClient) -> Unit = { _, _ -> },
        /** Request overrides (Radarr always tmdb 555; Sonarr tvdb 123 S2E5 unless overridden). */
        val tvdbId: Int? = 123,
        val seasonNumber: Int? = 2,
        val episodeNumber: Int? = 5,
        val expectedSteps: List<ArrRedownloadStepResult>,
        val expectedComplete: Boolean,
        val postVerify: suspend (RadarrApiClient, SonarrApiClient) -> Unit = { _, _ -> },
    )

    private fun step(step: ArrRedownloadStep, status: ArrRedownloadStepStatus, message: String?) =
        ArrRedownloadStepResult(step, status, message)

    private suspend fun runLadder(rows: List<Row>, server: ArrServerConfig, sonarrPath: Boolean) {
        for (row in rows) {
            val (radarr, sonarr, repo) = newHarness(server)
            row.arrange(radarr, sonarr)
            val result = if (sonarrPath) {
                repo.redownloadMedia(
                    0, ArrServiceKind.SONARR,
                    tvdbId = row.tvdbId, seasonNumber = row.seasonNumber, episodeNumber = row.episodeNumber,
                ).getOrThrow()
            } else {
                repo.redownloadMedia(555, ArrServiceKind.RADARR).getOrThrow()
            }
            assertEquals(row.expectedSteps, result.steps, "[${row.name}] steps")
            assertEquals(row.expectedComplete, result.isComplete, "[${row.name}] isComplete")
            row.postVerify(radarr, sonarr)
        }
    }

    // ── Radarr (movie) table ───────────────────────────────────────────────

    private fun radarrMovie(fileId: Int, hasFile: Boolean, monitored: Boolean) =
        RadarrMovieInfo(id = 42, movieFileId = fileId, hasFile = hasFile, monitored = monitored)

    /** First lookup returns [movie]; the verify re-query returns it file-less. */
    private suspend fun stubRadarrLookupThenVerified(radarr: RadarrApiClient, movie: RadarrMovieInfo) {
        var calls = 0
        coEvery { radarr.getMovieForTmdb("https://r1.local", "k", 555) } answers {
            calls++
            Result.success(if (calls == 1) movie else movie.copy(hasFile = false, movieFileId = 0))
        }
    }

    @Test
    fun radarr_ladderTable() = runTest {
        val rows = listOf(
            Row(
                "not tracked aborts at the DELETE_FILE gate",
                arrange = { r, _ ->
                    coEvery { r.getMovieForTmdb("https://r1.local", "k", 555) } returns Result.success(null)
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Movie (tmdb 555) not tracked in Radarr."),
                ),
                expectedComplete = false,
            ),
            Row(
                "lookup failure aborts with the error",
                arrange = { r, _ ->
                    coEvery { r.getMovieForTmdb(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Radarr lookup failed: boom."),
                ),
                expectedComplete = false,
            ),
            Row(
                "no file skips the delete",
                arrange = { r, _ ->
                    stubRadarrLookupThenVerified(r, radarrMovie(fileId = 0, hasFile = false, monitored = false))
                    coEvery { r.monitorMovies(any(), any(), any(), any()) } returns Result.success(Unit)
                    coEvery { r.postCommand(any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "SearchMovie", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SKIPPED, "No file to delete."),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Radarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { r, _ ->
                    coVerify(exactly = 0) { r.deleteMovieFile(any(), any(), any()) }
                },
            ),
            Row(
                "delete failure is a hard gate",
                arrange = { r, _ ->
                    coEvery { r.getMovieForTmdb("https://r1.local", "k", 555) } returns
                        Result.success(radarrMovie(fileId = 9001, hasFile = true, monitored = false))
                    coEvery { r.deleteMovieFile("https://r1.local", "k", 9001) } returns
                        Result.failure(ApiException.fromHttp(409, "Root folder missing"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Radarr rejected the file delete."),
                ),
                expectedComplete = false,
                postVerify = { r, _ ->
                    coVerify(exactly = 0) { r.postCommand(any(), any(), any(), any(), any()) }
                },
            ),
            Row(
                "verify failure does NOT gate (Radarr continues best-effort)",
                arrange = { r, _ ->
                    // Both lookups see hasFile=true, so the verify re-check fails.
                    coEvery { r.getMovieForTmdb("https://r1.local", "k", 555) } returns
                        Result.success(radarrMovie(fileId = 9001, hasFile = true, monitored = false))
                    coEvery { r.deleteMovieFile("https://r1.local", "k", 9001) } returns Result.success(Unit)
                    coEvery { r.monitorMovies(any(), any(), any(), any()) } returns Result.success(Unit)
                    coEvery { r.postCommand(any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "SearchMovie", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.FAILED, "Radarr still reports a file present."),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Radarr is searching for a new download."),
                ),
                expectedComplete = true,
            ),
            Row(
                "already monitored skips the monitor step",
                arrange = { r, _ ->
                    stubRadarrLookupThenVerified(r, radarrMovie(fileId = 9001, hasFile = true, monitored = true))
                    coEvery { r.deleteMovieFile("https://r1.local", "k", 9001) } returns Result.success(Unit)
                    coEvery { r.postCommand(any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "SearchMovie", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SKIPPED, "Already monitored."),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Radarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { r, _ ->
                    coVerify(exactly = 0) { r.monitorMovies(any(), any(), any(), any()) }
                },
            ),
            Row(
                "full success runs all four steps",
                arrange = { r, _ ->
                    stubRadarrLookupThenVerified(r, radarrMovie(fileId = 9001, hasFile = true, monitored = false))
                    coEvery { r.deleteMovieFile("https://r1.local", "k", 9001) } returns Result.success(Unit)
                    coEvery { r.monitorMovies(any(), any(), any(), any()) } returns Result.success(Unit)
                    coEvery { r.postCommand(any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "SearchMovie", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Radarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { r, _ ->
                    coVerify { r.deleteMovieFile("https://r1.local", "k", 9001) }
                },
            ),
        )
        runLadder(rows, radarrSrv, sonarrPath = false)
    }

    // ── Sonarr (episode) table ─────────────────────────────────────────────

    private fun sonarrEpisode(fileId: Int, hasFile: Boolean, monitored: Boolean) = SonarrEpisodeInfo(
        id = 7, episodeFileId = fileId, hasFile = hasFile, monitored = monitored, seasonNumber = 2,
    )

    private suspend fun stubSonarrSeriesLookup(sonarr: SonarrApiClient, seriesId: Int? = 10) {
        coEvery { sonarr.findSeriesByTvdb("https://s1.local", "k", 123) } returns Result.success(seriesId)
    }

    /** First episode lookup returns [episode]; the verify re-query returns it file-less. */
    private suspend fun stubSonarrLookupThenVerified(sonarr: SonarrApiClient, episode: SonarrEpisodeInfo) {
        var calls = 0
        coEvery { sonarr.getEpisodeInfo(any(), any(), any(), any(), any()) } answers {
            calls++
            Result.success(if (calls == 1) episode else episode.copy(hasFile = false, episodeFileId = 0))
        }
    }

    @Test
    fun sonarr_ladderTable() = runTest {
        val rows = listOf(
            Row(
                "missing ids aborts before any client call",
                tvdbId = null,
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Missing tvdb id or season/episode number."),
                ),
                expectedComplete = false,
                postVerify = { _, s ->
                    coVerify(exactly = 0) { s.findSeriesByTvdb(any(), any(), any()) }
                },
            ),
            Row(
                "series not tracked aborts at the DELETE_FILE gate",
                arrange = { _, s -> stubSonarrSeriesLookup(s, seriesId = null) },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Series (tvdb 123) not tracked in Sonarr."),
                ),
                expectedComplete = false,
            ),
            Row(
                "series lookup failure aborts with the error",
                arrange = { _, s ->
                    coEvery { s.findSeriesByTvdb(any(), any(), any()) } returns Result.failure(RuntimeException("down"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Sonarr lookup failed: down."),
                ),
                expectedComplete = false,
            ),
            Row(
                "episode lookup failure aborts with the error",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns
                        Result.failure(RuntimeException("nope"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Sonarr episode lookup failed: nope."),
                ),
                expectedComplete = false,
            ),
            Row(
                "episode not found lists what Sonarr has (requested season)",
                seasonNumber = 5,
                episodeNumber = 12,
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns Result.success(null)
                    coEvery { s.getSeasonSummaries("https://s1.local", "k", 10) } returns Result.success(
                        listOf(
                            SonarrSeasonSummary(0, listOf(1, 2, 3)),
                            SonarrSeasonSummary(1, (1..12).toList()),
                        ),
                    )
                },
                expectedSteps = listOf(
                    step(
                        ArrRedownloadStep.DELETE_FILE,
                        ArrRedownloadStepStatus.FAILED,
                        "Sonarr has: S0 (eps 1–3), S1 (eps 1–12). No episode numbered E12 found (requested as S5E12) in series 10.",
                    ),
                ),
                expectedComplete = false,
            ),
            Row(
                "episode not found with an empty season summary renders it bare",
                seasonNumber = 5,
                episodeNumber = 12,
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns Result.success(null)
                    coEvery { s.getSeasonSummaries("https://s1.local", "k", 10) } returns Result.success(
                        listOf(
                            SonarrSeasonSummary(0, emptyList()),
                            SonarrSeasonSummary(1, (1..12).toList()),
                        ),
                    )
                },
                expectedSteps = listOf(
                    step(
                        ArrRedownloadStep.DELETE_FILE,
                        ArrRedownloadStepStatus.FAILED,
                        "Sonarr has: S0 (no episodes listed), S1 (eps 1–12). No episode numbered E12 found (requested as S5E12) in series 10.",
                    ),
                ),
                expectedComplete = false,
            ),
            Row(
                "episode not found with no seasons says so",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns Result.success(null)
                    coEvery { s.getSeasonSummaries("https://s1.local", "k", 10) } returns Result.success(emptyList())
                },
                expectedSteps = listOf(
                    step(
                        ArrRedownloadStep.DELETE_FILE,
                        ArrRedownloadStepStatus.FAILED,
                        "Sonarr has no episodes for this series. No episode numbered E5 found (requested as S2E5) in series 10.",
                    ),
                ),
                expectedComplete = false,
            ),
            Row(
                "no file skips the delete",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    stubSonarrLookupThenVerified(s, sonarrEpisode(fileId = 0, hasFile = false, monitored = false))
                    coEvery { s.monitorEpisodes(any(), any(), any(), any()) } returns Result.success(Unit)
                    coEvery { s.postCommand(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "EpisodeSearch", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SKIPPED, "No file to delete."),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Sonarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { _, s ->
                    coVerify(exactly = 0) { s.deleteEpisodeFile(any(), any(), any()) }
                },
            ),
            Row(
                "delete failure is a hard gate",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns
                        Result.success(sonarrEpisode(fileId = 500, hasFile = true, monitored = false))
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns
                        Result.failure(ApiException.fromHttp(409, "Root folder missing"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.FAILED, "Sonarr rejected the file delete."),
                ),
                expectedComplete = false,
                postVerify = { _, s ->
                    coVerify(exactly = 0) { s.postCommand(any(), any(), any(), any(), any(), any()) }
                },
            ),
            Row(
                "verify failure IS a hard gate on Sonarr",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    // Both lookups see hasFile=true, so the verify re-check fails.
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } returns
                        Result.success(sonarrEpisode(fileId = 500, hasFile = true, monitored = false))
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.FAILED, "Sonarr still reports a file present."),
                ),
                expectedComplete = false,
                postVerify = { _, s ->
                    coVerify(exactly = 0) { s.monitorEpisodes(any(), any(), any(), any()) }
                    coVerify(exactly = 0) { s.postCommand(any(), any(), any(), any(), any(), any()) }
                },
            ),
            Row(
                "already monitored skips the monitor step",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    stubSonarrLookupThenVerified(s, sonarrEpisode(fileId = 500, hasFile = true, monitored = true))
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
                    coEvery { s.postCommand(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "EpisodeSearch", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SKIPPED, "Already monitored."),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Sonarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { _, s ->
                    coVerify(exactly = 0) { s.monitorEpisodes(any(), any(), any(), any()) }
                },
            ),
            Row(
                "verify re-query returning null surfaces WARNING and continues",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    var calls = 0
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } answers {
                        calls++
                        Result.success(if (calls == 1) sonarrEpisode(fileId = 500, hasFile = true, monitored = true) else null)
                    }
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
                    coEvery { s.postCommand(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "EpisodeSearch", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(
                        ArrRedownloadStep.VERIFY_DELETED,
                        ArrRedownloadStepStatus.WARNING,
                        "Sonarr no longer reports the episode after delete (it may have been removed); cannot confirm file status.",
                    ),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SKIPPED, "Already monitored."),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Sonarr is searching for a new download."),
                ),
                expectedComplete = true,
            ),
            Row(
                "verify re-query failing surfaces WARNING and continues",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    var calls = 0
                    coEvery { s.getEpisodeInfo(any(), any(), any(), any(), any()) } answers {
                        calls++
                        if (calls == 1) {
                            Result.success(sonarrEpisode(fileId = 500, hasFile = true, monitored = false))
                        } else {
                            Result.failure(RuntimeException("flaky"))
                        }
                    }
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
                    coEvery { s.monitorEpisodes("https://s1.local", "k", listOf(7), true) } returns Result.success(Unit)
                    coEvery { s.postCommand(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "EpisodeSearch", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(
                        ArrRedownloadStep.VERIFY_DELETED,
                        ArrRedownloadStepStatus.WARNING,
                        "Couldn't re-query Sonarr to confirm deletion (flaky); the delete command did return success.",
                    ),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Sonarr is searching for a new download."),
                ),
                expectedComplete = true,
            ),
            Row(
                "full success runs all four steps",
                arrange = { _, s ->
                    stubSonarrSeriesLookup(s)
                    stubSonarrLookupThenVerified(s, sonarrEpisode(fileId = 500, hasFile = true, monitored = false))
                    coEvery { s.deleteEpisodeFile("https://s1.local", "k", 500) } returns Result.success(Unit)
                    coEvery { s.monitorEpisodes("https://s1.local", "k", listOf(7), true) } returns Result.success(Unit)
                    coEvery { s.postCommand(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(ArrCommand(1, "EpisodeSearch", "queued"))
                },
                expectedSteps = listOf(
                    step(ArrRedownloadStep.DELETE_FILE, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.VERIFY_DELETED, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.MONITOR, ArrRedownloadStepStatus.SUCCESS, null),
                    step(ArrRedownloadStep.SEARCH, ArrRedownloadStepStatus.SUCCESS, "Sonarr is searching for a new download."),
                ),
                expectedComplete = true,
                postVerify = { _, s ->
                    coVerify {
                        s.deleteEpisodeFile("https://s1.local", "k", 500)
                        s.postCommand(
                            "https://s1.local", "k", ArrCommandName.SEARCH_EPISODES,
                            seriesId = null, episodeIds = listOf(7),
                        )
                    }
                },
            ),
        )
        runLadder(rows, sonarrSrv, sonarrPath = true)
    }
}
