package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the [AutoDownloadWorker] behaviour around the [EpisodeCatalogue]
 * consolidated snapshot (seasons + episodes in one load per series):
 *
 *  - No-op successes: `autoDownloadNewEpisodes`
 *    disabled, or zero downloaded series — the repository/catalogue/intake are
 *    never touched.
 *  - An enabled run loads each downloaded series' snapshot **online**
 *    (`offline = false`), filters out already-downloaded episode ids, and
 *    starts one intake batch per season with new episodes.
 *  - A per-series catalogue failure is tolerated (that series is skipped, no
 *    intake) but escalates the run `retry` → `failure` after MAX_RETRIES (3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadWorkerTest {

    private lateinit var context: Context
    private val episodeCatalogue: EpisodeCatalogue = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val downloadIntake: DownloadIntake = mockk()
    private val downloadsStore: DownloadsStore = mockk()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = true))
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns listOf("s1")
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns mapOf("s1" to setOf("ep-old"))
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(snapshot())
        coEvery { downloadIntake.startSeries(any(), any()) } returns Result.success(emptyList())
    }

    private fun season(id: String) = MediaItem(id = id, name = "Season $id", mediaType = MediaType.SEASON)

    private fun episode(id: String, seasonId: String) = MediaItem(
        id = id,
        name = "Episode $id",
        mediaType = MediaType.EPISODE,
        seriesId = "s1",
        seasonId = seasonId,
    )

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

    private fun buildWorker(runAttemptCount: Int = 0): AutoDownloadWorker =
        TestListenableWorkerBuilder<AutoDownloadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): AutoDownloadWorker = AutoDownloadWorker(
                    appContext,
                    workerParameters,
                    episodeCatalogue,
                    downloadRepository,
                    downloadIntake,
                    downloadsStore,
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()

    // ── No-op gates ───────────────────────────────────────────────────

    @Test
    fun `auto-download disabled short-circuits success`() = runTest {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = false))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { downloadRepository.getDownloadedSeriesIds() }
    }

    @Test
    fun `no downloaded series short-circuits success without loading any catalogue`() = runTest {
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns emptyList()

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes(any(), any()) }
    }

    // ── Happy path: per-season intake of new episodes ─────────────────

    @Test
    fun `new episodes start per season and already-downloaded ones are filtered`() = runTest {
        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The consolidated online snapshot: offline defaults to false.
        coVerify(exactly = 1) { episodeCatalogue.loadSeriesEpisodes("s1", offline = false) }
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-2" to listOf("ep-new-2")))
        }
        // Exactly the two season batches — no duplicate intake for the whole-series fallback.
        coVerify(exactly = 2) { downloadIntake.startSeries(any(), any()) }
    }

    @Test
    fun `a series whose episodes are all downloaded starts no intake`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1", any()) } returns Result.success(
            snapshot(
                seasons = listOf(season("season-1")),
                episodesBySeason = mapOf(
                    "season-1" to listOf(episode("ep-old", "season-1")),
                ),
            ),
        )

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    // ── Failure escalation ────────────────────────────────────────────

    @Test
    fun `catalogue failure skips the series but escalates to retry`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        val result = buildWorker(runAttemptCount = 0).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    @Test
    fun `catalogue failure after exhausted retries returns failure`() = runTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        // MAX_RETRIES is 3: attempt 3 is past the retry budget.
        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
