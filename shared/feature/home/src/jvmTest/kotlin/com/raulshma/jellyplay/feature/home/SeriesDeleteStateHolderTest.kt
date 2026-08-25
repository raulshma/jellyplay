package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

/**
 * Direct [SeriesDeleteStateHolder] tests. This concern had ZERO coverage
 * while living on HomeViewModel (constructing the VM in a test required
 * Robolectric + the lifecycle stack); the headline case is the
 * snapshot-before-dismiss pin on [SeriesDeleteStateHolder.deleteOfflineEpisodes]
 * — the invariant that lets the sheet dismiss instantly while whole-season
 * collapse still works. Plain JUnit + [MainDispatcherRule] + MockK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDeleteStateHolderTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var offlineRepository: OfflineRepository
    private var holderScope: CoroutineScope? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        offlineRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun stopHolder() {
        holderScope?.cancel()

        Dispatchers.resetMain()    }

    private fun TestScope.buildHolder(): SeriesDeleteStateHolder {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        holderScope = scope
        return SeriesDeleteStateHolder(scope = scope, offlineRepository = offlineRepository)
    }

    private fun season(id: String) = OfflineMediaItem(id = id, name = id, mediaType = MediaType.SEASON)

    private fun episode(id: String, seasonId: String, sizeBytes: Long = 0L) =
        OfflineMediaItem(
            id = id,
            name = id,
            mediaType = MediaType.EPISODE,
            seasonId = seasonId,
            totalSizeBytes = sizeBytes,
        )

    private fun stubSeries(seasons: List<OfflineMediaItem>, episodes: List<OfflineMediaItem>) {
        every { offlineRepository.getSeasonsForSeries("s1") } returns flowOf(seasons)
        coEvery { offlineRepository.getEpisodesForSeries("s1") } returns episodes
    }

    @Test
    fun requestSeriesDelete_loadsDownloadedSeasons_andSizes() = runTest {
        stubSeries(
            seasons = listOf(season("season1"), season("season2")),
            episodes = listOf(
                episode("e1", "season1", 10L),
                episode("e2", "season1", 20L),
                episode("e3", "season2", 30L),
            ),
        )
        val holder = buildHolder()

        holder.requestSeriesDelete(series())
        runCurrent()

        val state = holder.state.value
        assertTrue(state != null && !state.isLoading)
        assertEquals(listOf("season1", "season2"), state!!.seasons.map { it.id })
        assertEquals(setOf("e1", "e2"), state.episodesBySeason["season1"]!!.map { it.id }.toSet())
        assertEquals(60L, state.totalSizeBytes)
        assertEquals(mapOf("e1" to 10L, "e2" to 20L, "e3" to 30L), state.episodeSizeBytes)
    }

    @Test
    fun requestSeriesDelete_excludesSeasonsWithNoDownloadedEpisodes() = runTest {
        stubSeries(
            seasons = listOf(season("season1"), season("season2")),
            episodes = listOf(episode("e1", "season1")),
        )
        val holder = buildHolder()

        holder.requestSeriesDelete(series())
        runCurrent()

        val state = holder.state.value!!
        assertEquals(listOf("season1"), state.seasons.map { it.id })
        assertTrue(state.episodesBySeason.keys == setOf("season1"))
    }

    @Test
    fun dismiss_clearsState() = runTest {
        stubSeries(seasons = listOf(season("season1")), episodes = listOf(episode("e1", "season1")))
        val holder = buildHolder()
        holder.requestSeriesDelete(series())
        runCurrent()

        holder.dismiss()

        assertNull(holder.state.value)
    }

    @Test
    fun dismiss_cancelsInFlightLoad_soItCannotRepublishState() = runTest {
        stubSeries(seasons = listOf(season("season1")), episodes = listOf(episode("e1", "season1")))
        val holder = buildHolder()

        holder.requestSeriesDelete(series()) // load queued on the test scheduler
        holder.dismiss()                     // dismissed before the load runs
        assertNull(holder.state.value)

        runCurrent()

        // The cancelled load must not reopen the sheet with loaded state.
        assertNull(holder.state.value)
    }

    @Test
    fun requestSeriesDelete_cancelsPreviousSeriesLoad() = runTest {
        // Opening series A, dismissing, then quickly opening B: A's slower
        // load must die with the dismiss/re-request, not publish last and
        // render B's sheet with A's data.
        every { offlineRepository.getSeasonsForSeries("s1") } returns flowOf(listOf(season("season1")))
        coEvery { offlineRepository.getEpisodesForSeries("s1") } returns listOf(episode("e1", "season1"))
        every { offlineRepository.getSeasonsForSeries("s2") } returns flowOf(listOf(season("season2")))
        coEvery { offlineRepository.getEpisodesForSeries("s2") } returns listOf(episode("e2", "season2"))
        val holder = buildHolder()

        holder.requestSeriesDelete(series())                    // A's load queued
        holder.requestSeriesDelete(series().copy(id = "s2"))    // cancels A, queues B
        runCurrent()

        val state = holder.state.value!!
        assertEquals("s2", state.seriesId)
        assertEquals(listOf("e2"), state.episodesBySeason["season2"]!!.map { it.id })
    }

    @Test
    fun requestSeriesDelete_failedLoad_closesSheetInsteadOfStuckSpinner() = runTest {
        every { offlineRepository.getSeasonsForSeries("s1") } throws RuntimeException("room broke")
        val holder = buildHolder()

        holder.requestSeriesDelete(series())
        runCurrent()

        // The state has no error field: a failed load must close the sheet,
        // not leave isLoading = true wedged on screen forever.
        assertNull(holder.state.value)
    }

    /**
     * THE pin: the sheet snapshot must be captured BEFORE dismissal. The
     * shared OfflineDeleteActions reads its providers lazily; if the holder
     * cleared state first, the providers would return empty content and a
     * whole-season selection would silently degrade to per-episode deletes.
     */
    @Test
    fun deleteOfflineEpisodes_capturesSnapshotBeforeDismiss_andCollapsesSeasons() = runTest {
        stubSeries(
            seasons = listOf(season("season1")),
            episodes = listOf(episode("e1", "season1"), episode("e2", "season1")),
        )
        val holder = buildHolder()
        holder.requestSeriesDelete(series())
        runCurrent()

        holder.deleteOfflineEpisodes(setOf("e1", "e2"))

        // The sheet is dismissed immediately (background deletes continue).
        assertNull(holder.state.value)
        advanceUntilIdle()

        // e1+e2 cover every downloaded episode of season1 → one whole-season
        // transaction, no per-episode deletes. This only holds because the
        // snapshot was captured before the dismiss above cleared the state.
        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeason("season1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem("e2") }
    }

    @Test
    fun deleteOfflineEpisodes_partialSelection_fallsBackToPerEpisodeDeletes() = runTest {
        stubSeries(
            seasons = listOf(season("season1")),
            episodes = listOf(episode("e1", "season1"), episode("e2", "season1")),
        )
        val holder = buildHolder()
        holder.requestSeriesDelete(series())
        runCurrent()

        holder.deleteOfflineEpisodes(setOf("e1"))
        assertNull(holder.state.value)
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason(any()) }
        coVerify(exactly = 1) { offlineRepository.deleteOfflineItem("e1") }
    }

    @Test
    fun deleteOfflineEpisodes_emptySelection_isNoOp() = runTest {
        stubSeries(seasons = listOf(season("season1")), episodes = listOf(episode("e1", "season1")))
        val holder = buildHolder()
        holder.requestSeriesDelete(series())
        runCurrent()

        holder.deleteOfflineEpisodes(emptySet())

        // No dismissal and no deletes for an empty selection.
        assertTrue(holder.state.value != null)
        advanceUntilIdle()
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeason(any()) }
    }

    @Test
    fun deleteOfflineEpisodes_withoutOpenSheet_isNoOp() = runTest {
        val holder = buildHolder()

        holder.deleteOfflineEpisodes(setOf("e1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }
    }

    @Test
    fun deleteOfflineSeries_dismissesSheet_andDelegates() = runTest {
        stubSeries(seasons = listOf(season("season1")), episodes = listOf(episode("e1", "season1")))
        val holder = buildHolder()
        holder.requestSeriesDelete(series())
        runCurrent()
        assertFalse(holder.state.value == null)

        holder.deleteOfflineSeries("s1")

        assertNull(holder.state.value)
        advanceUntilIdle()
        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeries("s1") }
    }

    private fun series() = MediaItem(id = "s1", name = "Series One", mediaType = MediaType.SERIES)
}
