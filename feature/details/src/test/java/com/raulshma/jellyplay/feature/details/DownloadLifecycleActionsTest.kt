package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadStatus
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DownloadLifecycleActions]. The helper owns no schedulers of
 * its own — it launches on the injected [CoroutineScope], so each test passes
 * the [runTest] [TestScope] and flushes via [advanceUntilIdle].
 *
 * Session-seam fixture: one [MutableStateFlow] of [DetailSession] replaces the
 * former 4 provider lambdas; localized strings arrive via the pure
 * [fakeDetailStrings] fake; one-shot messages are captured from the shared
 * flow via [RecordingMessages].
 */
class DownloadLifecycleActionsTest {

    private val downloadIntake: DownloadIntake = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk(relaxed = true)
    private val adaptiveBitrateManager: AdaptiveBitrateManager = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = true)

    private val strings = fakeDetailStrings()
    private val messages = RecordingMessages()

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * Builds a [DownloadLifecycleActions] wired to the relaxed collaborators,
     * with an overridable session flow. Defaults: unmetered connection, an
     * empty [DownloadsSlice] (→ ORIGINAL quality → null maxBitrate), and an
     * expandSeason that records calls and returns empty.
     */
    private fun makeActions(
        scope: CoroutineScope,
        detail: MediaDetail? = null,
        seasons: List<MediaItem> = emptyList(),
        seriesId: String? = null,
        itemId: String? = null,
        expandCalls: MutableList<Pair<String, String>>? = null,
        expandedEpisodes: (String) -> List<MediaItem> = { emptyList() },
        downloadsSlice: DownloadsSlice = DownloadsSlice(),
        unmetered: Boolean = true,
    ): DownloadLifecycleActions {
        val session = MutableStateFlow(
            DetailSession(
                itemId = itemId ?: detail?.item?.id ?: "item-1",
                seriesId = seriesId,
                detail = detail,
                seasons = seasons,
            ),
        )
        every { downloadsStore.downloads } returns MutableStateFlow(downloadsSlice)
        every { adaptiveBitrateManager.isUnmeteredConnection() } returns unmetered
        if (expandCalls != null) {
            coEvery { mediaDetailProvider.expandSeason(any(), any()) } coAnswers {
                expandCalls += arg<String>(0) to arg<String>(1)
                expandedEpisodes(arg(1))
            }
        }
        return DownloadLifecycleActions(
            scope = scope,
            session = session,
            messages = messages.flow,
            strings = strings,
            downloadIntake = downloadIntake,
            downloadsStore = downloadsStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            downloadRepository = downloadRepository,
            mediaDetailProvider = mediaDetailProvider,
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
        coEvery { downloadIntake.start(any(), any(), any()) } coAnswers { gate.await() }

        val h = makeActions(scope = this, detail = detail)

        h.startDownload()
        advanceUntilIdle() // runs the launch up to the suspended start() call

        assertTrue(h.state.value.isDownloading) // flipped true mid-flight

        gate.complete(DownloadResult(downloadItem = mockk(relaxed = true), error = null))
        advanceUntilIdle() // resumes the launch → isDownloading flips false

        assertFalse(h.state.value.isDownloading)
        coVerify { downloadIntake.start(detail, null, null) } // ORIGINAL quality → null maxBitrate
    }
    // endregion

    // region download picker (quality + external-subtitle selection)
    @Test
    fun `openDownloadPicker seeds pending quality from prefs and resets subtitle selection`() = runTest {
        val h = makeActions(
            scope = this,
            downloadsSlice = DownloadsSlice(downloadQuality = DownloadQuality.HIGH_1080P),
        )

        h.openDownloadPicker()

        assertTrue(h.state.value.downloadPicker.visible)
        assertEquals(DownloadQuality.HIGH_1080P, h.state.value.downloadPicker.quality)
        assertEquals(SubtitleSelection.All, h.state.value.downloadPicker.subtitleSelection)
    }

    @Test
    fun `setPendingSubtitleSelection replaces the subtitle selection, with All restoring the default`() = runTest {
        val h = makeActions(scope = this)
        h.openDownloadPicker()

        h.setPendingSubtitleSelection(SubtitleSelection.Subset(setOf(2, 4)))
        assertEquals(SubtitleSelection.Subset(setOf(2, 4)), h.state.value.downloadPicker.subtitleSelection)

        h.setPendingSubtitleSelection(SubtitleSelection.All)
        assertEquals(SubtitleSelection.All, h.state.value.downloadPicker.subtitleSelection)
    }

    @Test
    fun `setPendingQuality updates the pending download quality`() = runTest {
        val h = makeActions(scope = this)
        h.openDownloadPicker()

        h.setPendingQuality(DownloadQuality.MEDIUM_720P)

        assertEquals(DownloadQuality.MEDIUM_720P, h.state.value.downloadPicker.quality)
    }

    @Test
    fun `dismissDownloadPicker hides the sheet without clearing the pending selection`() = runTest {
        val h = makeActions(scope = this)
        h.openDownloadPicker()
        h.setPendingSubtitleSelection(SubtitleSelection.Subset(setOf(1)))

        h.dismissDownloadPicker()

        assertFalse(h.state.value.downloadPicker.visible)
        // Pending selection persists so a cellular-confirm follow-up resolves identically.
        assertEquals(SubtitleSelection.Subset(setOf(1)), h.state.value.downloadPicker.subtitleSelection)
    }

    @Test
    fun `startDownload forwards pending quality maxBitrate + subtitle indices to intake and closes the picker`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source")),
        )
        coEvery { downloadIntake.start(any(), any(), any()) } returns DownloadResult(
            downloadItem = DownloadItem(
                id = "dl-1",
                name = "Movie",
                mediaItemId = "m1",
                mediaType = MediaType.MOVIE,
                downloadUrl = "https://stream",
                downloadPath = "/tmp/dl-1",
                totalSizeBytes = 0,
                downloadedBytes = 0,
                status = DownloadStatus.PENDING,
            ),
            error = null,
        )
        val h = makeActions(scope = this, detail = detail)

        h.openDownloadPicker()
        h.setPendingQuality(DownloadQuality.HIGH_1080P) // → 8_000_000 bps
        h.setPendingSubtitleSelection(SubtitleSelection.Subset(setOf(3, 5)))
        h.startDownload()
        advanceUntilIdle()

        // downloadPicker.quality drives maxBitrate (NOT prefs.downloadQuality),
        // and the subtitle selection is threaded through to the intake.
        coVerify { downloadIntake.start(detail, 8_000_000, setOf(3, 5)) }
        assertFalse(h.state.value.downloadPicker.visible)
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

        val h = makeActions(scope = this, detail = detail)

        h.downloadSeries()
        advanceUntilIdle()

        assertEquals(
            listOf(DetailMessage.SeriesDownload(queuedCount = 3, error = null)),
            messages.recorded,
        )
        assertFalse(h.state.value.isDownloadingSeries)
    }

    @Test
    fun `downloadSeries with no loaded session emits details-not-loaded error`() = runTest {
        // Ports the former DetailViewModelTest message-flow test: the no-detail
        // precondition must surface exactly one SeriesDownload error through the
        // shared message channel.
        val h = makeActions(scope = this, detail = null)

        h.downloadSeries()
        advanceUntilIdle()

        val msg = messages.recorded.filterIsInstance<DetailMessage.SeriesDownload>().single()
        assertEquals(0, msg.queuedCount)
        assertEquals(
            strings.get(R.string.detail_error_details_not_loaded),
            msg.error,
        )
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
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
            expandCalls = expandedCalls,
            expandedEpisodes = { seasonId ->
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
            expandCalls = mutableListOf(),
            expandedEpisodes = { listOf(ep1) },
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

    // region startDownload — precondition + cellular-warning guards
    @Test
    fun `startDownload with no loaded detail emits details-not-loaded and skips intake`() = runTest {
        val h = makeActions(scope = this, detail = null)

        h.startDownload()
        advanceUntilIdle()

        assertTrue(messages.recorded.contains(DetailMessage.Text(strings.get(R.string.detail_error_details_not_loaded))))
        coVerify(exactly = 0) { downloadIntake.start(any(), any(), any()) }
    }

    @Test
    fun `startDownload with no media source emits no-source and skips intake`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        val h = makeActions(scope = this, detail = detail)

        h.startDownload()
        advanceUntilIdle()

        assertTrue(messages.recorded.contains(DetailMessage.Text(strings.get(R.string.detail_error_no_source))))
        coVerify(exactly = 0) { downloadIntake.start(any(), any(), any()) }
    }

    @Test
    fun `startDownload on metered connection over threshold surfaces cellular warning`() = runTest {
        // 60 MB source on a metered link with a 50 MB warning threshold → the
        // download does NOT start; the warning dialog state is surfaced instead.
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source", size = 60L * 1024L * 1024L)),
        )
        val h = makeActions(
            scope = this,
            detail = detail,
            unmetered = false,
            downloadsSlice = DownloadsSlice(cellularDownloadSizeWarningMb = 50),
        )

        h.startDownload()
        advanceUntilIdle()

        assertEquals(60, h.state.value.cellularDownloadWarningMb)
        assertFalse(h.state.value.isDownloading)
        coVerify(exactly = 0) { downloadIntake.start(any(), any(), any()) }
    }

    @Test
    fun `startDownload on metered connection under threshold proceeds`() = runTest {
        // 40 MB source under a 50 MB threshold on a metered link → no warning,
        // the download starts.
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source", size = 40L * 1024L * 1024L)),
        )
        coEvery { downloadIntake.start(any(), any(), any()) } returns DownloadResult(
            downloadItem = mockk(relaxed = true),
            error = null,
        )
        val h = makeActions(
            scope = this,
            detail = detail,
            unmetered = false,
            downloadsSlice = DownloadsSlice(cellularDownloadSizeWarningMb = 50),
        )

        h.startDownload()
        advanceUntilIdle()

        assertNull(h.state.value.cellularDownloadWarningMb)
        coVerify { downloadIntake.start(detail, null, null) }
    }

    @Test
    fun `confirmCellularDownload clears the warning and proceeds`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source", size = 60L * 1024L * 1024L)),
        )
        coEvery { downloadIntake.start(any(), any(), any()) } returns DownloadResult(
            downloadItem = mockk(relaxed = true),
            error = null,
        )
        val h = makeActions(
            scope = this,
            detail = detail,
            unmetered = false,
            downloadsSlice = DownloadsSlice(cellularDownloadSizeWarningMb = 50),
        )
        h.startDownload()
        advanceUntilIdle()
        assertEquals(60, h.state.value.cellularDownloadWarningMb)

        h.confirmCellularDownload()
        advanceUntilIdle()

        assertNull(h.state.value.cellularDownloadWarningMb)
        coVerify { downloadIntake.start(detail, null, null) }
    }

    @Test
    fun `dismissCellularDownloadWarning clears the warning without downloading`() = runTest {
        val detail = MediaDetail(
            item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE),
            mediaSources = listOf(MediaSource(id = "src-1", name = "Source", size = 60L * 1024L * 1024L)),
        )
        val h = makeActions(
            scope = this,
            detail = detail,
            unmetered = false,
            downloadsSlice = DownloadsSlice(cellularDownloadSizeWarningMb = 50),
        )
        h.startDownload()
        advanceUntilIdle()
        assertEquals(60, h.state.value.cellularDownloadWarningMb)

        h.dismissCellularDownloadWarning()

        assertNull(h.state.value.cellularDownloadWarningMb)
        coVerify(exactly = 0) { downloadIntake.start(any(), any(), any()) }
    }
    // endregion

    // region downloadSeries — eligibility + failure paths
    @Test
    fun `downloadSeries on a non-series emits not-a-series error`() = runTest {
        val movieDetail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        val h = makeActions(scope = this, detail = movieDetail)

        h.downloadSeries()
        advanceUntilIdle()

        val msg = messages.recorded.filterIsInstance<DetailMessage.SeriesDownload>().single()
        assertEquals(0, msg.queuedCount)
        assertEquals(strings.get(R.string.detail_error_not_a_series), msg.error)
        coVerify(exactly = 0) { downloadIntake.startSeries(any(), any()) }
    }

    @Test
    fun `downloadSeries intake failure emits queue-failed error`() = runTest {
        val seriesDetail = MediaDetail(item = MediaItem(id = "series-1", name = "Series", mediaType = MediaType.SERIES))
        // A throwable with no message exercises the strings-fallback path.
        coEvery { downloadIntake.startSeries("series-1", null) } returns Result.failure(RuntimeException())
        val h = makeActions(scope = this, detail = seriesDetail)

        h.downloadSeries()
        advanceUntilIdle()

        val msg = messages.recorded.filterIsInstance<DetailMessage.SeriesDownload>().single()
        assertEquals(0, msg.queuedCount)
        assertEquals(strings.get(R.string.detail_error_queue_failed), msg.error)
        assertFalse(h.state.value.isDownloadingSeries)
    }
    // endregion

    // region loadDownloadSheetEpisodes — on-demand per-season cache + idempotency
    @Test
    fun `loadDownloadSheetEpisodes expands once and is idempotent for a fetched season`() = runTest {
        val ep1 = MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE)
        val expandCalls = mutableListOf<Pair<String, String>>()
        val h = makeActions(
            scope = this,
            itemId = "item-1",
            expandCalls = expandCalls,
            expandedEpisodes = { listOf(ep1) },
        )

        h.loadDownloadSheetEpisodes("s1")
        advanceUntilIdle()
        // Second call for the same season is a no-op (already in fetchedSeasonIds).
        h.loadDownloadSheetEpisodes("s1")
        advanceUntilIdle()

        assertEquals(listOf("item-1" to "s1"), expandCalls)
        assertEquals(mapOf("s1" to listOf(ep1)), h.state.value.downloadSheetEpisodes)
    }

    @Test
    fun `loadDownloadSheetEpisodes with a null session is a no-op`() = runTest {
        val actions = DownloadLifecycleActions(
            scope = this,
            session = MutableStateFlow(null),
            messages = messages.flow,
            strings = strings,
            downloadIntake = downloadIntake,
            downloadsStore = downloadsStore,
            adaptiveBitrateManager = adaptiveBitrateManager,
            downloadRepository = downloadRepository,
            mediaDetailProvider = mediaDetailProvider,
        )

        actions.loadDownloadSheetEpisodes("s1")
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaDetailProvider.expandSeason(any(), any()) }
    }

    @Test
    fun `resetDownloadSheetState clears the per-season cache so the next load re-expands`() = runTest {
        val ep1 = MediaItem(id = "e1", name = "E1", mediaType = MediaType.EPISODE)
        val expandCalls = mutableListOf<Pair<String, String>>()
        val h = makeActions(
            scope = this,
            itemId = "item-1",
            expandCalls = expandCalls,
            expandedEpisodes = { listOf(ep1) },
        )

        h.loadDownloadSheetEpisodes("s1")
        advanceUntilIdle()
        h.resetDownloadSheetState()
        h.loadDownloadSheetEpisodes("s1")
        advanceUntilIdle()

        // Reset dropped the fetched-season cache → the season re-expands.
        assertEquals(listOf("item-1" to "s1", "item-1" to "s1"), expandCalls)
    }
    // endregion

    // region download-details sheet inventory (moved from the VM body)
    @Test
    fun `loadDownloadFileInventory loads from the repository into state`() = runTest {
        val inventory = mockk<com.raulshma.jellyplay.core.model.DownloadFileInventory>(relaxed = true)
        coEvery { downloadRepository.getDownloadFileInventory("m1") } returns inventory
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        val h = makeActions(scope = this, detail = detail)

        h.loadDownloadFileInventory()
        advanceUntilIdle()

        assertEquals(inventory, h.state.value.downloadFileInventory)
        assertFalse(h.state.value.isLoadingDownloadFiles)
    }

    @Test
    fun `clearDownloadFileInventory resets the sheet inventory`() = runTest {
        val detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE))
        val h = makeActions(scope = this, detail = detail)

        h.clearDownloadFileInventory()

        assertNull(h.state.value.downloadFileInventory)
        assertFalse(h.state.value.isLoadingDownloadFiles)
    }
    // endregion
}
