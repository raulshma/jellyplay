package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.ArrAutoArmSync
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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
    /** The arr auto-arm stage mock — the setup no-ops it (gates off in the suite's slices). */
    private val arrAutoArmSync: ArrAutoArmSync = mockk()

    @BeforeTest
    fun setup() {
        // The legacy-behavior slice (lookahead 0 = take every missing episode)
        // — the retention-policy bounds have their own suites.
        givenPrefs(
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0),
        )
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } returns AutoDownloadSweepResult.EMPTY
        // The stage mock no-ops by default (its own suite pins the gates); the
        // arr-stage tests below override it.
        coEvery { arrAutoArmSync.sync(any()) } just runs
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1")))
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns emptyMap()
        coEvery { policyStore.seriesPolicies(any()) } returns emptyMap()
        coEvery { policyStore.armedSeriesIds() } returns emptyList()
        coEvery { policyStore.recordAutoDownloadSuggestions(any()) } returns 0
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(snapshot())
        coEvery { downloadIntake.startSeries(any(), any(), any()) } returns Result.success(emptyList())
    }

    /**
     * Stubs BOTH surfaces the check touches off one slice: the pass reads
     * disk truth via [DownloadsStore.readDownloadsOrNull] (the eagerly-shared
     * projection is the observer surface only — see checkOnce's comment).
     */
    private fun givenPrefs(slice: DownloadsSlice) {
        every { downloadsStore.downloads } returns MutableStateFlow(slice)
        coEvery { downloadsStore.readDownloadsOrNull() } returns slice
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
        arrAutoArmSync = arrAutoArmSync,
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
    fun `a failed prefs read fails closed instead of lifting the server restriction`() = runTest {
        // The check's read is the failure-surfacing [DownloadsStore.readDownloadsOrNull]:
        // a null there must fail the pass (retry budget), not fall through to a
        // defaults-shaped slice — the defaults' EMPTY autoDownloadServers
        // allow-list reads as "every server allowed", so the sweep and the
        // per-series intake would run for a server the user excluded.
        coEvery { downloadsStore.readDownloadsOrNull() } returns null

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.RetriesPending, outcome)
        coVerify(exactly = 0) { downloadRepository.sweepExpiredAutoDownloads() }
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any(), any()) }
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
        givenPrefs(DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER, autoDownloadLookahead = 0))
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
        givenPrefs(DownloadsSlice(autoDownloadMode = AutoDownloadMode.ASK, autoDownloadLookahead = 0))
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

    // ── ADR-0015: armed series outlive local files ────────────────────

    @Test
    fun `an armed series with zero downloads is still visited`() = runTest {
        // The gh-225 shape: watch-boundary hygiene emptied the series, but its
        // arm keeps it in the checked universe (coverage ∪ arms).
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = emptySet()))
        coEvery { policyStore.armedSeriesIds() } returns listOf("s1")
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns
            mapOf("s1" to AutoDownloadMode.FOLLOW_GLOBAL)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) { episodeCatalogue.loadSeriesEpisodes("s1", any()) }
    }

    @Test
    fun `a series in both the coverage and the armed halves of the universe is visited once`() = runTest {
        // Implicit arming gives nearly every downloaded series an arm row too,
        // so the universe union's halves overlap heavily — the dedup keeps the
        // pass from visiting s1 twice (a duplicate visit re-runs the enqueue
        // choreography and double-counts it against maxPerPass).
        coEvery { policyStore.armedSeriesIds() } returns listOf("s1")
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns
            mapOf("s1" to AutoDownloadMode.FOLLOW_GLOBAL)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
    }

    @Test
    fun `a FOLLOW_GLOBAL arm under a global NEVER is visited but skipped`() = runTest {
        givenPrefs(DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER, autoDownloadLookahead = 0))
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns
            mapOf("s1" to AutoDownloadMode.FOLLOW_GLOBAL)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        // Visited: the arm put s1 in the universe, so the per-series policy
        // read asks for exactly it — then the resolved NEVER skips the series
        // (no enqueue, no Ask prompt, no catalogue round-trip).
        coVerify(exactly = 1) { policyStore.seriesPolicies(listOf("s1")) }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes(any(), any()) }
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any(), any()) }
        coVerify(exactly = 0) { policyStore.recordAutoDownloadSuggestions(any()) }
    }

    @Test
    fun `a FOLLOW_GLOBAL arm under a global INSTANT enqueues`() = runTest {
        coEvery { policyStore.seriesPolicies(listOf("s1")) } returns
            mapOf("s1" to AutoDownloadMode.FOLLOW_GLOBAL)

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
    }

    @Test
    fun `an armed-emptied series anchors on its last watched episode and enqueues the next aired one`() = runTest {
        // The ADR-0015 resume mechanics: with zero downloaded rows, the
        // positional anchor comes from the snapshot's played flag alone — the
        // watched ep-1 anchors the window and the next aired episode (ep-2)
        // is enqueued (lookahead 1).
        givenPrefs(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 1))
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = emptySet()))
        coEvery { policyStore.armedSeriesIds() } returns listOf("s1")
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(season("season-1")),
                episodesBySeason = mapOf(
                    "season-1" to listOf(
                        episode("ep-1", "season-1").copy(isPlayed = true),
                        episode("ep-2", "season-1"),
                    ),
                ),
                fetchedSeasonIds = setOf("season-1"),
                sortedEpisodes = listOf(episode("ep-1", "season-1"), episode("ep-2", "season-1")),
                epoch = 0L,
            ),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-2")))
        }
    }

    // ── arr auto-arm stage (gh-225 follow-up) ─────────────────────────

    @Test
    fun `an arr auto-arm failure skips the stage but the pass still completes and downloads`() = runTest {
        // Sonarr is down: the stage throws, the pass's runCatching swallows
        // it, and the normal choreography (universe → catalogue → intake)
        // continues untouched.
        coEvery { arrAutoArmSync.sync(any()) } throws RuntimeException("sonarr unreachable")

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
    }

    @Test
    fun `a series the arr stage armed joins the same pass's universe`() = runTest {
        // The stage runs BEFORE the universe derivation, so a row it arms is
        // visible to this pass's armedSeriesIds() read — the pass visits the
        // freshly armed series right away (no waiting a full period).
        coEvery { arrAutoArmSync.sync(any()) } coAnswers {
            coEvery { policyStore.armedSeriesIds() } returns listOf("s1", "arr-armed")
            coEvery { policyStore.seriesPolicies(listOf("s1", "arr-armed")) } returns
                mapOf("s1" to AutoDownloadMode.INSTANT, "arr-armed" to AutoDownloadMode.FOLLOW_GLOBAL)
            coEvery { episodeCatalogue.loadSeriesEpisodes("arr-armed", any()) } returns Result.success(
                EpisodeCatalogueSnapshot(
                    seriesId = "arr-armed",
                    seasons = listOf(season("arr-season-1")),
                    episodesBySeason = mapOf(
                        "arr-season-1" to listOf(episode("arr-ep-1", "arr-season-1", seriesId = "arr-armed")),
                    ),
                    fetchedSeasonIds = setOf("arr-season-1"),
                    sortedEpisodes = listOf(episode("arr-ep-1", "arr-season-1", seriesId = "arr-armed")),
                    epoch = 0L,
                ),
            )
        }

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("arr-armed", episodeIds = mapOf("arr-season-1" to listOf("arr-ep-1")))
        }
    }

    @Test
    fun `an arr-armed series the user never engaged with starts at episode 1 the same pass`() = runTest {
        // The gh-225 core ask under the DEFAULT lookahead (3): a monitored
        // series with zero downloads and zero watched episodes has no anchor,
        // so the season-head fallback enqueues its first 3 real episodes in
        // the very pass that armed it — not nothing until a hand-download.
        givenPrefs(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 3))
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = emptySet()))
        coEvery { arrAutoArmSync.sync(any()) } coAnswers {
            coEvery { policyStore.armedSeriesIds() } returns listOf("arr-armed")
            coEvery { policyStore.seriesPolicies(listOf("arr-armed")) } returns
                mapOf("arr-armed" to AutoDownloadMode.FOLLOW_GLOBAL)
            coEvery { episodeCatalogue.loadSeriesEpisodes("arr-armed", any()) } returns Result.success(
                EpisodeCatalogueSnapshot(
                    seriesId = "arr-armed",
                    seasons = listOf(season("arr-season-1")),
                    episodesBySeason = mapOf(
                        "arr-season-1" to listOf(
                            episode("arr-ep-1", "arr-season-1", seriesId = "arr-armed"),
                            episode("arr-ep-2", "arr-season-1", seriesId = "arr-armed"),
                            episode("arr-ep-3", "arr-season-1", seriesId = "arr-armed"),
                            episode("arr-ep-4", "arr-season-1", seriesId = "arr-armed"),
                        ),
                    ),
                    fetchedSeasonIds = setOf("arr-season-1"),
                    sortedEpisodes = listOf(
                        episode("arr-ep-1", "arr-season-1", seriesId = "arr-armed"),
                        episode("arr-ep-2", "arr-season-1", seriesId = "arr-armed"),
                        episode("arr-ep-3", "arr-season-1", seriesId = "arr-armed"),
                        episode("arr-ep-4", "arr-season-1", seriesId = "arr-armed"),
                    ),
                    epoch = 0L,
                ),
            )
        }

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries(
                "arr-armed",
                episodeIds = mapOf("arr-season-1" to listOf("arr-ep-1", "arr-ep-2", "arr-ep-3")),
            )
        }
    }
}
