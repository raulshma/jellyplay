package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.R
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
    private val context: android.content.Context = mockk()
    private val intake = DownloadIntakeImpl(context, delegate, downloadRepository)

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
        // The per-item recipe (prepare + execute) is encapsulated in
        // DownloadDelegate.startOne; the intake is a thin adapter that maps
        // a null recipe result to a human-readable error.
        coEvery { delegate.startOne(detail, null, null, null) } returns DownloadResult(item, null)

        val result = intake.start(detail)

        assertEquals("dl-1", result.downloadItem?.id)
        assertNull(result.error)
        coVerify(exactly = 1) { delegate.startOne(detail, null, null, null) }
    }

    @Test
    fun `start forwards maxBitrate to prepareDownloadRequest`() = runTest {
        val detail = mediaDetail()
        coEvery { delegate.startOne(detail, 8_000_000, null, null) } returns
            DownloadResult(null, null)

        intake.start(detail, maxBitrate = 8_000_000)

        coVerify(exactly = 1) { delegate.startOne(detail, 8_000_000, null, null) }
    }

    @Test
    fun `start returns descriptive error when delegate cannot build a request`() = runTest {
        val detail = mediaDetail()
        // startOne yields null when no request can be built (no media source /
        // blank stream URL); the intake must surface a human-readable error
        // rather than throw or return a silent success.
        coEvery { delegate.startOne(detail, any(), any(), any()) } returns null
        every { context.getString(R.string.data_no_media_source_download) } returns
            "No media source available for download"

        val result = intake.start(detail)

        assertNull(result.downloadItem)
        assertEquals("No media source available for download", result.error)
    }

    @Test
    fun `start surfaces the delegate's failure verbatim`() = runTest {
        val detail = mediaDetail()
        coEvery { delegate.startOne(detail, null, null, null) } returns
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
