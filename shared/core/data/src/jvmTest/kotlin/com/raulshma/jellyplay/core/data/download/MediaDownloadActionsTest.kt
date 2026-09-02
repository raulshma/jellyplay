package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [MediaDownloadActions]' three contracts for the plain hosts (favorites,
 * studio, collection, person, search — issue #147 quick actions):
 *
 * - [MediaDownloadActions.downloadedIds] mirrors the repository's
 *   completed-ids-∪-series-ids union,
 * - [MediaDownloadActions.downloadAndReport] folds the shared outcome cascade
 *   (toast vs open-detail routing) exactly once per branch,
 * - [MediaDownloadActions.removeDownload] keeps the series-vs-item delete
 *   routing even though [com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions]
 *   is constructed internally from the injected scope/repository.
 * Koin-constructed (no @Inject) — the holder is built directly here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaDownloadActionsTest {

    private val downloadRepository: DownloadRepository = mockk {
        every { observeDownloadedIdsIncludingSeries() } returns flowOf(emptySet())
    }
    private val downloadIntake: DownloadIntake = mockk()
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val messenger: DownloadOutcomeMessenger = mockk(relaxed = true)

    private fun TestScope.actions(
        downloadedIds: Set<String> = emptySet(),
    ): MediaDownloadActions {
        every { downloadRepository.observeDownloadedIdsIncludingSeries() } returns flowOf(downloadedIds)
        return MediaDownloadActions(
            scope = this,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
            offlineRepository = offlineRepository,
            messenger = messenger,
        )
    }

    private fun item(id: String, type: MediaType) = MediaItem(
        id = id,
        name = id,
        mediaType = type,
    )

    // ── downloadedIds: the union flow is exposed as host-shareable state ──

    @Test
    fun `downloadedIds exposes the repository's completed-plus-series union`() = runTest {
        val actions = actions(downloadedIds = setOf("a", "series-1"))

        // SharingStarted.Eagerly launches on construction; let the TestScope
        // scheduler run the collector before reading the state.
        advanceUntilIdle()

        assertEquals(setOf("a", "series-1"), actions.downloadedIds.value)
    }

    @Test
    fun `downloadedIds starts empty before the repository emits`() = runTest {
        val actions = actions()

        // Before the Eagerly collector runs, hosts see the empty seed — no
        // card flashes REMOVE_DOWNLOAD for an id it cannot know yet.
        assertTrue(actions.downloadedIds.value.isEmpty())
    }

    // ── download: thin suspend delegation for hosts with richer routing ──

    @Test
    fun `download delegates to downloadIntake startFromItem`() = runTest {
        val movie = item("m1", MediaType.MOVIE)
        coEvery { downloadIntake.startFromItem(movie) } returns DownloadRequestResult.Started
        val actions = actions()

        val result = actions.download(movie)

        assertEquals(DownloadRequestResult.Started, result)
        coVerify(exactly = 1) { downloadIntake.startFromItem(movie) }
    }

    // ── downloadAndReport: the shared when-cascade, branch by branch ──

    @Test
    fun `downloadAndReport reports Started through the messenger only`() = runTest {
        val movie = item("m1", MediaType.MOVIE)
        coEvery { downloadIntake.startFromItem(movie) } returns DownloadRequestResult.Started
        val actions = actions()
        val opened = mutableListOf<String>()

        actions.downloadAndReport(movie) { opened.add(it) }

        verify(exactly = 1) { messenger.downloadStarted() }
        verify(exactly = 0) { messenger.downloadStartFailed() }
        assertTrue(opened.isEmpty(), "Started must not navigate")
    }

    @Test
    fun `downloadAndReport routes SeriesSelectionRequired to the series detail`() = runTest {
        val series = item("series-9", MediaType.SERIES)
        coEvery { downloadIntake.startFromItem(series) } returns
            DownloadRequestResult.SeriesSelectionRequired("series-9")
        val actions = actions()
        val opened = mutableListOf<String>()

        actions.downloadAndReport(series) { opened.add(it) }

        assertEquals(listOf("series-9"), opened)
        verify(exactly = 0) { messenger.downloadStarted() }
        verify(exactly = 0) { messenger.downloadStartFailed() }
    }

    @Test
    fun `downloadAndReport routes NeedsDetailScreen to the item detail`() = runTest {
        val season = item("season-9", MediaType.SEASON)
        coEvery { downloadIntake.startFromItem(season) } returns
            DownloadRequestResult.NeedsDetailScreen("season-9")
        val actions = actions()
        val opened = mutableListOf<String>()

        actions.downloadAndReport(season) { opened.add(it) }

        assertEquals(listOf("season-9"), opened)
        verify(exactly = 0) { messenger.downloadStarted() }
        verify(exactly = 0) { messenger.downloadStartFailed() }
    }

    @Test
    fun `downloadAndReport reports Failed through the messenger only`() = runTest {
        val track = item("t1", MediaType.AUDIO)
        coEvery { downloadIntake.startFromItem(track) } returns
            DownloadRequestResult.Failed("no source")
        val actions = actions()
        val opened = mutableListOf<String>()

        actions.downloadAndReport(track) { opened.add(it) }

        verify(exactly = 1) { messenger.downloadStartFailed() }
        verify(exactly = 0) { messenger.downloadStarted() }
        assertTrue(opened.isEmpty(), "Failed must not navigate")
    }

    // ── removeDownload: fire-and-forget delete with series-vs-item routing ──

    @Test
    fun `removeDownload deletes the whole series download for a series card`() = runTest {
        val actions = actions()

        actions.removeDownload(item("series-1", MediaType.SERIES))
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineRepository.deleteOfflineSeries("series-1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineItem(any()) }
    }

    @Test
    fun `removeDownload deletes a single item for a non-series card`() = runTest {
        val actions = actions()

        actions.removeDownload(item("m1", MediaType.MOVIE))
        advanceUntilIdle()

        coVerify(exactly = 1) { offlineRepository.deleteOfflineItem("m1") }
        coVerify(exactly = 0) { offlineRepository.deleteOfflineSeries(any()) }
    }
}
