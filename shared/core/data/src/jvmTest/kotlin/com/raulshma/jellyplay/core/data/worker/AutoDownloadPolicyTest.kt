package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.ArrAutoArmSync
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.AutoDownloadSweepResult
import com.raulshma.jellyplay.core.data.repository.DownloadCoverage
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.PersistedIdentity
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.resolveAgainst
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
import kotlin.test.assertTrue

/**
 * The retention-policy suites for [AutoDownloadCheck.checkOnce]: the lookahead
 * window (boundary cases — gapped episode numbering, a mid-season start, the
 * watched-episode anchor, the 0 = legacy escape), the ADR-0015 candidate
 * hygiene (played/virtual episodes are never candidates in any mode, and
 * [resolveAgainst] folds FOLLOW_GLOBAL arms and missing rows into the global
 * mode), the global max-per-pass budget, the per-server allow-list gate, and
 * the leading retention-sweep step. The scheduling shells (Android worker /
 * desktop loop) are pinned by their own suites; everything here drives the
 * shared check directly.
 */
class AutoDownloadPolicyTest {

    private val episodeCatalogue: EpisodeCatalogue = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val policyStore: SeriesDownloadPolicyStore = mockk()
    private val downloadIntake: DownloadIntake = mockk()
    private val downloadsStore: DownloadsStore = mockk()
    private val serverIdentityStore: ServerIdentityStore = mockk()
    /** The arr auto-arm stage mock — no-op (its gates are pinned by its own suite). */
    private val arrAutoArmSync: ArrAutoArmSync = mockk()

    /** Slice override per test — defaults to the gate-on legacy shape. */
    private var slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0)

    @BeforeTest
    fun setup() {
        // The pass reads disk truth via readDownloadsOrNull (the eagerly-shared
        // projection is the observer surface only — see checkOnce's comment);
        // both surfaces answer the per-test slice field.
        every { downloadsStore.downloads } answers { MutableStateFlow(slice) }
        coEvery { downloadsStore.readDownloadsOrNull() } answers { slice }
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } returns AutoDownloadSweepResult.EMPTY
        coEvery { arrAutoArmSync.sync(any()) } just runs
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1")))
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns emptyMap()
        coEvery { policyStore.seriesPolicies(any()) } returns emptyMap()
        coEvery { policyStore.armedSeriesIds() } returns emptyList()
        coEvery { policyStore.recordAutoDownloadSuggestions(any()) } returns 0
        coEvery { downloadIntake.startSeries(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { serverIdentityStore.readIdentity() } returns PersistedIdentity.ServerSelected("srv-1")
    }

    private fun check() = AutoDownloadCheck(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        policyStore = policyStore,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        arrAutoArmSync = arrAutoArmSync,
        serverIdentityStore = serverIdentityStore,
    )

    private fun episodeInSeason(
        id: String,
        seasonId: String,
        number: Int?,
        watched: Boolean = false,
        seriesId: String = "s1",
        virtual: Boolean = false,
    ) = MediaItem(
        id = id,
        name = "Episode $number",
        mediaType = MediaType.EPISODE,
        seriesId = seriesId,
        seasonId = seasonId,
        seasonNumber = 1,
        episodeNumber = number,
        isPlayed = watched,
        isVirtual = virtual,
    )

    private fun givenSnapshot(vararg episodes: MediaItem) {
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(season("season-1")),
                episodesBySeason = mapOf("season-1" to episodes.toList()),
                fetchedSeasonIds = setOf("season-1"),
                sortedEpisodes = episodes.toList(),
                epoch = 0L,
            ),
        )
    }

    // ── Lookahead ─────────────────────────────────────────────────────

    @Test
    fun `lookahead takes only the episodes after the downloaded anchor`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 2)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-2"))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-3", "ep-4")))
        }
    }

    @Test
    fun `lookahead resolves across a gap in episode numbers by playback position`() = runTest {
        // Numbering jumps 5 → 10 → 11: the anchor is positional (10), so the
        // window is the next two episodes in order (11 and the unnumbered
        // tail), never a numeric +N arithmetic.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 2)
        givenSnapshot(
            episodeInSeason("ep-5", "season-1", 5),
            episodeInSeason("ep-10", "season-1", 10),
            episodeInSeason("ep-11", "season-1", 11),
            episodeInSeason("ep-special", "season-1", null),
        )
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-10"))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-11", "ep-special")))
        }
    }

    @Test
    fun `mid-season start anchors on the first downloaded episode - earlier episodes are never fetched`() = runTest {
        // The user started downloading from the middle of the season: the
        // window opens after their anchor, the earlier episodes stay untouched.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 3)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
            episodeInSeason("ep-6", "season-1", 6),
        )
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-3"))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-4", "ep-5", "ep-6")))
        }
    }

    @Test
    fun `a watched episode anchors the window even when not downloaded`() = runTest {
        // Watched set comes from the same played-state surface the catalogue
        // snapshot carries (the server userData flag PlayedStateSync reads):
        // ep-3 was watched online, so the window opens at ep-4 despite nothing
        // being downloaded in this season.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 2)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3, watched = true),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-4", "ep-5")))
        }
    }

    @Test
    fun `a season with no downloaded and no watched episode falls back to the season head`() = runTest {
        // ADR-0016's lookahead amendment: an arr- or picker-armed series the
        // user never engaged with must start at episode 1 — the window opens
        // at the season head instead of enqueuing nothing.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 3)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1", "ep-2", "ep-3")))
        }
    }

    @Test
    fun `the season-head fallback skips virtual episodes at the head`() = runTest {
        // The no-anchor fallback applies the same decision-6 hygiene filter:
        // virtual head episodes never spend the window (a watched head
        // episode would be an anchor, not a fallback).
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 2)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1, virtual = true),
            episodeInSeason("ep-2", "season-1", 2, virtual = true),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-3", "ep-4")))
        }
    }

    @Test
    fun `lookahead zero restores the legacy take-them-all behavior`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1", "ep-2", "ep-3")))
        }
    }

    @Test
    fun `an anchor at the season end opens no window`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 3)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
        )
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-2"))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    // ── Candidate hygiene (ADR-0015 decision 6) ───────────────────────

    @Test
    fun `legacy take-all never picks played episodes`() = runTest {
        // An armed, fully-watched, fully-deleted series must not re-download
        // its history: the legacy take-all arm now filters played episodes
        // exactly like the lookahead ladder always did.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1, watched = true),
            episodeInSeason("ep-2", "season-1", 2, watched = true),
            episodeInSeason("ep-3", "season-1", 3),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-3")))
        }
    }

    @Test
    fun `legacy take-all never picks virtual or unaired episodes`() = runTest {
        // The client-side guard decouples download behavior from the
        // show_missing_episodes display pref (the gh-225 resource complaint).
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-missing", "season-1", 2, virtual = true),
            episodeInSeason("ep-3", "season-1", 3),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1", "ep-3")))
        }
    }

    @Test
    fun `lookahead skips played and virtual episodes without spending the window on them`() = runTest {
        // The anchor still counts played episodes (ep-2 watched), but the
        // window AFTER it is filled by real candidates only: the played ep-2
        // would be the anchor anyway and the virtual ep-3 is skipped, so the
        // 2-episode window lands on ep-4 and ep-5.
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 2)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2, watched = true),
            episodeInSeason("ep-3", "season-1", 3, virtual = true),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-4", "ep-5")))
        }
    }

    // ── Policy resolution (ADR-0015's resolveAgainst) ─────────────────

    @Test
    fun `resolveAgainst folds the sentinel and missing rows into the global mode`() {
        assertEquals(AutoDownloadMode.INSTANT, null.resolveAgainst(AutoDownloadMode.INSTANT))
        assertEquals(AutoDownloadMode.ASK, AutoDownloadMode.FOLLOW_GLOBAL.resolveAgainst(AutoDownloadMode.ASK))
        // An explicit row wins over any global mode — the sentinel folds, the
        // override does not.
        assertEquals(AutoDownloadMode.NEVER, AutoDownloadMode.NEVER.resolveAgainst(AutoDownloadMode.ASK))
        assertEquals(AutoDownloadMode.INSTANT, AutoDownloadMode.INSTANT.resolveAgainst(AutoDownloadMode.NEVER))
    }

    // ── Max-per-pass ──────────────────────────────────────────────────

    @Test
    fun `max-per-pass truncates the season batch and stops the pass`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0, autoDownloadMaxPerPass = 3)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
            episodeInSeason("ep-5", "season-1", 5),
        )
        // A second series behind the first — the spent budget must stop the
        // pass before it (remaining series next pass).
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1", "s2")))

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(AutoDownloadCheck.Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1", "ep-2", "ep-3")))
        }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes("s2", any()) }
    }

    @Test
    fun `max-per-pass carries the remaining budget across series`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0, autoDownloadMaxPerPass = 3)
        coEvery { downloadRepository.downloadCoverage() } returns
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1", "s2")))
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(season("season-1")),
                episodesBySeason = mapOf("season-1" to listOf(episodeInSeason("a-1", "season-1", 1), episodeInSeason("a-2", "season-1", 2))),
                fetchedSeasonIds = setOf("season-1"),
                sortedEpisodes = emptyList(),
                epoch = 0L,
            ),
        )
        coEvery { episodeCatalogue.loadSeriesEpisodes("s2", any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s2",
                seasons = listOf(season("s2-season-1")),
                episodesBySeason = mapOf("s2-season-1" to listOf(episodeInSeason("b-1", "s2-season-1", 1, seriesId = "s2"))),
                fetchedSeasonIds = setOf("s2-season-1"),
                sortedEpisodes = emptyList(),
                epoch = 0L,
            ),
        )

        check().checkOnce(attempt = 0)

        // s1 spends 2 of 3; s2 gets the remaining 1.
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("a-1", "a-2")))
        }
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s2", episodeIds = mapOf("s2-season-1" to listOf("b-1")))
        }
    }

    @Test
    fun `max-per-pass zero is unlimited`() = runTest {
        slice = DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT, autoDownloadLookahead = 0, autoDownloadMaxPerPass = 0)
        givenSnapshot(
            episodeInSeason("ep-1", "season-1", 1),
            episodeInSeason("ep-2", "season-1", 2),
            episodeInSeason("ep-3", "season-1", 3),
            episodeInSeason("ep-4", "season-1", 4),
        )

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1", "ep-2", "ep-3", "ep-4")))
        }
    }

    // ── Per-server allow-list ─────────────────────────────────────────

    @Test
    fun `a non-allow-listed active server no-ops the pass`() = runTest {
        slice = DownloadsSlice(
            autoDownloadMode = AutoDownloadMode.INSTANT,
            autoDownloadLookahead = 0,
            autoDownloadServers = setOf("srv-other"),
        )

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(AutoDownloadCheck.Outcome.Complete(), outcome)
        coVerify(exactly = 0) { downloadRepository.sweepExpiredAutoDownloads() }
        coVerify(exactly = 0) { downloadRepository.downloadCoverage() }
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    @Test
    fun `an allow-listed active server runs the pass`() = runTest {
        slice = DownloadsSlice(
            autoDownloadMode = AutoDownloadMode.INSTANT,
            autoDownloadLookahead = 0,
            autoDownloadServers = setOf("srv-1"),
        )
        givenSnapshot(episodeInSeason("ep-1", "season-1", 1))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1")))
        }
    }

    @Test
    fun `an empty allow-list means all servers`() = runTest {
        coEvery { serverIdentityStore.readIdentity() } returns PersistedIdentity.NoSession
        givenSnapshot(episodeInSeason("ep-1", "season-1", 1))

        check().checkOnce(attempt = 0)

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1")))
        }
    }

    // ── Retention sweep step ──────────────────────────────────────────

    @Test
    fun `the retention sweep runs before the enqueue pass`() = runTest {
        val callOrder = mutableListOf<String>()
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } coAnswers {
            callOrder += "sweep"
            AutoDownloadSweepResult(deletedCount = 2, bytesReclaimed = 4_000L)
        }
        coEvery { downloadRepository.downloadCoverage() } coAnswers {
            callOrder += "seriesQuery"
            flowOf(DownloadCoverage(completedItemIds = emptySet(), seriesIds = setOf("s1")))
        }
        givenSnapshot(episodeInSeason("ep-1", "season-1", 1))

        check().checkOnce(attempt = 0)

        assertEquals(listOf("sweep", "seriesQuery"), callOrder.take(2), "the sweep must run at the start of the pass")
        assertTrue(callOrder.contains("seriesQuery"))
    }

    @Test
    fun `a failing sweep never blocks the enqueue pass`() = runTest {
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } throws RuntimeException("disk gone")
        givenSnapshot(episodeInSeason("ep-1", "season-1", 1))

        val outcome = check().checkOnce(attempt = 0)

        assertEquals(AutoDownloadCheck.Outcome.Complete(), outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-1")))
        }
    }
}
