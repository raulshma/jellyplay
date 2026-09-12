package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource
import com.raulshma.jellyplay.core.model.BookFormat
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class OkHttpBookContentResolverTest {

    private val tempDir: String = Files.createTempDirectory("book-resolver-test").toString()

    private fun completedDownload(itemId: String, path: String) = DownloadItem(
        id = "dl-$itemId",
        mediaItemId = itemId,
        name = "Test Book",
        mediaType = MediaType.MOVIE,
        downloadPath = path,
        downloadUrl = "http://server/ignored",
        totalSizeBytes = 10L,
        downloadedBytes = 10L,
        status = DownloadStatus.COMPLETED,
    )

    /** Records whether the network path was taken; writes the destination file like the real fetcher. */
    private class FakeFetcher : BookHttpFetcher {
        var fetchCount = 0
        val fetchedUrls = mutableListOf<String>()
        val progressCalls = mutableListOf<BookDownloadProgress>()

        override suspend fun downloadToFile(
            url: String,
            destination: okio.Path,
            onProgress: (BookDownloadProgress) -> Unit,
        ) {
            fetchCount++
            fetchedUrls += url
            okio.FileSystem.SYSTEM.write(destination) { writeUtf8("book-bytes") }
            val progress = BookDownloadProgress(bytesDownloaded = 10L, totalBytes = 10L)
            progressCalls += progress
            onProgress(progress)
        }
    }

    private class FakeResolver(
        private val download: DownloadItem?,
    ) : PlaybackSourceResolver {
        var resolveUsableDownloadCalls = 0

        override suspend fun resolvePlaybackSource(
            itemId: String,
            mediaSourceId: String?,
            startPositionTicks: Long,
        ): ResolvedPlaybackSource? = null

        override suspend fun resolveUsableDownload(itemId: String): DownloadItem? {
            resolveUsableDownloadCalls++
            return download
        }

        override suspend fun resolveLocalSource(itemId: String): ResolvedPlaybackSource.Local? = null

        override suspend fun resolveStartPositionTicks(itemId: String, explicitTicks: Long): Long = 0L
    }

    private fun resolver(cacheRoot: String, download: DownloadItem?, fetcher: FakeFetcher) =
        OkHttpBookContentResolver(
            playbackSourceResolver = FakeResolver(download),
            fetcher = fetcher,
            cacheRoot = cacheRoot.toPath(),
        )

    @Test
    fun offlineDownloadHitShortCircuitsTheNetwork() = runTest {
        val download = completedDownload("item-1", "$tempDir/downloads/item-1.cbz")
        val fetcher = FakeFetcher()
        val result = resolver(tempDir, download, fetcher)
            .resolve("item-1", "Book.cbz", BookFormat.CBZ, "http://server/download", {})

        assertIs<ResolvedBook.OfflineDownload>(result)
        assertEquals(download.downloadPath.toPath(), result.path)
        assertEquals(0, fetcher.fetchCount)
    }

    @Test
    fun missFetchesIntoReaderCacheWithProgress() = runTest {
        val fetcher = FakeFetcher()
        val progress = mutableListOf<BookDownloadProgress>()
        val result = resolver(tempDir, download = null, fetcher = fetcher)
            .resolve("item-2", "My Book.cbz", BookFormat.CBZ, "http://server/dl?api_key=k", progress::add)

        assertIs<ResolvedBook.ReaderCache>(result)
        assertTrue(result.downloadedNow)
        assertEquals("$tempDir/reader-cache/item-2/My Book.cbz".toPath(), result.path)
        assertEquals(1, fetcher.fetchCount)
        assertEquals("http://server/dl?api_key=k", fetcher.fetchedUrls.single())
        assertEquals(1, progress.size)
        assertTrue(okio.FileSystem.SYSTEM.exists(result.path))
    }

    @Test
    fun cachedFileShortCircuitsTheNetwork() = runTest {
        val cachedPath = "$tempDir/reader-cache/item-3/book.pdf".toPath()
        okio.FileSystem.SYSTEM.createDirectories(cachedPath.parent!!)
        okio.FileSystem.SYSTEM.write(cachedPath) { writeUtf8("already-here") }
        val fetcher = FakeFetcher()

        val result = resolver(tempDir, download = null, fetcher = fetcher)
            .resolve("item-3", "book.pdf", BookFormat.PDF, "http://server/dl", {})

        assertIs<ResolvedBook.ReaderCache>(result)
        assertFalse(result.downloadedNow)
        assertEquals(0, fetcher.fetchCount)
        assertEquals(cachedPath, result.path)
    }

    @Test
    fun blankFileNameFallsBackToTheFormatExtension() {
        assertEquals("book.cbz", OkHttpBookContentResolver.sanitizeFileName(null, BookFormat.CBZ))
        assertEquals("book.pdf", OkHttpBookContentResolver.sanitizeFileName("  ", BookFormat.PDF))
    }

    @Test
    fun sanitizeStripsDirectoriesAndHostileCharacters() {
        // The whole directory component is dropped, not underscore-escaped.
        assertEquals("b.cbz", OkHttpBookContentResolver.sanitizeFileName("a/b.cbz", BookFormat.CBZ))
        assertEquals("b.cbz", OkHttpBookContentResolver.sanitizeFileName("a\\b.cbz", BookFormat.CBZ))
        assertEquals("bad_name_.cbz", OkHttpBookContentResolver.sanitizeFileName("bad:name?.cbz", BookFormat.CBZ))
    }
}
