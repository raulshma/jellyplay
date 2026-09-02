package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Direct [SeriesDownloadStateHolder] tests — this holder had ZERO coverage
 * while its twin ([SeriesDeleteStateHolder]) had a full suite, despite owning
 * the trickiest concurrency on the home screen: the open-time load is
 * cancelled/replaced per request, and the LAZY per-season expansion jobs are
 * deliberately NOT tracked, so they can outlive a dismiss and overlap a
 * freshly opened sheet for a different series. The series-match guard there
 * is the invariant under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDownloadStateHolderTest {

    private lateinit var episodeCatalogue: EpisodeCatalogue
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var userMessageBus: UserMessageBus
    private var holderScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        episodeCatalogue = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)
    }

    @AfterTest
    fun stopHolder() {
        holderScope?.cancel()
    }

    private fun TestScope.buildHolder(): SeriesDownloadStateHolder {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        holderScope = scope
        return SeriesDownloadStateHolder(
            scope = scope,
            episodeCatalogue = episodeCatalogue,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
            userMessageBus = userMessageBus,
        )
    }

    private fun snapshot(seriesId: String, episodesBySeason: Map<String, List<MediaItem>>) =
        EpisodeCatalogueSnapshot(
            seriesId = seriesId,
            seasons = episodesBySeason.keys.map { season(it) },
            episodesBySeason = episodesBySeason,
            fetchedSeasonIds = episodesBySeason.keys,
            sortedEpisodes = episodesBySeason.values.flatten(),
            epoch = 1L,
        )

    private fun season(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.SEASON)
    private fun episode(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.EPISODE)
    private fun series(id: String = "s1") = MediaItem(id = id, name = "Series", mediaType = MediaType.SERIES)

    private fun stubLoad(seriesId: String, episodesBySeason: Map<String, List<MediaItem>>) {
        coEvery {
            episodeCatalogue.loadSeriesEpisodes(seriesId)
        } returns Result.success(snapshot(seriesId, episodesBySeason))
    }

    @Test
    fun requestSeriesDownload_raisesLoadingSentinel_thenPublishesSnapshotAndDownloadedIds() = runTest {
        val holder = buildHolder()
        coEvery { downloadRepository.getDownloadedEpisodeIdsForSeries("s1") } returns setOf("e1", "e2")
        // Load resolves only after runCurrent — the sentinel window is real.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1") } coAnswers {
            gate.await()
            Result.success(snapshot("s1", mapOf("season1" to listOf(episode("e1")))))
        }

        holder.requestSeriesDownload(series())
        runCurrent()

        val sentinel = holder.state.value!!
        assertEquals("s1", sentinel.seriesId)
        // The series id itself rides loadingSeasons (never collides with a
        // season id) so the sheet opens with its spinner immediately.
        assertEquals(setOf("s1"), sentinel.loadingSeasons)
        assertTrue(sentinel.episodesBySeason.isEmpty())

        gate.complete(Unit)
        runCurrent()

        val loaded = holder.state.value!!
        assertEquals(listOf("season1"), loaded.seasons.map { it.id })
        assertEquals(mapOf("season1" to listOf(episode("e1"))), loaded.episodesBySeason)
        assertEquals(setOf("e1", "e2"), loaded.downloadedEpisodeIds)
        assertTrue(loaded.loadingSeasons.isEmpty())
    }

    @Test
    fun dismiss_cancelsInFlightLoad_soItCannotRepublishState() = runTest {
        val holder = buildHolder()
        holder.requestSeriesDownload(series())
        holder.dismiss()
        assertNull(holder.state.value)

        advanceUntilIdle()

        // The cancelled load must not reopen the sheet with loaded state.
        assertNull(holder.state.value)
    }

    @Test
    fun requestSeriesDownload_cancelsPreviousSeriesLoad() = runTest {
        // Opening series A, dismissing, then quickly opening B: A's slower
        // load must die, not publish last and render B's sheet with A's data.
        stubLoad("s1", mapOf("season1" to listOf(episode("a1"))))
        stubLoad("s2", mapOf("season2" to listOf(episode("b1"))))
        val holder = buildHolder()

        holder.requestSeriesDownload(series())                  // A's load queued
        holder.requestSeriesDownload(series().copy(id = "s2"))  // cancels A, queues B
        advanceUntilIdle()

        val state = holder.state.value!!
        assertEquals("s2", state.seriesId)
        assertEquals(listOf("b1"), state.episodesBySeason["season2"]!!.map { it.id })
    }

    @Test
    fun requestSeriesDownload_failedLoad_closesSheetInsteadOfStuckSpinner() = runTest {
        coEvery {
            episodeCatalogue.loadSeriesEpisodes("s1")
        } returns Result.failure(RuntimeException("offline"))
        val holder = buildHolder()

        holder.requestSeriesDownload(series())
        advanceUntilIdle()

        // No error field on the sheet state: a failed load must close it, not
        // wedge the spinner, and surface the failure on the message bus.
        assertNull(holder.state.value)
        verify(exactly = 1) { userMessageBus.error(any<com.raulshma.jellyplay.core.ui.message.UiText>()) }
    }

    @Test
    fun loadSeasonEpisodes_alreadyLoadedSeason_isNoOp() = runTest {
        stubLoad("s1", mapOf("season1" to listOf(episode("e1"))))
        val holder = buildHolder()
        holder.requestSeriesDownload(series())
        advanceUntilIdle()

        holder.loadSeasonEpisodes("season1")
        advanceUntilIdle()

        coVerify(exactly = 0) { episodeCatalogue.loadSeasonEpisodes(any(), any()) }
    }

    /**
     * THE pin: the lazy season-expansion job is NOT tracked by the open-time
     * loadJob — it can outlive a dismiss and overlap a freshly opened sheet
     * for a DIFFERENT series. Its late result must be dropped for that other
     * series (the series-match guard), never merged into the new sheet.
     */
    @Test
    fun loadSeasonEpisodes_resultForOldSeries_doesNotLeakIntoNewSeriesSheet() = runTest {
        // Series A's open snapshot never completes on its own (gate held).
        val gateA = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { episodeCatalogue.loadSeriesEpisodes("s1") } coAnswers {
            gateA.await()
            Result.success(snapshot("s1", mapOf("seasonA" to listOf(episode("a1")))))
        }
        stubLoad("s2", mapOf("seasonB" to listOf(episode("b1"))))
        // Series A's lazy expansion completes after the user moved to series B.
        coEvery {
            episodeCatalogue.loadSeasonEpisodes("s1", "seasonA")
        } coAnswers {
            kotlinx.coroutines.delay(10_000L)
            Result.success(listOf(episode("a1"), episode("a2")))
        }
        val holder = buildHolder()

        holder.requestSeriesDownload(series())              // A opens (sentinel only)
        holder.loadSeasonEpisodes("seasonA")                // A's lazy expansion queued
        holder.requestSeriesDownload(series().copy(id = "s2")) // user moved to B
        advanceUntilIdle()
        gateA.complete(Unit)                                // A's open load lands late
        runCurrent()

        val state = holder.state.value!!
        assertEquals("s2", state.seriesId)
        assertEquals(setOf("seasonB"), state.episodesBySeason.keys)
        // The stale expansion for A must not have touched B's sheet, and must
        // not have re-published A's dismissed sheet either.
        assertNull(holder.state.value!!.episodesBySeason["seasonA"])
        assertTrue(state.loadingSeasons.isEmpty())
    }

    @Test
    fun loadSeasonEpisodes_failure_clearsOnlyThatSeasonsSpinner() = runTest {
        stubLoad("s1", mapOf("season1" to listOf(episode("e1"))))
        coEvery {
            episodeCatalogue.loadSeasonEpisodes("s1", "season2")
        } returns Result.failure(RuntimeException("offline"))
        val holder = buildHolder()
        holder.requestSeriesDownload(series())
        advanceUntilIdle()

        holder.loadSeasonEpisodes("season2")
        advanceUntilIdle()

        val state = holder.state.value!!
        // The sheet stays open with what it has; only the spinner flag clears.
        assertTrue(state.loadingSeasons.isEmpty())
        assertEquals(setOf("season1"), state.episodesBySeason.keys)
    }

    @Test
    fun downloadSeries_dismissesImmediately_andQueuesSelectionInBackground() = runTest {
        stubLoad("s1", mapOf("season1" to listOf(episode("e1"))))
        coEvery { downloadIntake.startSeries("s1", mapOf("season1" to listOf("e1"))) } returns
            Result.success(listOf("e1"))
        val holder = buildHolder()
        holder.requestSeriesDownload(series())
        advanceUntilIdle()

        holder.downloadSeries(mapOf("season1" to listOf("e1")))

        // The sheet dismisses immediately; the queueing runs in background.
        assertNull(holder.state.value)
        advanceUntilIdle()
        coVerify(exactly = 1) { downloadIntake.startSeries("s1", mapOf("season1" to listOf("e1"))) }
        verify(exactly = 1) { userMessageBus.info(any<com.raulshma.jellyplay.core.ui.message.UiText>()) }
    }

    @Test
    fun downloadSeries_emptySelection_isNoOp() = runTest {
        stubLoad("s1", mapOf("season1" to listOf(episode("e1"))))
        val holder = buildHolder()
        holder.requestSeriesDownload(series())
        advanceUntilIdle()

        holder.downloadSeries(mapOf("season1" to emptyList()))

        // Nothing selected: the sheet stays open, nothing is queued.
        assertTrue(holder.state.value != null)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    @Test
    fun downloadSeries_withoutOpenSheet_isNoOp() = runTest {
        val holder = buildHolder()

        holder.downloadSeries(mapOf("season1" to listOf("e1")))
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }
}
