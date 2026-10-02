package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
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
        assertEquals(resolved.itemId, "item1")
    }

    @Test
    fun resolve_nonCompletedDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.DOWNLOADING)
        val source = PlaybackSource.Auto("item1", "src1")
        val resolved = source.resolve(dl)
        assertTrue(resolved is PlaybackSource.Online, "Expected Online for non-completed download")
    }

    @Test
    fun resolve_completedDownloadButFileMissing_returnsOnline() {
        val dl = downloadItem("/nonexistent/path/file.mp4", status = DownloadStatus.COMPLETED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue(resolved is PlaybackSource.Online, "Expected Online when file is missing")
    }

    @Test
    fun resolve_completedDownloadWithExistingFile_returnsOffline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.COMPLETED)
        val source = PlaybackSource.Auto("item1", "src1")
        val resolved = source.resolve(dl)
        assertTrue(resolved is PlaybackSource.Offline, "Expected Offline for completed download with existing file")
        assertEquals(resolved.itemId, "item1")
        assertEquals(tempFile.absolutePath, (resolved as PlaybackSource.Offline).downloadPath)
    }

    @Test
    fun resolve_cancelledDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.CANCELLED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue(resolved is PlaybackSource.Online, "Expected Online for cancelled download")
    }

    @Test
    fun resolve_failedDownload_returnsOnline() {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val dl = downloadItem(tempFile.absolutePath, status = DownloadStatus.FAILED)
        val source = PlaybackSource.Auto("item1", null)
        val resolved = source.resolve(dl)
        assertTrue(resolved is PlaybackSource.Online, "Expected Online for failed download")
    }

    @Test
    fun resolve_preservesMediaSourceIdWhenResolvingOnline() {
        val source = PlaybackSource.Auto("item1", "source-42")
        val resolved = source.resolve(download = null)
        assertTrue(resolved is PlaybackSource.Online)
        assertEquals((resolved as PlaybackSource.Online).mediaSourceId, "source-42")
    }

    @Test
    fun online_source_doesNotNeedResolution() {
        val source = PlaybackSource.Online("item1", "src1")
        assertEquals(source.itemId, "item1")
        assertEquals(source.mediaSourceId, "src1")
    }

    @Test
    fun offline_source_carriesDownloadPath() {
        val source = PlaybackSource.Offline("item1", "/data/media/movie.mp4")
        assertEquals(source.itemId, "item1")
        assertEquals(source.downloadPath, "/data/media/movie.mp4")
    }

    // ── Offline-source preference truth table ─────────────────────────
    //
    // preference × online × download-state × file-exists. The default
    // (PREFER_DOWNLOADED) rows pin the historical behaviour: a usable
    // download wins regardless of connectivity. The single new branch is
    // PREFER_STREAMING + online + usable download → Online.

    /** A download that satisfies the COMPLETED + file-exists predicate. */
    private fun usableDownload(): DownloadItem {
        val tempFile = Files.createTempFile("truth-table", ".mp4").toFile()
        tempFile.deleteOnExit()
        return downloadItem(tempFile.absolutePath)
    }

    @Test
    fun truthTable_preferDownloaded_online_usableDownload_returnsOffline() {
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(usableDownload(), online = true, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED)
        assertTrue(resolved is PlaybackSource.Offline, "the historical default: the download wins")
    }

    @Test
    fun truthTable_preferDownloaded_offline_usableDownload_returnsOffline() {
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(usableDownload(), online = false, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED)
        assertTrue(resolved is PlaybackSource.Offline)
    }

    @Test
    fun truthTable_preferStreaming_online_usableDownload_returnsOnline() {
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(usableDownload(), online = true, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING)
        assertTrue(resolved is PlaybackSource.Online, "the one new branch: streaming is preferred while online")
        assertEquals("src1", (resolved as PlaybackSource.Online).mediaSourceId)
    }

    @Test
    fun truthTable_preferStreaming_offline_usableDownload_returnsOffline() {
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(usableDownload(), online = false, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING)
        assertTrue(resolved is PlaybackSource.Offline, "no server to stream from — the download plays")
    }

    @Test
    fun truthTable_preferStreaming_online_nullDownload_returnsOnline() {
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(null, online = true, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING)
        assertTrue(resolved is PlaybackSource.Online)
    }

    @Test
    fun truthTable_preferStreaming_online_fileMissing_returnsOnline() {
        val missing = downloadItem("/nonexistent/path/file.mp4")
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(missing, online = true, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING)
        assertTrue(resolved is PlaybackSource.Online, "a vanished file is not a usable download")
    }

    @Test
    fun truthTable_preferStreaming_online_nonCompletedDownload_returnsOnline() {
        val tempFile = Files.createTempFile("truth-table", ".mp4").toFile()
        tempFile.deleteOnExit()
        val partial = downloadItem(tempFile.absolutePath, status = DownloadStatus.DOWNLOADING)
        val resolved = PlaybackSource.Auto("item1", "src1")
            .resolve(partial, online = true, offlinePlaybackPreference = OfflinePlaybackPreference.PREFER_STREAMING)
        assertTrue(resolved is PlaybackSource.Online, "an in-progress download is not a usable download")
    }

    @Test
    fun truthTable_defaults_preserveTheHistoricalBehaviour() {
        val usable = usableDownload()
        // The defaulted overload (the shape every pre-existing call-site and
        // test used) resolves exactly as before the preference existed.
        assertTrue(PlaybackSource.Auto("item1", "src1").resolve(usable) is PlaybackSource.Offline)
        assertTrue(PlaybackSource.Auto("item1", "src1").resolve(null) is PlaybackSource.Online)
    }
}
