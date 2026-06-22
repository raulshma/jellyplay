package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PlaybackSourceTest {

    private fun downloadItem(
        path: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
    ) = DownloadItem(
        id = "dl1",
        mediaItemId = "item1",
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = path,
        downloadUrl = "http://example.com/movie",
        totalSizeBytes = 1_000_000L,
        downloadedBytes = 1_000_000L,
        status = status,
    )

    @Test
    fun resolve_nullDownload_returnsOnline() {
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(download = null)
        assertTrue(resolved is PlaybackSource.Online)
        assertEquals("item1", resolved.itemId)
    }

    @Test
    fun resolve_nonCompletedDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.DOWNLOADING)
        val source = PlaybackSource.Auto("item1", "src1")
        val resolved = source.resolve(dl)
        assertTrue("Expected Online for non-completed download", resolved is PlaybackSource.Online)
    }

    @Test
    fun resolve_completedDownloadButFileMissing_returnsOnline() {
        val dl = downloadItem("/nonexistent/path/file.mp4", status = DownloadStatus.COMPLETED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue("Expected Online when file is missing", resolved is PlaybackSource.Online)
    }

    @Test
    fun resolve_completedDownloadWithExistingFile_returnsOffline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.COMPLETED)
        val source = PlaybackSource.Auto("item1", "src1")
        val resolved = source.resolve(dl)
        assertTrue("Expected Offline for completed download with existing file", resolved is PlaybackSource.Offline)
        assertEquals("item1", resolved.itemId)
        assertEquals(tempFile.absolutePath, (resolved as PlaybackSource.Offline).downloadPath)
    }

    @Test
    fun resolve_cancelledDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.CANCELLED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue("Expected Online for cancelled download", resolved is PlaybackSource.Online)
    }

    @Test
    fun resolve_failedDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.FAILED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue("Expected Online for failed download", resolved is PlaybackSource.Online)
    }

    @Test
    fun resolve_preservesMediaSourceIdWhenResolvingOnline() {
        val source = PlaybackSource.Auto("item1", "source-42")
        val resolved = source.resolve(download = null)
        assertTrue(resolved is PlaybackSource.Online)
        assertEquals("source-42", (resolved as PlaybackSource.Online).mediaSourceId)
    }

    @Test
    fun online_source_doesNotNeedResolution() {
        val source = PlaybackSource.Online("item1", "src1")
        assertEquals("item1", source.itemId)
        assertEquals("src1", source.mediaSourceId)
    }

    @Test
    fun offline_source_carriesDownloadPath() {
        val source = PlaybackSource.Offline("item1", "/data/media/movie.mp4")
        assertEquals("item1", source.itemId)
        assertEquals("/data/media/movie.mp4", source.downloadPath)
    }
}
