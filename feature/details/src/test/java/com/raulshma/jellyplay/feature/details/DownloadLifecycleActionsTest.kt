package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DownloadLifecycleActions]. The helper owns no schedulers of
 * its own — it launches on the injected [CoroutineScope], so each test passes
 * the [runTest] [TestScope] and flushes via [advanceUntilIdle].
 *
 * Follows the [com.raulshma.jellyplay.core.data.seerr.SeerrRequestStateHolder]
 * test template: relaxed mocks for the collaborators, provider lambdas for the
 * VM-injected readers, and a capturing sink for one-shot messages.
 */
class DownloadLifecycleActionsTest {

    private val downloadIntake: DownloadIntake = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk(relaxed = true)
    private val adaptiveBitrateManager: AdaptiveBitrateManager = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * Builds an [DownloadLifecycleActions] wired to the relaxed collaborators,
     * with overridable provider lambdas. Defaults: unmetered connection, an
     * empty [DownloadsSlice] (→ ORIGINAL quality → null maxBitrate).
     */
    private fun makeActions(
        scope: CoroutineScope,
        detail: MediaDetail? = null,
        seasons: List<MediaItem> = emptyList(),
        seriesId: String? = null,
        itemId: String? = null,
        expandSeason: suspend (itemId: String, seasonId: String) -> List<MediaItem> = { _, _ -> emptyList() },
        messageSink: (DetailMessage) -> Unit = {},
        downloadsSlice: DownloadsSlice = DownloadsSlice(),
        unmetered: Boolean = true,
    ): DownloadLifecycleActions {
        every { downloadsStore.downloads } returns MutableStateFlow(downloadsSlice)
        every { adaptiveBitrateManager.isUnmeteredConnection() } returns unmetered
        return DownloadLifecycleActions(
            scope = scope,
            downloadIntake = downloadIntake,
            downloadsStore = downloadsStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            downloadRepository = downloadRepository,
            context = context,
            detailProvider = { detail },
            seasonsProvider = { seasons },
            currentSeriesIdProvider = { seriesId },
            itemIdProvider = { itemId },
            expandSeason = expandSeason,
            messageSink = messageSink,
        )
    }

    // region startDownload
    @Test
    fun `startDownload on unmetered connection flips isDownloading true then false and invokes intake`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source")),
        )
        // Gate the suspend call so we can observe the transient isDownloading=true
        // before the coroutine completes (StateFlow conflates, so a collector
        // could skip the intermediate value — reading .value at the suspend
        // point is deterministic).
        val gate = CompletableDeferred<DownloadResult>()
        coEvery { downloadIntake.start(any(), any()) } coAnswers { gate.await() }

        val h = makeActions(scope = this, detail = detail)

        h.startDownload()
        advanceUntilIdle() // runs the launch up to the suspended start() call

        assertTrue(h.state.value.isDownloading) // flipped true mid-flight

        gate.complete(DownloadResult(downloadItem = mockk(relaxed = true), error = null))
        advanceUntilIdle() // resumes the launch → isDownloading flips false

        assertFalse(h.state.value.isDownloading)
        coVerify { downloadIntake.start(detail, null) } // ORIGINAL quality → null maxBitrate
    }
    // endregion

    // region downloadSeries
    @Test
    fun `downloadSeries success emits SeriesDownload with queued count`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "series-1", name = "Series", mediaType = MediaType.SERIES),
        )
        coEvery { downloadIntake.startSeries("series-1", null) } returns
            Result.success(listOf("d1", "d2", "d3"))

        val messages = mutableListOf<DetailMessage>()
        val h = makeActions(scope = this, detail = detail, messageSink = { messages += it })

        h.downloadSeries()
        advanceUntilIdle()

        assertEquals(
            listOf(DetailMessage.SeriesDownload(queuedCount = 3, error = null)),
            messages,
        )
        assertFalse(h.state.value.isDownloadingSeries)
    }
    // endregion

    // region prepareDownloadSheetEpisodes
    @Test
    fun `prepareDownloadSheetEpisodes calls expandSeason per season and populates sheet episodes`() = runTest {
        val season1 = MediaItem(id = "s1", name = "Season 1", mediaType = MediaType.SEASON)
        val season2 = MediaItem(id = "s2", name = "Season 2", mediaType = MediaType.SEASON)
        val ep1 = MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE)
        val ep2 = MediaItem(id = "e2", name = "E2", mediaType = MediaType.EPISODE)
        val expandedCalls = mutableListOf<Pair<String, String>>()

        val h = makeActions(
            scope = this,
            seasons = listOf(season1, season2),
            seriesId = "series-1",
            itemId = "item-1",
            expandSeason = { itemId, seasonId ->
                expandedCalls += itemId to seasonId
                when (seasonId) {
                    "s1" -> listOf(ep1)
                    "s2" -> listOf(ep2)
                    else -> emptyList()
                }
            },
        )

        h.prepareDownloadSheetEpisodes()
        advanceUntilIdle()

        assertEquals(listOf("item-1" to "s1", "item-1" to "s2"), expandedCalls)
        assertEquals(
            mapOf("s1" to listOf(ep1), "s2" to listOf(ep2)),
            h.state.value.downloadSheetEpisodes,
        )
        assertTrue(h.state.value.downloadSheetLoadingSeasons.isEmpty())
    }
    // endregion

    // region resetForNavigation
    @Test
    fun `resetForNavigation clears populated sheet and download state`() = runTest {
        val season1 = MediaItem(id = "s1", name = "Season 1", mediaType = MediaType.SEASON)
        val ep1 = MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE)
        coEvery { downloadRepository.getDownloadedEpisodeIdsForSeries("series-1") } returns setOf("e1")

        val h = makeActions(
            scope = this,
            seasons = listOf(season1),
            seriesId = "series-1",
            itemId = "item-1",
            expandSeason = { _, _ -> listOf(ep1) },
        )

        h.prepareDownloadSheetEpisodes()
        advanceUntilIdle()
        assertTrue(h.state.value.downloadSheetEpisodes.isNotEmpty())

        h.loadDownloadedEpisodeIds()
        advanceUntilIdle()
        assertEquals(setOf("e1"), h.state.value.downloadedEpisodeIds)

        h.resetForNavigation()

        assertEquals(DownloadLifecycleState(), h.state.value)
    }
    // endregion
}
