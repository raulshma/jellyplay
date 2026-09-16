package com.raulshma.jellyplay.feature.player.audio

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
 * Pins the [TrackDownloadStatusWindow] adapter's id honesty: the player's
 * window is the single now-playing track today, but `downloadsFor` must
 * answer for EVERY requested id (per-id flows combined, input order kept),
 * not just the first — a bulk admission through this window would otherwise
 * read the whole table from one track's row.
 */
class AudioTrackDownloadStatusWindowTest {

    private val downloads: AudioTrackDownloads = mockk(relaxed = true)

    private val window = AudioTrackDownloadStatusWindow(downloads)

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
        every { downloads.trackStatus("a") } returns flowOf(a)
        every { downloads.trackStatus("b") } returns flowOf(null)
        every { downloads.trackStatus("c") } returns flowOf(c)

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
        every { downloads.trackStatus("a") } returns flowOf(a)

        assertEquals(listOf(a), window.downloadsFor(listOf("a")).first())
    }
}
