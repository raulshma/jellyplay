package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.AutoDownloadCheck.Outcome
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
 */
class AutoDownloadCheckTest {

    private val episodeCatalogue: EpisodeCatalogue = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val downloadIntake: DownloadIntake = mockk()
    private val downloadsStore: DownloadsStore = mockk()

    @BeforeTest
    fun setup() {
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = true))
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns listOf("s1")
        coEvery { downloadRepository.getDownloadedEpisodeIdsBySeries() } returns emptyMap()
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns Result.success(snapshot())
        coEvery { downloadIntake.startSeries(any(), any()) } returns Result.success(emptyList())
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
        downloadIntake = downloadIntake,
        episodeCatalogue = episodeCatalogue,
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

        assertEquals(Outcome.Complete, outcome)
    }

    // ── Mid-pass abort (the isStopped seam) ───────────────────────────

    @Test
    fun `isStopped aborts mid-pass — the season and series after the flip are skipped`() = runTest {
        var stop = false
        // The flip happens mid-pass: once season-1's intake has started, the
        // caller is stopped. Season-2 and the next series must not run.
        coEvery { downloadIntake.startSeries("s1", any()) } coAnswers {
            stop = true
            Result.success(emptyList())
        }
        coEvery { downloadRepository.getDownloadedSeriesIds() } returns listOf("s1", "s2")

        val outcome = check(isStopped = { stop }).checkOnce(attempt = 0)

        assertEquals(Outcome.Complete, outcome)
        coVerify(exactly = 1) {
            downloadIntake.startSeries("s1", episodeIds = mapOf("season-1" to listOf("ep-new-1")))
        }
        coVerify(exactly = 1) { downloadIntake.startSeries(any(), any()) }
        coVerify(exactly = 0) { episodeCatalogue.loadSeriesEpisodes("s2", any()) }
    }
}
