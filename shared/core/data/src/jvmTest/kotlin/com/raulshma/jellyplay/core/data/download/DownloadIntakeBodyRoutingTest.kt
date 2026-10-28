package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadStatus
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
import kotlin.test.assertNull
import kotlin.test.assertSame
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
 *    copy;
 *  - [DownloadIntakeBody.start] passes the delegate's [DownloadResult] through
 *    verbatim — success (item untouched), explicit maxBitrate forwarded, and
 *    the delegate's own failure copy verbatim (folded in from the former
 *    Android host-lane suite, which re-pinned these rows);
 *  - [DownloadIntakeBody.startSeries] forwards null episodeIds (whole-series)
 *    untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadIntakeBodyRoutingTest {

    private val delegate: DownloadDelegate = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val policyStore: SeriesDownloadPolicyStore = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk()
    private val downloadsStore: DownloadsStore = mockk {
        every { downloads } returns MutableStateFlow(
            DownloadsSlice(downloadQuality = DownloadQuality.HIGH_1080P),
        )
    }

    private var noSourceCopy = "no source sentinel"

    private fun body() = DownloadIntakeBody(
        delegate = delegate,
        downloadRepository = downloadRepository,
        policyStore = policyStore,
        detailFeed = detailFeed,
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
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
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
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `every inline single-stream type resolves detail and starts at the default quality`() = runTest {
        for (type in listOf(MediaType.MOVIE, MediaType.EPISODE, MediaType.MUSIC_VIDEO, MediaType.AUDIO)) {
            val inline = item("inline-1", type)
            val detail = detailOf(inline)
            coEvery { detailFeed.getMediaDetail("inline-1", force = false) } returns Result.success(detail)
            coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
                DownloadResult(downloadItem = mockk(), error = null)

            val result = body().startFromItem(inline)

            assertEquals(DownloadRequestResult.Started, result, "mediaType=$type")
        }
        coVerify(exactly = 4) { delegate.startOne(any(), 8_000_000, any(), any()) }
    }

    @Test
    fun `an unresolvable detail fails without attempting a start`() = runTest {
        coEvery { detailFeed.getMediaDetail("movie-1", force = false) } returns
            Result.failure(RuntimeException("offline"))

        val result = body().startFromItem(item("movie-1", MediaType.MOVIE))

        assertTrue(result is DownloadRequestResult.Failed)
        assertEquals("offline", (result as DownloadRequestResult.Failed).message)
        coVerify(exactly = 0) { delegate.startOne(any(), any(), any(), any()) }
    }

    @Test
    fun `a null start request fails with the noSourceError copy`() = runTest {
        val detail = detailOf(item("movie-1", MediaType.MOVIE))
        coEvery { detailFeed.getMediaDetail("movie-1", force = false) } returns Result.success(detail)
        // No usable media source = startOne yields NO request (null), which
        // the body maps to a Failed carrying the platform's no-source copy.
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns null

        val result = body().startFromItem(item("movie-1", MediaType.MOVIE))

        assertEquals(DownloadRequestResult.Failed(noSourceCopy), result)
    }

    @Test
    fun `a successful start passes the delegate result through verbatim`() = runTest {
        val detail = detailOf(item("movie-1", MediaType.MOVIE))
        // Default start() arguments: the null maxBitrate / null subtitle
        // selections must reach the delegate untouched, and the delegate's
        // result must come back as the very same instance (no rewrapping).
        val started = DownloadResult(
            downloadItem = DownloadItem(
                id = "dl-1",
                mediaItemId = "movie-1",
                name = "movie-1",
                mediaType = MediaType.MOVIE,
                downloadUrl = "https://example.com/stream",
                downloadPath = "/tmp/dl-1",
                totalSizeBytes = 0,
                downloadedBytes = 0,
                status = DownloadStatus.PENDING,
            ),
            error = null,
        )
        coEvery { delegate.startOne(detail, null, null, null) } returns started

        val result = body().start(detail)

        assertSame(started, result)
        coVerify(exactly = 1) { delegate.startOne(detail, null, null, null) }
    }

    @Test
    fun `start forwards an explicit maxBitrate to the delegate`() = runTest {
        val detail = detailOf(item("movie-1", MediaType.MOVIE))
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
            DownloadResult(downloadItem = mockk(), error = null)

        body().start(detail, maxBitrate = 8_000_000)

        coVerify(exactly = 1) { delegate.startOne(detail, 8_000_000, null, null) }
    }

    @Test
    fun `a failed start surfaces the delegate's error verbatim`() = runTest {
        val detail = detailOf(item("movie-1", MediaType.MOVIE))
        coEvery { delegate.startOne(detail, null, null, null) } returns
            DownloadResult(downloadItem = null, error = "disk full")

        val result = body().start(detail)

        assertNull(result.downloadItem)
        assertEquals("disk full", result.error)
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

    @Test
    fun `startSeries forwards null episodeIds to download the whole series`() = runTest {
        coEvery { downloadRepository.downloadSeries("series-1", null) } returns
            Result.success(emptyList<String>())

        val result = body().startSeries("series-1", null)

        assertEquals(emptyList<String>(), result.getOrThrow())
        coVerify(exactly = 1) { downloadRepository.downloadSeries("series-1", null) }
    }
}
