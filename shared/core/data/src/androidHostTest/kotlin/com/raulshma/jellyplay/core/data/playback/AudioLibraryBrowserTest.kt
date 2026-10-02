package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.MediaItem as Media3Item
import android.os.Bundle
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaCollectionReads
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MusicCatalogue
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.StreamingQuality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
 *
 * Robolectric runner: the artwork-uri pin reads `MediaMetadata.artworkUri`,
 * which flows through `android.net.Uri.parse` — unavailable on the plain
 * JUnit runner (this lane's android.jar returns default values). The sdk pin
 * matches the lane's other Robolectric suites (module targetSdk exceeds the
 * installed android-all max).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioLibraryBrowserTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val musicCatalogue: MusicCatalogue = mockk(relaxed = true)
    private val mediaCollectionReads: MediaCollectionReads = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk(relaxed = true)

    private val concurrent = AtomicInteger()
    private val maxConcurrent = AtomicInteger()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun browser() = AudioLibraryBrowser(
        scope = scope,
        mediaRepository = mediaRepository,
        musicCatalogue = musicCatalogue,
        mediaCollectionReads = mediaCollectionReads,
        playlistRepository = playlistRepository,
        downloadRepository = downloadRepository,
        playbackRepository = playbackRepository,
        imageUrlProvider = imageUrlProvider,
        playbackSourceResolver = playbackSourceResolver,
        streamingQualityProvider = { StreamingQuality.HD_720P },
        adaptiveBitrateSelector = adaptiveBitrateSelector,
        audioQueueFacadeProvider = { audioQueueFacade },
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
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns Result.success(ids.map(::track))
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
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns Result.success(ids.map(::track))
        stubLocalResolves(ids, droppedId = "t2")
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1")

        assertEquals(listOf("t1", "t3", "t4"), resolved.map { it.mediaId })
    }

    @Test
    fun `concurrent resolves never exceed the 4-permit bound`() {
        val ids = (1..8).map { "t$it" }
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns Result.success(ids.map(::track))
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
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns
            Result.success(listOf(track("a1-t1"), track("a1-t2")))
        coEvery { musicCatalogue.getAlbumTracks("album-2", force = false) } returns
            Result.success(listOf(track("a2-t1")))
        stubLocalResolves(listOf("a1-t1", "a1-t2", "a2-t1"))
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1", "ALBUM_|album-2")

        assertEquals(listOf("a1-t1", "a1-t2", "a2-t1"), resolved.map { it.mediaId })
    }

    @Test
    fun `resolved items carry the artwork uri from the playback repository`() {
        // Pins the shared artUri lookup: the repository's URL string flows
        // into MediaMetadata.artworkUri for every resolved playable.
        val ids = listOf("t1", "t2")
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns Result.success(ids.map(::track))
        stubLocalResolves(ids)
        coEvery { imageUrlProvider.getImageUrl(any(), any()) } returns "https://server/art/t1.jpg"
        val browser = browser()

        val resolved = addItems(browser, "ALBUM_|album-1")

        assertEquals("https://server/art/t1.jpg", resolved.first().mediaMetadata.artworkUri.toString())
    }

    // ── Android Auto browse tree / search / playback routing ────────────────
    // The constants these tests pin are file-private in AudioLibraryBrowser;
    // the literals here ARE the wire contract, so drift from the production
    // side fails these assertions rather than compiling false-green.

    private fun children(
        browser: AudioLibraryBrowser,
        parentId: String,
        page: Int = 0,
        pageSize: Int = 10,
        params: MediaLibraryService.LibraryParams? = null,
    ): LibraryResult<com.google.common.collect.ImmutableList<Media3Item>> =
        browser.callback.onGetChildren(mockk(relaxed = true), mockk(relaxed = true), parentId, page, pageSize, params)
            .get(10, TimeUnit.SECONDS)

    private fun stubCollectionReads(vararg items: MediaItem) {
        coEvery {
            mediaCollectionReads.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(SearchResult(items = items.toList(), totalRecordCount = items.size, startIndex = 0))
    }

    @Test
    fun `root exposes the car browse folders as browsable typed nodes`() {
        val result = children(browser(), "ROOT")

        assertEquals(
            listOf(
                "Recently Played", "Recently Added", "Artists", "Albums",
                "Genres", "Playlists", "Favorites", "Downloads",
            ),
            result.value!!.map { it.mediaMetadata.title.toString() },
        )
        assertEquals(listOf(true), result.value!!.map { it.mediaMetadata.isBrowsable }.distinct())
        assertEquals(listOf(false), result.value!!.map { it.mediaMetadata.isPlayable }.distinct())
    }

    @Test
    fun `recently played folder queries audio tracks by DatePlayed`() {
        stubCollectionReads(track("r1"))
        val result = children(browser(), "RECENT")

        val filters = slot<LibraryFilters>()
        coVerify { mediaCollectionReads.getMediaItems(any(), capture(filters), any(), any(), any(), any()) }
        assertEquals(listOf(MediaType.AUDIO), filters.captured.mediaTypes)
        assertEquals(SortOption.DATE_PLAYED, filters.captured.sortBy)
        assertEquals(listOf("TRACK_|r1"), result.value!!.map { it.mediaId })
    }

    @Test
    fun `recently added folder queries albums by DateAdded`() {
        stubCollectionReads(MediaItem(id = "al1", name = "New Album", mediaType = MediaType.ALBUM))

        children(browser(), "RECENT_ALBUMS")

        val filters = slot<LibraryFilters>()
        coVerify { mediaCollectionReads.getMediaItems(any(), capture(filters), any(), any(), any(), any()) }
        assertEquals(listOf(MediaType.ALBUM), filters.captured.mediaTypes)
        assertEquals(SortOption.DATE_ADDED, filters.captured.sortBy)
    }

    @Test
    fun `genres folder lists genre nodes and genre children filter by name`() {
        coEvery { mediaRepository.getGenres(any(), any()) } returns Result.success(
            listOf(Genre(id = "g1", name = "Rock"), Genre(id = "g2", name = "Jazz"))
        )
        stubCollectionReads(track("x1"))
        val browser = browser()

        val folders = children(browser, "GENRES")
        assertEquals(listOf("GENRE_|g1", "GENRE_|g2"), folders.value!!.map { it.mediaId })

        val tracks = children(browser, "GENRE_|g1")
        val filters = slot<LibraryFilters>()
        coVerify { mediaCollectionReads.getMediaItems(any(), capture(filters), any(), any(), any(), any()) }
        assertEquals(listOf("Rock"), filters.captured.genres)
        assertEquals(listOf("TRACK_|x1"), tracks.value!!.map { it.mediaId })
    }

    @Test
    fun `folder children responses carry Auto content-style hints`() {
        stubCollectionReads()
        // Relaxed fabrication of the generic Result returns miscasts — stub
        // real empties for every arm this test browses into.
        coEvery { musicCatalogue.getAlbumTracks(any(), any()) } returns Result.success(emptyList())
        coEvery { musicCatalogue.getArtistAlbums(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getGenres(any(), any()) } returns Result.success(emptyList())
        coEvery { playlistRepository.getPlaylists(any()) } returns Result.success(emptyList())
        val browser = browser()

        // The root's children are container tabs — category cards, like the
        // other container folders.
        assertEquals(
            4,
            children(browser, "ROOT").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        // Container folders (albums, an artist's albums, genres) render as
        // grids of category cards.
        assertEquals(
            4,
            children(browser, "ALBUMS").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        assertEquals(
            4,
            children(browser, "GENRES").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        assertEquals(
            4,
            children(browser, "ARTIST_|x").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        // Playlists are the container exception — a list (Auto's playlist
        // convention; Jellyfin playlists rarely carry artwork).
        assertEquals(
            3,
            children(browser, "PLAYLISTS").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"),
        )
        // Track folders (an album's children, history, downloads) render as
        // a flat list.
        assertEquals(
            1,
            children(browser, "ALBUM_|x").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
        assertEquals(
            1,
            children(browser, "RECENT").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
        assertEquals(
            1,
            children(browser, "DOWNLOADS").params?.extras?.getInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"),
        )
    }

    @Test
    fun `content-style hints merge into the client params instead of replacing them`() {
        stubCollectionReads()
        val clientParams = MediaLibraryService.LibraryParams.Builder()
            .setExtras(Bundle().apply { putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true) })
            .build()
        val browser = browser()

        val merged = requireNotNull(children(browser, "ALBUMS", params = clientParams).params?.extras)

        // The client's own extras survive; the hint lands on top.
        assertTrue(merged.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED"))
        assertEquals(4, merged.getInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"))
        // An unknown parent passes the caller's params through verbatim.
        val passedThrough = children(browser, "UNKNOWN_PARENT", params = clientParams).params
        assertTrue(
            passedThrough?.extras?.getBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED") == true,
        )
    }

    @Test
    fun `blank search query is rejected and valid queries map artist album and track hits`() {
        val browser = browser()

        val blank = browser.callback
            .onSearch(mockk(relaxed = true), mockk(relaxed = true), "  ", null)
            .get(5, TimeUnit.SECONDS)
        assertEquals(LibraryResult.RESULT_ERROR_BAD_VALUE, blank.resultCode)

        coEvery { mediaRepository.search(any(), any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(
                    MediaItem(id = "ar1", name = "Artist One", mediaType = MediaType.ARTIST),
                    MediaItem(id = "al1", name = "Album One", mediaType = MediaType.ALBUM),
                    track("s1"),
                ),
                totalRecordCount = 3,
                startIndex = 0,
            )
        )
        val result = browser.callback
            .onGetSearchResult(mockk(relaxed = true), mockk(relaxed = true), "one", 0, 10, null)
            .get(10, TimeUnit.SECONDS)

        assertEquals(listOf("ARTIST_|ar1", "ALBUM_|al1", "TRACK_|s1"), result.value!!.map { it.mediaId })
        assertEquals(listOf(true, true, false), result.value!!.map { it.mediaMetadata.isBrowsable })
    }

    @Test
    fun `onSetMediaItems routes the expansion through the queue facade`() {
        coEvery { musicCatalogue.getAlbumTracks("album-1", force = false) } returns
            Result.success(listOf(track("a1"), track("a2")))
        stubLocalResolves(listOf("a1", "a2"))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns AudioQueueOutcome.Empty
        val browser = browser()

        val withStart = browser.callback.onSetMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf(Media3Item.Builder().setMediaId("ALBUM_|album-1").build()),
            0,
            4200L,
        ).get(30, TimeUnit.SECONDS)

        // The chassis queue stays the source of truth: the facade saw the
        // DOMAIN tracks (not the playable URIs), and the returned playable
        // list mirrors them for media3's own player write. Queue rows are a
        // dense music list — MUSIC_MAX_WIDTH, the phone-side width.
        val tracksSlot = slot<List<MediaItem>>()
        coVerify { audioQueueFacade.playTracks(capture(tracksSlot), startIndex = 0, imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH) }
        assertEquals(listOf("a1", "a2"), tracksSlot.captured.map { it.id })
        assertEquals(listOf("a1", "a2"), withStart.mediaItems.map { it.mediaId })
        assertEquals(0, withStart.startIndex)
        // A resume position for a SURVIVING tapped track passes through.
        assertEquals(4200L, withStart.startPositionMs)
    }

    @Test
    fun `onSetMediaItems maps the controller start index into the expanded list`() {
        coEvery { playlistRepository.getPlaylistItems("pl-1") } returns Result.success(
            listOf(playlistItem("p1"), playlistItem("p2")),
        )
        coEvery { playlistRepository.getPlaylistItems("pl-2") } returns Result.success(
            listOf(playlistItem("q1")),
        )
        stubLocalResolves(listOf("p1", "p2", "q1"))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns AudioQueueOutcome.Empty
        val browser = browser()

        browser.callback.onSetMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf("PLAYLIST_|pl-1", "PLAYLIST_|pl-2").map { Media3Item.Builder().setMediaId(it).build() },
            1,
            0L,
        ).get(30, TimeUnit.SECONDS)

        // The tapped index (1 → pl-2's node) maps to the start of that
        // node's expansion inside the flattened track list.
        val startIndexSlot = slot<Int>()
        coVerify { audioQueueFacade.playTracks(any(), capture(startIndexSlot), any(), any(), any()) }
        assertEquals(2, startIndexSlot.captured)
    }

    @Test
    fun `onSetMediaItems expands a tapped TRACK node through the facade`() {
        // A TRACK_| tap once fell through to the bare-id detail fetch — with
        // the prefix still attached — and resolved nothing; pin the
        // strip-and-expand behavior every playable node depends on.
        coEvery { downloadRepository.getCompletedAudioDownloads(any(), any()) } returns
            listOf(localSource("t9").download)
        stubLocalResolves(listOf("t9"))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns AudioQueueOutcome.Empty
        val browser = browser()

        val withStart = browser.callback.onSetMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf(Media3Item.Builder().setMediaId("TRACK_|t9").build()),
            0,
            0L,
        ).get(30, TimeUnit.SECONDS)

        val tracksSlot = slot<List<MediaItem>>()
        coVerify { audioQueueFacade.playTracks(capture(tracksSlot), startIndex = 0, imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH) }
        assertEquals(listOf("t9"), tracksSlot.captured.map { it.id })
        assertEquals(listOf("t9"), withStart.mediaItems.map { it.mediaId })
    }

    @Test
    fun `onSetMediaItems expands a tapped ARTIST node through the facade`() {
        // "Play <artist>" (voice search returns ARTIST_| nodes) must resolve
        // through the same expansion ladder as a browse-tree tap.
        coEvery { musicCatalogue.getArtistAlbums("ar-1", 50) } returns Result.success(
            listOf(
                MediaItem(id = "al-1", name = "Album 1", mediaType = MediaType.ALBUM),
                MediaItem(id = "al-2", name = "Album 2", mediaType = MediaType.ALBUM),
            ),
        )
        coEvery { musicCatalogue.getAlbumTracks("al-1", force = false) } returns Result.success(listOf(track("x1")))
        coEvery { musicCatalogue.getAlbumTracks("al-2", force = false) } returns Result.success(listOf(track("x2")))
        stubLocalResolves(listOf("x1", "x2"))
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns AudioQueueOutcome.Empty
        val browser = browser()

        val withStart = browser.callback.onSetMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf(Media3Item.Builder().setMediaId("ARTIST_|ar-1").build()),
            0,
            0L,
        ).get(30, TimeUnit.SECONDS)

        val tracksSlot = slot<List<MediaItem>>()
        coVerify { audioQueueFacade.playTracks(capture(tracksSlot), startIndex = 0, imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH) }
        // The artist's albums flatten in traversal order.
        assertEquals(listOf("x1", "x2"), tracksSlot.captured.map { it.id })
        assertEquals(listOf("x1", "x2"), withStart.mediaItems.map { it.mediaId })
    }

    @Test
    fun `onSetMediaItems start index survives a dropped start-track resolution`() {
        // The tapped node (controller index 1) is the PLAYLIST expanding to
        // p2+p3; p2 — its first track, the resume target — resolves to
        // nothing. The returned list must drop it and start on the next
        // survivor (p3), not shift the position onto p3's slot while p1
        // keeps index 0. (The controller index must address a REAL node:
        // media3 clamps startIndex into the item list, and an out-of-range
        // index would clamp to 0 and make p1 the start instead.)
        coEvery { downloadRepository.getCompletedAudioDownloads(any(), any()) } returns
            listOf(localSource("p1").download)
        coEvery { playlistRepository.getPlaylistItems("pl-1") } returns Result.success(
            listOf(playlistItem("p2"), playlistItem("p3")),
        )
        stubLocalResolves(listOf("p1", "p2", "p3"), droppedId = "p2")
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns AudioQueueOutcome.Empty
        val browser = browser()

        val withStart = browser.callback.onSetMediaItems(
            mockk(relaxed = true),
            mockk(relaxed = true),
            listOf(
                Media3Item.Builder().setMediaId("TRACK_|p1").build(),
                Media3Item.Builder().setMediaId("PLAYLIST_|pl-1").build(),
            ),
            1,
            5000L,
        ).get(30, TimeUnit.SECONDS)

        assertEquals(listOf("p1", "p3"), withStart.mediaItems.map { it.mediaId })
        assertEquals(1, withStart.startIndex)
        // The tapped track's resume position must not slide onto the survivor.
        assertEquals(0L, withStart.startPositionMs)
    }

    private fun playlistItem(id: String) = PlaylistItem(
        id = id,
        playlistItemId = "pli-$id",
        name = "Track $id",
        artist = "Artist",
        album = "Album",
        mediaType = MediaType.AUDIO,
        runTimeTicks = null,
    )
}
