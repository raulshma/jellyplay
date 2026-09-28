package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.AutoDownloadSweepResult
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The [DesktopAutoDownloadScheduler]'s first behavioural suite: pins the
 * scheduling shell around the shared [AutoDownloadCheck] — the prefs gate,
 * the per-season intake of a gate-on pass, the in-process retry ladder
 * (attempt counts from 0 like the Android `runAttemptCount`, backoff-spaced,
 * giving up at [AutoDownloadCheck.MAX_RETRIES]), and mid-pass cancellation
 * via the check's `isStopped` seam. The check choreography itself is pinned
 * by [AutoDownloadCheckTest] and the Android worker suite; this suite pins
 * that the shell feeds it a live cancellation seam and honours its outcomes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAutoDownloadSchedulerTest {

    private val episodeCatalogue: EpisodeCatalogue = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val downloadIntake: DownloadIntake = mockk()
    private val downloadsStore: DownloadsStore = mockk()
    private val serverIdentityStore: ServerIdentityStore = mockk()

    @BeforeTest
    fun setup() {
        // Legacy-behavior slice (lookahead 0 = take every missing episode); the
        // lookahead-enabled desktop-parity case below overrides the slice.
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(autoDownloadNewEpisodes = true, autoDownloadLookahead = 0),
        )
        coEvery { downloadRepository.sweepExpiredAutoDownloads() } returns AutoDownloadSweepResult.EMPTY
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns listOf("s1")
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-old"))
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(snapshot())
        coEvery { downloadIntake.startSeries(any(), any()) } returns Result.success(emptyList())
    }

    private fun snapshot(
        seasons: List<MediaItem> = listOf(season("season-1"), season("season-2")),
        episodesBySeason: Map<String, List<MediaItem>> = mapOf(
            "season-1" to listOf(episode("ep-old", "season-1"), episode("ep-new-1", "season-1")),
            "season-2" to listOf(episode("ep-new-2", "season-2")),
        ),
    ) = EpisodeCatalogueSnapshot(
        seriesId = "s1",
        seasons = seasons,
        episodesBySeason = episodesBySeason,
        fetchedSeasonIds = episodesBySeason.keys,
        sortedEpisodes = episodesBySeason.values.flatten(),
        epoch = 0L,
    )

    private fun numberedEpisode(id: String, seasonId: String, number: Int) = MediaItem(
        id = id,
        name = "Episode $number",
        mediaType = MediaType.EPISODE,
        seriesId = "s1",
        seasonId = seasonId,
        seasonNumber = 1,
        episodeNumber = number,
    )

    private fun TestScope.buildScheduler(): DesktopAutoDownloadScheduler = DesktopAutoDownloadScheduler(
        downloadsStore = downloadsStore,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
        serverIdentityStore = serverIdentityStore,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    // ── Prefs gate ────────────────────────────────────────────────────

    @Test
    fun `auto-download disabled fetches nothing`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = false))
        val scheduler = buildScheduler()

        scheduler.start()
        testScheduler.runCurrent()
        scheduler.stop()

        coVerify(exactly = 0) { downloadRepository.getDownloadedSeriesIds() }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes(any(), any()) }
    }

    // ── Gate-on pass: per-season intake ───────────────────────────────

    @Test
    fun `gate-on pass starts per-season intake of new episodes`() = runTest {
        val scheduler = buildScheduler()

        scheduler.start()
        testScheduler.runCurrent()
        scheduler.stop()

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-2" to listOf("ep-new-2")))
        }
        coVerify(exactly = 2) { downloadIntake.startSeries(any(), any()) }
    }

    // ── Desktop parity: the retention policy rides the shared check ───

    @Test
    fun `lookahead policy flows through the desktop shell`() = runTest {
        // The desktop loop constructs the same AutoDownloadCheck, so a
        // non-zero lookahead bounds the pass here exactly as on Android —
        // one episode past the downloaded anchor (ep-1) instead of every
        // missing one. Episodes carry explicit numbers because the playback
        // order falls back to name comparison when they are unset.
        every { downloadsStore.downloads } returns MutableStateFlow(
            DownloadsSlice(autoDownloadNewEpisodes = true, autoDownloadLookahead = 1),
        )
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-1"))
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(
            EpisodeCatalogueSnapshot(
                seriesId = "s1",
                seasons = listOf(season("season-1")),
                episodesBySeason = mapOf(
                    "season-1" to listOf(
                        numberedEpisode("ep-1", "season-1", 1),
                        numberedEpisode("ep-2", "season-1", 2),
                        numberedEpisode("ep-3", "season-1", 3),
                    ),
                ),
                fetchedSeasonIds = setOf("season-1"),
                sortedEpisodes = emptyList(),
                epoch = 0L,
            ),
        )
        val scheduler = buildScheduler()

        scheduler.start()
        testScheduler.runCurrent()
        scheduler.stop()

        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-2")))
        }
        coVerify(exactly = 1) { downloadIntake.startSeries(any(), any()) }
    }

    // ── In-process retry ladder ───────────────────────────────────────

    @Test
    fun `transient failure consumes the retry budget across backoff-spaced passes then gives up`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))
        val scheduler = buildScheduler()

        scheduler.start()
        testScheduler.runCurrent() // pass 1 (attempt 0) fails → schedule the backoff
        // Passes 2-4 (attempts 1-3): attempt 3 is at the MAX_RETRIES cap.
        repeat(3) { testScheduler.advanceTimeBy(DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS + 1) }
        scheduler.stop()

        // Four passes total (attempts 0-3), then the ladder gives up until
        // the next periodic tick — the WorkManager retry()/failure() ladder
        // folded in-process.
        coVerify(exactly = 4) { episodeCatalogue.loadSeriesEpisodes(any(), any()) }
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    // ── Mid-pass cancellation (the isStopped seam) ────────────────────

    @Test
    fun `stop aborts the in-flight pass mid-series`() = runTest {
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns listOf("s1", "s2")
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns emptyMap()
        val s1Loaded = Channel<EpisodeCatalogueSnapshot>()
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } coAnswers {
            Result.success(s1Loaded.receive())
        }
        coEvery { episodeCatalogue.loadSeriesEpisodes("s2", any()) } returns Result.success(snapshot())
        val scheduler = buildScheduler()

        scheduler.start()
        testScheduler.runCurrent() // the pass runs up to s1's load and suspends
        s1Loaded.trySend(snapshot()) // the load lands; its resumption is queued
        scheduler.stop() // cancel while the pass is between suspension points
        testScheduler.runCurrent() // the pass resumes into a stopped job

        // The SEAM, not the suspension, stopped the pass: s1's snapshot had
        // new episodes to intake and s2 was next — neither may happen.
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes("s2", any()) }
    }
}
