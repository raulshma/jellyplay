package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.shared.core.data.R
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Per docs/adr/0006 rule 2 (the subject's lane wins), [DownloadIntakeImpl]'s
 * shared body is [DownloadIntakeBody] and its routing is pinned on the JVM
 * lane (DownloadIntakeBodyRoutingTest). This suite keeps only the class's
 * single real Android delta: the localized no-source copy —
 * R.string.data_no_media_source_download resolved via Context — flowing into
 * the body's start and startFromItem paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadIntakeTest {

    private val delegate: DownloadDelegate = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val policyStore: SeriesDownloadPolicyStore = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk()
    private val downloadsStore: DownloadsStore = mockk {
        every { downloads } returns MutableStateFlow(DownloadsSlice())
    }
    private val context: android.content.Context = mockk()
    private val intake = DownloadIntakeImpl(
        context, delegate, downloadRepository, policyStore, detailFeed, downloadsStore,
    )

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

    // ── startFromItem: inline download from a browse card ──

    private fun browseItem(type: MediaType, id: String = "item-1") = MediaItem(
        id = id,
        name = "Test",
        mediaType = type,
    )

    @Test
    fun `startFromItem maps a null recipe to Failed for a music track`() = runTest {
        val detail = mediaDetail()
        coEvery { detailFeed.getMediaDetail("item-1", force = false) } returns Result.success(detail)
        every { context.getString(R.string.data_no_media_source_download) } returns "no source"
        coEvery { delegate.startOne(detail, null, null, null) } returns null

        val result = intake.startFromItem(browseItem(MediaType.AUDIO))

        assertEquals(DownloadRequestResult.Failed("no source"), result)
    }
}
