package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [DownloadIntakeBody]'s startFromItem routing — the branch ladder the
 * I1 fold lifted out of the Android/desktop hand-twins so it exists exactly
 * once. Both platform intakes ([com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl]
 * and DesktopDownloadIntake) are bare constructors over this body, so these
 * rows hold verbatim on both platforms; the only per-platform input is the
 * [DownloadIntakeBody.noSourceError] copy, which the pins observe through the
 * constructor lambda, not the routing.
 *
 * Rows (browse item → outcome):
 *  - SERIES → [DownloadRequestResult.SeriesSelectionRequired] without touching
 *    the detail seam (the download sheet must pre-present on arrival);
 *  - other non-inline containers (SEASON, ALBUM, ...) →
 *    [DownloadRequestResult.NeedsDetailScreen], also without resolving detail;
 *  - inline single-stream types (MOVIE / EPISODE / MUSIC_VIDEO via
 *    `isVideoType`, AUDIO / MUSIC via `isMusicTrack`) resolve their detail and
 *    start at the store's default download quality;
 *  - detail resolution failure → [DownloadRequestResult.Failed] with the
 *    failure's message, no start attempted;
 *  - a start whose request came back null (no usable media source) →
 *    [DownloadRequestResult.Failed] carrying the [DownloadIntakeBody.noSourceError]
 *    copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadIntakeBodyRoutingTest {

    private val delegate: DownloadDelegate = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val mediaRepository: MediaRepository = mockk()
    private val downloadsStore: DownloadsStore = mockk {
        every { downloads } returns MutableStateFlow(
            DownloadsSlice(downloadQuality = DownloadQuality.HIGH_1080P),
        )
    }

    private var noSourceCopy = "no source sentinel"

    private fun body() = DownloadIntakeBody(
        delegate = delegate,
        downloadRepository = downloadRepository,
        mediaRepository = mediaRepository,
        downloadsStore = downloadsStore,
        noSourceError = { noSourceCopy },
    )

    private fun item(id: String, type: MediaType) = MediaItem(id = id, name = id, mediaType = type)

    private fun detailOf(item: MediaItem) = MediaDetail(item = item, mediaSources = emptyList())

    @Test
    fun `a SERIES routes to SeriesSelectionRequired without resolving detail`() = runTest {
        val result = body().startFromItem(item("series-1", MediaType.SERIES))

        assertEquals(
            DownloadRequestResult.SeriesSelectionRequired("series-1"),
            result,
        )
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `non-inline containers route to NeedsDetailScreen without resolving detail`() = runTest {
        for (type in listOf(MediaType.SEASON, MediaType.ALBUM, MediaType.ARTIST, MediaType.FOLDER)) {
            val result = body().startFromItem(item("container-1", type))

            assertEquals(
                DownloadRequestResult.NeedsDetailScreen("container-1"),
                result,
            )
        }
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `every inline single-stream type resolves detail and starts at the default quality`() = runTest {
        for (type in listOf(MediaType.MOVIE, MediaType.EPISODE, MediaType.MUSIC_VIDEO, MediaType.AUDIO)) {
            val inline = item("inline-1", type)
            val detail = detailOf(inline)
            coEvery { mediaRepository.getMediaDetail("inline-1") } returns Result.success(detail)
            coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
                DownloadResult(downloadItem = mockk(), error = null)

            val result = body().startFromItem(inline)

            assertEquals(DownloadRequestResult.Started, result, "mediaType=$type")
        }
        coVerify(exactly = 4) { delegate.startOne(any(), 8_000_000, any(), any()) }
    }

    @Test
    fun `an unresolvable detail fails without attempting a start`() = runTest {
        coEvery { mediaRepository.getMediaDetail("movie-1") } returns
            Result.failure(RuntimeException("offline"))

        val result = body().startFromItem(item("movie-1", MediaType.MOVIE))

        assertTrue(result is DownloadRequestResult.Failed)
        assertEquals("offline", (result as DownloadRequestResult.Failed).message)
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `a null start request fails with the noSourceError copy`() = runTest {
        val detail = detailOf(item("movie-1", MediaType.MOVIE))
        coEvery { mediaRepository.getMediaDetail("movie-1") } returns Result.success(detail)
        // No usable media source = startOne yields NO request (null), which
        // the body maps to a Failed carrying the platform's no-source copy.
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns null

        val result = body().startFromItem(item("movie-1", MediaType.MOVIE))

        assertEquals(DownloadRequestResult.Failed(noSourceCopy), result)
    }

    @Test
    fun `startSeries delegates to the repository verbatim`() = runTest {
        val episodeIds = mapOf("season-1" to listOf("ep-1", "ep-2"))
        coEvery { downloadRepository.downloadSeries("series-1", episodeIds) } returns
            Result.success(listOf("dl-1", "dl-2"))

        val result = body().startSeries("series-1", episodeIds)

        assertEquals(listOf("dl-1", "dl-2"), result.getOrThrow())
        coVerify(exactly = 1) { downloadRepository.downloadSeries("series-1", episodeIds) }
    }
}
