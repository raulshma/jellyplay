package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [JvmTrackDownloadStatusWindow]'s id honesty — the pin the former
 * player-audio AudioTrackDownloadStatusWindowTest carried (ported verbatim
 * with the download-actions seam consolidation that moved the window impl
 * from the feature adapter into core:data): the player's window is the
 * single now-playing track today, but `downloadsFor` must answer for EVERY
 * requested id (per-id flows combined, input order kept), not just the
 * first — a bulk admission through this window would otherwise read the
 * whole table from one track's row.
 */
class JvmTrackDownloadStatusWindowTest {

    private val downloadRepository: DownloadRepository = mockk(relaxed = true)

    private val window = JvmTrackDownloadStatusWindow(downloadRepository)

    private fun item(mediaItemId: String) = DownloadItem(
        id = "download-$mediaItemId",
        mediaItemId = mediaItemId,
        name = "Track $mediaItemId",
        mediaType = MediaType.AUDIO,
        downloadPath = "/tmp/$mediaItemId",
        downloadUrl = "http://example/$mediaItemId",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = DownloadStatus.COMPLETED,
    )

    @Test
    fun downloadsFor_answersForEveryRequestedIdInOrder() = runTest {
        val a = item("a")
        val c = item("c")
        every { downloadRepository.getDownloadByMediaItemIdFlow("a") } returns flowOf(a)
        every { downloadRepository.getDownloadByMediaItemIdFlow("b") } returns flowOf(null)
        every { downloadRepository.getDownloadByMediaItemIdFlow("c") } returns flowOf(c)

        val rows = window.downloadsFor(listOf("a", "b", "c")).first()

        assertEquals(listOf(a, c), rows)
    }

    @Test
    fun downloadsFor_emptyIds_isEmptyWithoutReadingTheSeam() = runTest {
        assertTrue(window.downloadsFor(emptyList()).first().isEmpty())
    }

    @Test
    fun downloadsFor_singleId_isTheNowPlayingRow() = runTest {
        val a = item("a")
        every { downloadRepository.getDownloadByMediaItemIdFlow("a") } returns flowOf(a)

        assertEquals(listOf(a), window.downloadsFor(listOf("a")).first())
    }
}
