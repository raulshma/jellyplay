package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadRequest
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadIntakeTest {

    private val delegate: DownloadDelegate = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val intake = DownloadIntakeImpl(delegate, downloadRepository)

    private fun mediaDetail(): MediaDetail {
        val item = MediaItem(
            id = "item-1",
            name = "Test",
            mediaType = MediaType.MOVIE,
        )
        // MediaDetail is a data class — constructing one directly keeps the test
        // independent of how a real detail is sourced.
        return MediaDetail(item = item, mediaSources = emptyList())
    }

    @Test
    fun `start builds request via delegate and executes it`() = runTest {
        val detail = mediaDetail()
        val request = DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = "MOVIE",
            mediaSourceId = "src-1",
            downloadUrl = "https://example.com/stream",
            imageUrl = "https://example.com/img",
            imageBlurHash = null,
        )
        val item = DownloadItem(
            id = "dl-1",
            mediaItemId = "item-1",
            name = "Test",
            mediaType = MediaType.MOVIE,
            downloadUrl = "https://example.com/stream",
            downloadPath = "/tmp/dl-1",
            totalSizeBytes = 0,
            downloadedBytes = 0,
            status = DownloadStatus.PENDING,
        )
        coEvery { delegate.prepareDownloadRequest(detail, null) } returns request
        coEvery { delegate.executeDownload(request) } returns DownloadResult(item, null)

        val result = intake.start(detail)

        assertEquals("dl-1", result.downloadItem?.id)
        assertNull(result.error)
        coVerify(exactly = 1) { delegate.prepareDownloadRequest(detail, null) }
        coVerify(exactly = 1) { delegate.executeDownload(request) }
    }

    @Test
    fun `start forwards maxBitrate to prepareDownloadRequest`() = runTest {
        val detail = mediaDetail()
        val request = DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = "MOVIE",
            mediaSourceId = "src-1",
            downloadUrl = "https://example.com/stream",
            imageUrl = "https://example.com/img",
            imageBlurHash = null,
        )
        coEvery { delegate.prepareDownloadRequest(detail, 8_000_000) } returns request
        coEvery { delegate.executeDownload(request) } returns
            DownloadResult(null, null)

        intake.start(detail, maxBitrate = 8_000_000)

        coVerify(exactly = 1) { delegate.prepareDownloadRequest(detail, 8_000_000) }
    }

    @Test
    fun `start returns descriptive error when delegate cannot build a request`() = runTest {
        val detail = mediaDetail()
        // Simulates "no media source" or "blank stream URL" — the delegate
        // returns null and the intake must surface a human-readable error
        // rather than throw or return a silent success.
        coEvery { delegate.prepareDownloadRequest(detail, any()) } returns null

        val result = intake.start(detail)

        assertNull(result.downloadItem)
        assertEquals("No media source available for download", result.error)
        coVerify(exactly = 0) { delegate.executeDownload(any()) }
    }

    @Test
    fun `start surfaces the delegate's failure verbatim`() = runTest {
        val detail = mediaDetail()
        val request = DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = "MOVIE",
            mediaSourceId = "src-1",
            downloadUrl = "https://example.com/stream",
            imageUrl = "https://example.com/img",
            imageBlurHash = null,
        )
        coEvery { delegate.prepareDownloadRequest(detail, null) } returns request
        coEvery { delegate.executeDownload(request) } returns
            DownloadResult(null, "disk full")

        val result = intake.start(detail)

        assertNull(result.downloadItem)
        assertEquals("disk full", result.error)
    }

    @Test
    fun `startSeries delegates to repository downloadSeries`() = runTest {
        coEvery {
            downloadRepository.downloadSeries("series-1", mapOf("s1" to listOf("e1", "e2")))
        } returns Result.success(listOf("dl-1", "dl-2"))

        val result = intake.startSeries("series-1", mapOf("s1" to listOf("e1", "e2")))

        // The intake is a pure router for the batch path — it must not rewrap
        // or transform the repository result.
        assertEquals(Result.success(listOf("dl-1", "dl-2")), result)
        coVerify(exactly = 1) {
            downloadRepository.downloadSeries("series-1", mapOf("s1" to listOf("e1", "e2")))
        }
    }

    @Test
    fun `startSeries forwards null episodeIds to download whole series`() = runTest {
        coEvery { downloadRepository.downloadSeries("series-1", null) } returns
            Result.success(emptyList<String>())

        val result = intake.startSeries("series-1", null)

        assertEquals(Result.success(emptyList<String>()), result)
    }
}
