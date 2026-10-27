package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.AutoDownloadSweepResult
import com.raulshma.jellyplay.core.data.repository.DownloadCoverage
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.worker.AutoDownloadCheck.Outcome
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.AutoDownloadSuggestion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [AutoDownloadCheck] retry-budget boundary and the `isStopped`
 * mid-pass abort directly (the adapters pin their mappings around it: the
 * Android worker suite maps [Outcome] onto WorkManager results, the desktop
 * suite pins the in-process ladder). The gate-off / happy-path /
 * per-season-intake choreography is double-covered through those adapters.
 * The retention-policy suites (lookahead window, max-per-pass, allow-list
 * gating, the leading sweep step) live in [AutoDownloadPolicyTest]; the
 * gh-181 per-series policy + Ask-mode branch is pinned here.
 */
class AutoDownloadCheckTest {

    private val episodeCatalogue: EpisodeCatalogue = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val policyStore: SeriesDownloadPolicyStore = mockk()
    private val downloadIntake: DownloadIntake = mockk()
    private val downloadsStore: DownloadsStore = mockk()
    private val serverIdentityStore: ServerIdentityStore = mockk()

    @BeforeTest
    fun setup() {
        // The legacy-behavior slice (lookahead 0 = take every missing episode)
        // — the retention-policy bounds have their own suites.
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0),
        )
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } returns AutoDownloadSweepResult.EMPTY
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1")))
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns emptyMap()
        coEvery { policyStore.seriesPolicies(any()) } returns emptyMap()
        coEvery { policyStore.recordAutoDownloadSuggestions(any()) } returns 0
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(snapshot())
        coEvery { downloadIntake.startSeries(any(), any(), any()) } returns Result.success(emptyList())
    }

    private fun snapshot() = EpisodeCatalogueSnapshot(
        seriesId = "s1",
        seasons = listOf(season("season-1"), season("season-2")),
        episodesBySeason = mapOf(
            "season-1" to listOf(episode("ep-new-1", "season-1")),
            "season-2" to listOf(episode("ep-new-2", "season-2")),
        ),
        fetchedSeasonIds = setOf("season-1", "season-2"),
        sortedEpisodes = listOf(episode("ep-new-1", "season-1"), episode("ep-new-2", "season-2")),
        epoch = 0L,
    )

    private fun check(isStopped: () -> Boolean = { false }) = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        policyStore = policyStore,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        serverIdentityStore = serverIdentityStore,
        isStopped = isStopped,
    )

    // ── Retry-budget boundary ─────────────────────────────────────────

    @Test
    fun `transient failure under the budget asks for another pass`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        val outcome = check().checkOnce(attempt = AutoDownloadCheck.MAX_RETRIES - 1)

        assertEquals(Outcome.RetriesPending, outcome)
    }

    @Test
    fun `transient failure at the budget cap gives up`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        val outcome = check().checkOnce(attempt = AutoDownloadCheck.MAX_RETRIES)

        assertEquals(Outcome.Exhausted, outcome)
    }

    @Test
    fun `a clean pass completes regardless of the attempt counter`() = runTest {
        val outcome = check().checkOnce(attempt = AutoDownloadCheck.MAX_RETRIES + 5)

        assertEquals(Outcome.Complete(), outcome)
    }

    // ── Mid-pass abort (the isStopped seam) ───────────────────────────

    @Test
    fun `isStopped aborts mid-pass — the season and series after the flip are skipped`() = runTest {
        var stop = false
        // The flip happens mid-pass: once season-1's intake has started, the
        // caller is stopped. Season-2 and the next series must not run.
        coEvery { downloadIntake.startSeries("s1", any(), any()) } coAnswers {
            stop = true
            Result.success(emptyList())
        }
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1", "s2")))

        val outcome = check(isStopped = { stop }).checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
        coVerify(exactly = 1) { downloadIntake.startSeries(any(), any(), any()) }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes("s2", any()) }
    }

    // ── gh-181: per-series policy + Ask-mode branch ───────────────────

    @Test
    fun `a series with a NEVER override is skipped even when the global mode is INSTANT`() = runTest {
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns mapOf("s1" to AutoDownloadMode.NEVER)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any(), any()) }
    }

    @Test
    fun `a series with an INSTANT override enqueues even when the global mode is NEVER`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER, autoDownloadLookahead = 0),
        )
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns mapOf("s1" to AutoDownloadMode.INSTANT)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-2" to listOf("ep-new-2")))
        }
    }

    @Test
    fun `ASK mode records suggestions instead of enqueueing`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.ASK, autoDownloadLookahead = 0),
        )
        var recorded: List<AutoDownloadSuggestion> = emptyList()
        coEvery { policyStore.recordAutoDownloadSuggestions(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            recorded = firstArg()
            2
        }

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(newSuggestions = 2), outcome)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any(), any()) }
        assertEquals(setOf("ep-new-1", "ep-new-2"), recorded.map { it.episodeId }.toSet())
        assertEquals("s1", recorded.first().seriesId)
    }

    @Test
    fun `per-series ASK override records prompts while other series enqueue`() = runTest {
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1", "s2")))
        coEvery { policyStore.seriesPolicies(listOf("s1", "s2")) } returns
            mapOf("s1" to AutoDownloadMode.ASK)
        coEvery { episodeCatalogue.loadSeriesEpisodes("s2", any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s2",
                seasons = listOf(season("s2-season-1")),
                episodesBySeason = mapOf("s2-season-1" to listOf(episode("s2-ep-1", "s2-season-1"))),
                fetchedSeasonIds = setOf("s2-season-1"),
                sortedEpisodes = listOf(episode("s2-ep-1", "s2-season-1")),
                epoch = 0L,
            ),
        )

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(newSuggestions = 0), outcome)
        coVerify(exactly = 0) { downloadIntake.startSeries("s1", any(), any()) }
        coVerify(exactly = 1) { downloadIntake.startSeries("s2", any(), any()) }
        coVerify(exactly = 1) { policyStore.recordAutoDownloadSuggestions(any()) }
    }
}
