package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * Pins the [PlaybackSourceResolver] contract — the single owner of the
 * download-vs-stream fork. The repositories are mocked (MockK); real disk
 * checks are exercised via `Files.createTempFile`, mirroring the
 * `PlaybackSourceTest` pattern in `:feature:player:video`.
 *
 * Robolectric is required because the Local path builds a real
 * `Uri.fromFile(...)`; the core:data unit-test preset stubs Android framework
 * methods to return null (`isReturnDefaultValues = true`), which would NPE on
 * `Uri.toString()`. Same convention as `AudioStreamCacheTest` /
 * `MediaStreamVolumeTest` in this module.
 *
 * Together these cases lock the five inlined predicates the resolver replaces
 * (MainViewModel external-player launch, the audio trio's local-url fallback,
 * PlayerSessionManager's Auto probe): no-download → Stream, completed+exists
 * → Local, completed-but-deleted → Stream, non-completed → Stream, detail
 * failure → null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackSourceResolverTest {

    private val downloadRepository: DownloadRepository = mockk()
    private val mediaRepository: MediaRepository = mockk()
    private val playbackRepository: PlaybackRepository = mockk()
    private val offlineRepository: OfflineRepository = mockk()
    private val offlinePlaybackFacade: OfflinePlaybackFacade = mockk()

    private val resolver = PlaybackSourceResolverImpl(
        downloadRepository,
        mediaRepository,
        playbackRepository,
        offlineRepository,
        offlinePlaybackFacade,
    )

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

    // ── resolvePlaybackSource ───────────────────────────────────────────────

    @Test
    fun `resolvePlaybackSource no download resolves Stream via getMediaDetail`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns null
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item1", name = "Movie Title", mediaType = MediaType.MOVIE),
                mediaSources = listOf(MediaSource(id = "src1", name = "source")),
            )
        )
        coEvery {
            playbackRepository.getStreamUrl("item1", "src1", 0L)
        } returns "http://example.com/stream"

        val resolved = resolver.resolvePlaybackSource("item1", mediaSourceId = null, startPositionTicks = 0L)

        assertTrue(resolved is ResolvedPlaybackSource.Stream)
        val stream = resolved as ResolvedPlaybackSource.Stream
        assertEquals("http://example.com/stream", stream.url)
        assertEquals("Movie Title", stream.title)
        assertEquals("src1", stream.mediaSourceId)
    }

    @Test
    fun `resolvePlaybackSource completed download with file returns Local without getMediaDetail`() = runTest {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val download = downloadItem(tempFile.absolutePath)
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns download
        coEvery { offlineRepository.getOfflineItem("item1") } returns null

        val resolved = resolver.resolvePlaybackSource("item1", mediaSourceId = "src1", startPositionTicks = 0L)

        assertTrue(resolved is ResolvedPlaybackSource.Local)
        val local = resolved as ResolvedPlaybackSource.Local
        assertEquals("item1", local.itemId)
        assertEquals(tempFile.absolutePath, local.filePath)
        assertEquals("Test Movie", local.title)
        assertTrue(local.uri.startsWith("file:"))
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        coVerify(exactly = 0) { playbackRepository.getStreamUrl(any(), any(), any()) }
    }

    @Test
    fun `resolvePlaybackSource completed but file deleted falls back to Stream`() = runTest {
        // Disk-race: row COMPLETED but the file is gone. resolveUsableDownload
        // returns null → falls through to the server path (MainViewModel semantics).
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns
            downloadItem("/nonexistent/path/file.mp4")
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE),
                mediaSources = listOf(MediaSource(id = "src1", name = "s")),
            )
        )
        coEvery { playbackRepository.getStreamUrl("item1", "src1", 0L) } returns "http://stream"

        val resolved = resolver.resolvePlaybackSource("item1", null, 0L)

        assertTrue(resolved is ResolvedPlaybackSource.Stream)
    }

    @Test
    fun `resolvePlaybackSource non-completed download falls back to Stream`() = runTest {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns
            downloadItem(tempFile.absolutePath, status = DownloadStatus.DOWNLOADING)
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE),
                mediaSources = emptyList(),
            )
        )
        coEvery { playbackRepository.getStreamUrl("item1", "", 0L) } returns "http://stream"

        val resolved = resolver.resolvePlaybackSource("item1", null, 0L)

        assertTrue(resolved is ResolvedPlaybackSource.Stream)
    }

    @Test
    fun `resolvePlaybackSource getMediaDetail failure returns null`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns null
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.failure(RuntimeException("boom"))

        val resolved = resolver.resolvePlaybackSource("item1", null, 0L)

        assertNull(resolved)
    }

    @Test
    fun `resolvePlaybackSource prefers explicit mediaSourceId over first`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns null
        coEvery { mediaRepository.getMediaDetail("item1") } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item1", name = "Movie", mediaType = MediaType.MOVIE),
                mediaSources = listOf(
                    MediaSource(id = "src1", name = "first"),
                    MediaSource(id = "src2", name = "second"),
                ),
            )
        )
        coEvery { playbackRepository.getStreamUrl("item1", "src2", 100L) } returns "http://stream2"

        val resolved = resolver.resolvePlaybackSource("item1", "src2", 100L)

        assertTrue(resolved is ResolvedPlaybackSource.Stream)
        assertEquals("src2", (resolved as ResolvedPlaybackSource.Stream).mediaSourceId)
        assertEquals("http://stream2", resolved.url)
    }

    // ── resolveUsableDownload ───────────────────────────────────────────────

    @Test
    fun `resolveUsableDownload null when no download row`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns null
        assertNull(resolver.resolveUsableDownload("item1"))
    }

    @Test
    fun `resolveUsableDownload null when status is not COMPLETED`() = runTest {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns
            downloadItem(tempFile.absolutePath, status = DownloadStatus.PAUSED)
        assertNull(resolver.resolveUsableDownload("item1"))
    }

    @Test
    fun `resolveUsableDownload null when COMPLETED but file missing`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns
            downloadItem("/nope/missing.mp4")
        assertNull(resolver.resolveUsableDownload("item1"))
    }

    @Test
    fun `resolveUsableDownload returns the download when COMPLETED and file exists`() = runTest {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        val download = downloadItem(tempFile.absolutePath)
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns download

        assertEquals(download, resolver.resolveUsableDownload("item1"))
    }

    // ── resolveLocalSource ──────────────────────────────────────────────────

    @Test
    fun `resolveLocalSource null when no usable download`() = runTest {
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns null
        assertNull(resolver.resolveLocalSource("item1"))
    }

    @Test
    fun `resolveLocalSource prefers offline item name over download name`() = runTest {
        val tempFile = Files.createTempFile("test", ".mp4").toFile()
        tempFile.deleteOnExit()
        coEvery { downloadRepository.getDownloadByMediaItemId("item1") } returns downloadItem(tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem("item1") } returns OfflineMediaItem(
            id = "item1",
            name = "Offline Title",
            mediaType = MediaType.MOVIE,
            seriesName = "Series",
        )

        val local = resolver.resolveLocalSource("item1")

        assertNotNull(local)
        assertEquals("Offline Title", local!!.title)
        assertEquals("Series", local.offlineItem?.seriesName)
        assertTrue(local.uri.startsWith("file:"))
        // No server round-trip on the local-only path.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    // ── resolveStartPositionTicks ───────────────────────────────────────────

    @Test
    fun `resolveStartPositionTicks explicit value wins`() = runTest {
        coEvery { offlinePlaybackFacade.getResumePositionTicks(any()) } returns 5_000_000L

        assertEquals(
            10_000_000L,
            resolver.resolveStartPositionTicks("item1", 10_000_000L),
        )
        coVerify(exactly = 0) { offlinePlaybackFacade.getResumePositionTicks(any()) }
    }

    @Test
    fun `resolveStartPositionTicks zero delegates to facade stored ticks`() = runTest {
        coEvery { offlinePlaybackFacade.getResumePositionTicks("item1") } returns 7_000_000L

        assertEquals(7_000_000L, resolver.resolveStartPositionTicks("item1", 0L))
    }

    @Test
    fun `resolveStartPositionTicks zero with no stored position returns zero`() = runTest {
        coEvery { offlinePlaybackFacade.getResumePositionTicks("item1") } returns 0L

        assertEquals(0L, resolver.resolveStartPositionTicks("item1", 0L))
    }
}
