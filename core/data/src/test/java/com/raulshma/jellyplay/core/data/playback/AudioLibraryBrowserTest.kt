package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the order-preserving bounded-concurrency invariant of
 * [AudioLibraryBrowser]'s `mapConcurrently` (driven through the public
 * `callback.onAddMediaItems` ALBUM path): item resolution runs concurrently
 * but is **bounded by the 4-permit semaphore** (never more than 4 resolves in
 * flight), and the deferreds are awaited **in input order**, so the result
 * order always matches the input order even when later items resolve first.
 * Null resolutions (no local file and no server detail) are dropped, never
 * padded.
 */
class AudioLibraryBrowserTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector = mockk(relaxed = true)

    private val concurrent = AtomicInteger()
    private val maxConcurrent = AtomicInteger()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun browser() = AudioLibraryBrowser(
        scope = scope,
        mediaRepository = mediaRepository,
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        playbackSourceResolver = playbackSourceResolver,
        streamingQualityProvider = { StreamingQuality.HD_720P },
        adaptiveBitrateSelector = adaptiveBitrateSelector,
    )

    private fun track(id: String) = MediaItem(id = id, name = "Track $id", mediaType = MediaType.MUSIC)

    private fun localSource(id: String) = ResolvedPlaybackSource.Local(
        itemId = id,
        filePath = "/data/downloads/$id.bin",
        uri = "file:///data/downloads/$id.bin",
        title = "Track $id",
        download = DownloadItem(
            id = "dl-$id",
            mediaItemId = id,
            name = "Track $id",
            mediaType = MediaType.MUSIC,
            downloadPath = "/data/downloads/$id.bin",
            downloadUrl = "https://server/$id",
            totalSizeBytes = 1024L,
            downloadedBytes = 1024L,
            status = DownloadStatus.COMPLETED,
        ),
    )

    /** All tracks resolve locally with per-track delay (later tracks faster). */
    private fun stubLocalResolves(ids: List<String>, droppedId: String? = null) {
        val delays = ids.withIndex().associate { (index, id) -> id to 400L - index * 40L }
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(IllegalStateException("offline in this test"))
        coEvery { playbackSourceResolver.resolveLocalSource(any()) } coAnswers {
            val id = firstArg<String>()
            val now = concurrent.incrementAndGet()
            maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            delay(delays[id] ?: 50L)
            concurrent.decrementAndGet()
            if (id == droppedId) null else localSource(id)
        }
    }

    private fun addItems(browser: AudioLibraryBrowser, vararg mediaIds: String): List<Media3Item> {
        // media3 callbacks are Java surfaces — positional arguments only.
        val future = browser.callback.onAddMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mediaIds.map { Media3Item.Builder().setMediaId(it).build() },
        )
        return future.get(30, TimeUnit.SECONDS)
    }

    @Test
    fun `album resolves to playable items in input order regardless of resolve latency`() {
        val ids = (1..8).map { "t$it" }
        coEvery { mediaRepository.getAlbumTracks("album-1") } returns Result.success(ids.map(::track))
        stubLocalResolves(ids)
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1")

        // Later tracks carry the *shortest* stub delays, so completion order is
        // reversed — the output order must still match the input order.
        assertEquals(ids, resolved.map { it.mediaId })
    }

    @Test
    fun `null resolutions are dropped from the resolved list`() {
        val ids = listOf("t1", "t2", "t3", "t4")
        coEvery { mediaRepository.getAlbumTracks("album-1") } returns Result.success(ids.map(::track))
        stubLocalResolves(ids, droppedId = "t2")
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1")

        assertEquals(listOf("t1", "t3", "t4"), resolved.map { it.mediaId })
    }

    @Test
    fun `concurrent resolves never exceed the 4-permit bound`() {
        val ids = (1..8).map { "t$it" }
        coEvery { mediaRepository.getAlbumTracks("album-1") } returns Result.success(ids.map(::track))
        stubLocalResolves(ids)
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1")

        assertEquals(ids.size, resolved.size)
        assertTrue(
            "observed $maxConcurrent concurrent resolves; semaphore bound is 4",
            maxConcurrent.get() <= 4,
        )
        assertFalse(maxConcurrent.get() == 0)
    }

    @Test
    fun `multiple albums flatten in traversal order`() {
        coEvery { mediaRepository.getAlbumTracks("album-1") } returns
            Result.success(listOf(track("a1-t1"), track("a1-t2")))
        coEvery { mediaRepository.getAlbumTracks("album-2") } returns
            Result.success(listOf(track("a2-t1")))
        stubLocalResolves(listOf("a1-t1", "a1-t2", "a2-t1"))
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1", "ALBUM_|album-2")

        assertEquals(listOf("a1-t1", "a1-t2", "a2-t1"), resolved.map { it.mediaId })
    }
}
