package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File

// V3 downloads conveyor: moved verbatim from the legacy :core:data shim's
// src/test (same package); JUnit4 asserts became kotlin.test and the dropped
// Context ctor param was removed at the move.

/**
 * Exercises the per-item download recipe through its real interface using a
 * hand-rolled fake [OfflineDownloadWriter]. This is the test surface the
 * previous god-interface dependency blocked: the recipe (start → enqueue →
 * bundle poster/backdrop/metadata/trickplay/subtitles/segments) can now be
 * driven end-to-end without mocking the 25-method DownloadRepository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadDelegateTest {

    /** Records every write call so a test can assert the recipe in order. */
    private class RecordingWriter : OfflineDownloadWriter {
        val calls = mutableListOf<String>()
        // Nullable so the field can default without calling an outer-class
        // member; set explicitly by the test before each case.
        var startResult: Result<DownloadItem>? = null
        // Captures the stream list handed to downloadExternalSubtitles so a test
        // can assert the selectedSubtitleIndices narrowing.
        var recordedSubtitleStreams: List<MediaStream>? = null
        // Controls the subtitle-bundle outcome so a test can simulate failure.
        var subtitleBundleResult: Boolean = true
        var subtitleBundleThrows: Throwable? = null
        private fun result(): Result<DownloadItem> = startResult!!

        override suspend fun startDownload(
            mediaItemId: String,
            name: String,
            mediaType: String,
            mediaSourceId: String?,
            downloadUrl: String,
            imageUrl: String?,
            imageBlurHash: String?,
            seriesId: String?,
            seasonId: String?,
            seriesName: String?,
            seasonName: String?,
            episodeNumber: Int?,
            seasonNumber: Int?,
            container: String?,
            precomputedCurrentBytes: Long?,
        ): Result<DownloadItem> {
            calls += "startDownload($mediaItemId)"
            return result()
        }

        override suspend fun saveOfflineMediaItem(
            item: MediaItem,
            imageUrl: String?,
            backdropUrl: String?,
            downloadPath: String?,
        ) { calls += "saveOfflineMediaItem(${item.id})" }

        override suspend fun saveOfflineMediaDetail(
            detail: MediaDetail,
            imageUrl: String?,
            backdropUrl: String?,
        ) { calls += "saveOfflineMediaDetail(${detail.item.id})" }

        override suspend fun downloadOfflineImage(
            itemId: String,
            imageType: String,
            maxWidth: Int,
            parentDir: File,
            fileName: String,
        ): String? {
            calls += "downloadOfflineImage($itemId,$imageType)"
            return "/tmp/$fileName"
        }

        override suspend fun downloadTrickplayData(
            itemId: String,
            trickplayInfo: TrickplayInfo,
            downloadPath: String,
        ): Boolean { calls += "downloadTrickplayData($itemId)"; return true }

        override suspend fun downloadExternalSubtitles(
            itemId: String,
            mediaSourceId: String,
            mediaStreams: List<MediaStream>,
            downloadPath: String,
        ): Boolean {
            recordedSubtitleStreams = mediaStreams
            calls += "downloadExternalSubtitles($itemId)"
            subtitleBundleThrows?.let { throw it }
            return subtitleBundleResult
        }

        override suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean {
            calls += "downloadMediaSegments($itemId)"; return true
        }

        override suspend fun markSubtitlesPending(itemId: String) {
            calls += "markSubtitlesPending($itemId)"
        }

        override fun enqueueDownload(downloadId: String) {
            calls += "enqueueDownload($downloadId)"
        }
    }

    private val playbackRepository: PlaybackRepository = mockk()
    private val writer = RecordingWriter().apply { startResult = Result.success(pendingItem()) }

    // V3 downloads conveyor: the moved DownloadDelegate ctor has no Context
    // (it was only ever touched by android.util.Log, now behind the facade).
    private val delegate = DownloadDelegate(
        writer = writer,
        playbackRepository = playbackRepository,
    )

    @Test
    fun `executeDownload runs the full bundle recipe when start yields PENDING`() = runTest {
        val request = buildRequest(detailWithStreams = true, withTrickplay = true)
        coEvery { playbackRepository.getBackdropUrl(any(), any()) } returns "https://backdrop"

        val result = delegate.executeDownload(request)

        // The recipe: start → enqueue → poster → backdrop → detail → trickplay
        // → subtitles → segments. Every artifact step runs because startDownload
        // returned PENDING (the signal that the transfer was actually queued).
        assertNotNull(result.downloadItem)
        assertNull(result.error)
        assertEquals(
            listOf(
                "startDownload(item-1)",
                "enqueueDownload(dl-1)",
                "downloadOfflineImage(item-1,Primary)",
                "downloadOfflineImage(item-1,Backdrop)",
                "saveOfflineMediaDetail(item-1)",
                "downloadTrickplayData(item-1)",
                "downloadExternalSubtitles(item-1)",
                "downloadMediaSegments(item-1)",
            ),
            writer.calls,
        )
    }

    @Test
    fun `executeDownload narrows bundled subtitles to the selected indices`() = runTest {
        // selectedSubtitleIndices drops only SUBTITLE streams outside the set;
        // non-subtitle streams (audio/video) always pass through, so the writer
        // — and the manifest it writes — record only the user's pick.
        val streams = listOf(
            MediaStream(index = 1, type = StreamType.SUBTITLE, isExternal = true),
            MediaStream(index = 2, type = StreamType.SUBTITLE, isExternal = true),
            MediaStream(index = 3, type = StreamType.SUBTITLE, isExternal = true),
            MediaStream(index = 4, type = StreamType.AUDIO),
        )
        val request = DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = MediaType.MOVIE.name,
            mediaSourceId = "src-1",
            downloadUrl = "https://stream",
            imageUrl = "https://img",
            imageBlurHash = null,
            mediaStreams = streams,
            detail = MediaDetail(
                item = MediaItem(id = "item-1", name = "Test", mediaType = MediaType.MOVIE),
                mediaSources = listOf(MediaSource(id = "src-1", name = "Source", container = "mkv")),
            ),
            container = "mkv",
            selectedSubtitleIndices = setOf(2),
        )
        coEvery { playbackRepository.getBackdropUrl(any(), any()) } returns "https://backdrop"

        delegate.executeDownload(request)

        // Subtitle index 2 survives; 1 and 3 are dropped; the audio stream (4)
        // passes through unchanged.
        assertEquals(listOf(2, 4), writer.recordedSubtitleStreams?.map { it.index })
    }

    @Test
    fun `executeDownload marks subtitles pending when the subtitle bundle fails`() = runTest {
        // Without the rollback, the pre-seeded baseline masks the failure and
        // the item plays offline with no subtitles forever (see executeDownload).
        writer.subtitleBundleResult = false
        val request = buildRequest(detailWithStreams = true, withTrickplay = false)
        coEvery { playbackRepository.getBackdropUrl(any(), any()) } returns "https://backdrop"

        delegate.executeDownload(request)

        assertTrue(writer.calls.contains("markSubtitlesPending(item-1)"))
    }

    @Test
    fun `executeDownload marks subtitles pending when the subtitle bundle throws`() = runTest {
        writer.subtitleBundleThrows = RuntimeException("network gone")
        val request = buildRequest(detailWithStreams = true, withTrickplay = false)

        val result = delegate.executeDownload(request)

        // The throw must not abort the recipe nor skip the rollback.
        assertNotNull(result.downloadItem)
        assertTrue(writer.calls.contains("downloadMediaSegments(item-1)"))
        assertTrue(writer.calls.contains("markSubtitlesPending(item-1)"))
    }

    @Test
    fun `executeDownload does not mark subtitles pending on a successful bundle`() = runTest {
        val request = buildRequest(detailWithStreams = true, withTrickplay = false)

        delegate.executeDownload(request)

        assertFalse(writer.calls.contains("markSubtitlesPending(item-1)"))
    }

    @Test
    fun `executeDownload skips the bundle when start returns a non-PENDING item`() = runTest {
        // A non-PENDING result means the item already existed (e.g. already
        // downloaded) — the artifact bundle must NOT re-run, or it would
        // re-fetch images / subtitles for an item that didn't just start.
        writer.startResult = Result.success(
            pendingItem().copy(status = DownloadStatus.COMPLETED),
        )
        val request = buildRequest(detailWithStreams = true, withTrickplay = true)

        val result = delegate.executeDownload(request)

        assertNotNull(result.downloadItem)
        // Only the start happens — no enqueue, no bundle.
        assertEquals(listOf("startDownload(item-1)"), writer.calls)
    }

    @Test
    fun `executeDownload skips the backdrop bundle for episodes and persists a null backdrop`() = runTest {
        // Jellyfin episodes usually have no Backdrop image, so an episode's own
        // backdrop download 404s and would persist a dead remote URL that only
        // renders offline when Coil's cache happens to hold it. The recipe must
        // skip it entirely: the offline hero falls back to the series backdrop
        // at load time (mirroring the online detail screen).
        val request = buildRequest(
            detailWithStreams = false,
            withTrickplay = false,
            mediaType = MediaType.EPISODE,
        )

        val result = delegate.executeDownload(request)

        assertNotNull(result.downloadItem)
        // Poster still bundles (the episode card thumbnail); the own backdrop
        // download must not run.
        assertTrue(writer.calls.contains("downloadOfflineImage(item-1,Primary)"))
        assertTrue(!writer.calls.contains("downloadOfflineImage(item-1,Backdrop)"))
        coVerify(exactly = 0) { playbackRepository.getBackdropUrl(any(), any()) }
        assertTrue(writer.calls.contains("saveOfflineMediaDetail(item-1)"))
    }

    @Test
    fun `executeDownload propagates the startDownload failure verbatim`() = runTest {
        writer.startResult = Result.failure(RuntimeException("disk full"))
        val request = buildRequest(detailWithStreams = false, withTrickplay = false)

        val result = delegate.executeDownload(request)

        assertNull(result.downloadItem)
        assertEquals("disk full", result.error)
        // No enqueue or bundle on failure.
        assertEquals(listOf("startDownload(item-1)"), writer.calls)
    }

    @Test
    fun `executeDownload persists minimal item when request carries no MediaDetail`() = runTest {
        // No detail → the recipe must not leave the download without an offline
        // row; it falls back to a minimal MediaItem stub instead of
        // saveOfflineMediaDetail.
        val request = DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = MediaType.MOVIE.name,
            mediaSourceId = "src-1",
            downloadUrl = "https://stream",
            imageUrl = "https://img",
            imageBlurHash = null,
        )
        coEvery { playbackRepository.getBackdropUrl(any(), any()) } returns "https://backdrop"

        val result = delegate.executeDownload(request)

        assertNotNull(result.downloadItem)
        assertTrue(writer.calls.contains("saveOfflineMediaItem(item-1)"))
        // Detail path not taken.
        assertTrue(!writer.calls.contains("saveOfflineMediaDetail(item-1)"))
    }

    @Test
    fun `startOne returns null when no media source can build a request`() = runTest {
        // MediaDetail with no mediaSources → prepareDownloadRequest yields null
        // → startOne returns null so the caller (intake / series loop) can
        // decide whether that's an error or a skip.
        val detail = MediaDetail(
            item = MediaItem(id = "item-1", name = "Test", mediaType = MediaType.MOVIE),
            mediaSources = emptyList(),
        )

        val result = delegate.startOne(detail)

        assertNull(result)
    }

    // --- helpers -------------------------------------------------------------

    private fun buildRequest(
        detailWithStreams: Boolean,
        withTrickplay: Boolean,
        mediaType: MediaType = MediaType.MOVIE,
    ): DownloadRequest {
        val streams = if (detailWithStreams) {
            listOf(MediaStream(index = 0, type = StreamType.SUBTITLE, isExternal = true))
        } else emptyList()
        val trickplay = if (withTrickplay) {
            TrickplayInfo(
                width = 320, height = 180,
                tileWidth = 10, tileHeight = 10,
                thumbnailCount = 0, interval = 10_000, bandwidth = 0,
            )
        } else null
        // Build the request + detail directly (instead of going through
        // prepareDownloadRequest, which would force mocking getStreamUrl /
        // getImageUrl). executeDownload only touches getBackdropUrl, mocked
        // per-test.
        val detail = MediaDetail(
            item = MediaItem(id = "item-1", name = "Test", mediaType = mediaType),
            mediaSources = listOf(
                MediaSource(
                    id = "src-1",
                    name = "Source",
                    container = "mkv",
                    mediaStreams = streams,
                    trickplayInfo = trickplay,
                ),
            ),
        )
        return DownloadRequest(
            mediaItemId = "item-1",
            name = "Test",
            mediaType = mediaType.name,
            mediaSourceId = "src-1",
            downloadUrl = "https://stream",
            imageUrl = "https://img",
            imageBlurHash = null,
            trickplayInfo = trickplay,
            mediaStreams = streams,
            detail = detail,
            container = "mkv",
        )
    }

    private fun pendingItem(): DownloadItem = DownloadItem(
        id = "dl-1",
        mediaItemId = "item-1",
        name = "Test",
        mediaType = MediaType.MOVIE,
        downloadUrl = "https://stream",
        downloadPath = "/tmp/dl-1/movie.mkv",
        totalSizeBytes = 0,
        downloadedBytes = 0,
        status = DownloadStatus.PENDING,
    )
}
