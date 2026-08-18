package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaylistItem
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Plain-JVM test over the facade's three mocked seams ([AudioQueueManager],
 * [MediaRepository], [ImageUrlProvider]) — no Robolectric, no concrete
 * AudioPlaybackManager (its Hilt constructor pulls in ~20 collaborators).
 *
 * `Main` is the shared [StandardTestDispatcher] so the dispatcher-contract
 * test can prove the queue mutation ran through the Main test scheduler even
 * when the facade's caller sits on `Dispatchers.Default`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioQueueFacadeTest {

    private val testDispatcher = StandardTestDispatcher()

    private val queueManager: AudioQueueManager = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)

    private lateinit var facade: DefaultAudioQueueFacade

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        facade = DefaultAudioQueueFacade(
            queueManager = queueManager,
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: String, album: String? = null) = MediaItem(
        id = id,
        name = "Track $id",
        mediaType = MediaType.AUDIO,
        album = album,
    )

    // ── Image-width propagation ─────────────────────────────────────────

    @Test
    fun `playTracks resolves image urls at the default width 400`() = runTest(testDispatcher) {
        facade.playTracks(listOf(track("t1"), track("t2")))

        verify(exactly = 1) { imageUrlProvider.getImageUrl("t1", maxWidth = ImageUrlProvider.DEFAULT_MAX_WIDTH) }
        verify(exactly = 1) { imageUrlProvider.getImageUrl("t2", maxWidth = ImageUrlProvider.DEFAULT_MAX_WIDTH) }
        verify(exactly = 0) { imageUrlProvider.getImageUrl(any(), maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH) }
    }

    @Test
    fun `playTracks honors MUSIC_MAX_WIDTH 300 for dense music lists`() = runTest(testDispatcher) {
        facade.playTracks(listOf(track("t1"), track("t2"), track("t3")), imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)

        verify(exactly = 3) { imageUrlProvider.getImageUrl(any(), maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH) }
    }

    // ── Album fallback ──────────────────────────────────────────────────

    @Test
    fun `playTracks fills null albums from albumFallback and keeps non-null albums`() = runTest(testDispatcher) {
        val withoutAlbum = track("t1", album = null)
        val withAlbum = track("t2", album = "Own Album")

        val outcome = facade.playTracks(listOf(withoutAlbum, withAlbum), albumFallback = "Fallback Album")

        val queue = (outcome as AudioQueueOutcome.Started).queue
        assertEquals("Fallback Album", queue[0].album)
        assertEquals("Own Album", queue[1].album)
    }

    // ── Shuffle + startIndex passthrough ────────────────────────────────

    @Test
    fun `playTracks passes startIndex through and preserves order unshuffled`() = runTest(testDispatcher) {
        val tracks = (1..3).map { track("t$it") }

        val outcome = facade.playTracks(tracks, startIndex = 2)

        verify(exactly = 1) { queueManager.playQueue(any(), 2) }
        val started = outcome as AudioQueueOutcome.Started
        assertEquals(2, started.startIndex)
        assertEquals(listOf("t1", "t2", "t3"), started.queue.map { it.id })
    }

    @Test
    fun `playTracks shuffled=true permutes the queue and keeps the same tracks`() = runTest(testDispatcher) {
        val tracks = (1..12).map { track("t%02d".format(it)) }
        val originalIds = tracks.map { it.id }

        val outcome = facade.playTracks(tracks, startIndex = 4, shuffled = true)

        verify(exactly = 1) { queueManager.playQueue(any(), 4) }
        val ids = (outcome as AudioQueueOutcome.Started).queue.map { it.id }
        // Same multiset of tracks…
        assertEquals(originalIds.toSet(), ids.toSet())
        // …in a different order (identity shuffle has probability 1/12! ≈ 0).
        assertFalse(ids == originalIds)
    }

    // ── Empty / Failed / Suppressed ─────────────────────────────────────

    @Test
    fun `playTracks on empty input returns Empty without touching the queue`() = runTest(testDispatcher) {
        val outcome = facade.playTracks(emptyList<MediaItem>())

        assertEquals(AudioQueueOutcome.Empty, outcome)
        verify { queueManager wasNot Called }
    }

    @Test
    fun `enqueueTracks appends every item and reports Started with no start index`() = runTest(testDispatcher) {
        val outcome = facade.enqueueTracks(listOf(track("t1"), track("t2")))

        val started = outcome as AudioQueueOutcome.Started
        assertEquals(-1, started.startIndex)
        // Bulk seam: one addToQueueAll (single queue emission + persistence)
        // instead of per-item appends.
        verify(exactly = 1) {
            queueManager.addToQueueAll(match { items -> items.map { it.id } == listOf("t1", "t2") })
        }
    }

    @Test
    fun `enqueueTracks on empty input returns Empty without touching the queue`() = runTest(testDispatcher) {
        assertEquals(AudioQueueOutcome.Empty, facade.enqueueTracks(emptyList()))
        verify { queueManager wasNot Called }
    }

    @Test
    fun `enqueueTrack appends the single item and reports Started with no start index`() = runTest(testDispatcher) {
        val outcome = facade.enqueueTrack(track("t1"))

        val started = outcome as AudioQueueOutcome.Started
        assertEquals(-1, started.startIndex)
        assertEquals(listOf("t1"), started.queue.map { it.id })
        verify(exactly = 1) { queueManager.addToQueue(match { it.id == "t1" }) }
    }

    @Test
    fun `startInstantMix plays the fetched mix at index 0 with the fallback applied`() = runTest(testDispatcher) {
        val guardThreads = CopyOnWriteArrayList<String>()
        coEvery { mediaRepository.getInstantMix("seed") } returns Result.success(
            listOf(track("m1", album = null), track("m2", album = "Own Album")),
        )

        val outcome = facade.startInstantMix("seed", albumFallback = "Fallback Album") {
            guardThreads.add(Thread.currentThread().name)
            true
        }

        val started = outcome as AudioQueueOutcome.Started
        assertEquals(listOf("m1", "m2"), started.queue.map { it.id })
        assertEquals("Fallback Album", started.queue[0].album)
        assertEquals("Own Album", started.queue[1].album)
        verify(exactly = 1) { queueManager.playQueue(any(), 0) }
        // Mix fetch resolves artwork at the default detail width (400).
        verify(exactly = 1) { imageUrlProvider.getImageUrl("m1", maxWidth = ImageUrlProvider.DEFAULT_MAX_WIDTH) }
        // The guard runs on Main (the shared test scheduler thread).
        assertEquals(listOf(Thread.currentThread().name), guardThreads)
    }

    @Test
    fun `startInstantMix on empty mix returns Empty without playing`() = runTest(testDispatcher) {
        coEvery { mediaRepository.getInstantMix("seed") } returns Result.success(emptyList())

        assertEquals(AudioQueueOutcome.Empty, facade.startInstantMix("seed"))
        verify { queueManager wasNot Called }
    }

    @Test
    fun `startInstantMix on repository failure returns Failed with the cause`() = runTest(testDispatcher) {
        val boom = RuntimeException("boom")
        coEvery { mediaRepository.getInstantMix("seed") } returns Result.failure(boom)

        val outcome = facade.startInstantMix("seed")

        val failed = outcome as AudioQueueOutcome.Failed
        assertSame(boom, failed.cause)
        verify { queueManager wasNot Called }
    }

    @Test
    fun `startInstantMix on guard veto returns Suppressed without playing`() = runTest(testDispatcher) {
        coEvery { mediaRepository.getInstantMix("seed") } returns Result.success(listOf(track("m1")))

        val outcome = facade.startInstantMix("seed") { false }

        assertEquals(AudioQueueOutcome.Suppressed, outcome)
        verify { queueManager wasNot Called }
    }

    // ── Dispatcher contract (the structural fix for the live crash) ─────

    @Test
    fun `queue mutations run on Main even when the caller is on Dispatchers Default`() = runTest(testDispatcher) {
        val mutationThreads = CopyOnWriteArrayList<String>()
        every { queueManager.playQueue(any(), any()) } answers { mutationThreads.add(Thread.currentThread().name) }
        every { queueManager.addToQueue(any()) } answers { mutationThreads.add(Thread.currentThread().name) }
        every { queueManager.addToQueueAll(any()) } answers { mutationThreads.add(Thread.currentThread().name) }

        // The pre-facade bug: DetailViewModel/InstantMixActions built the queue
        // on Default and called playQueue right there (IllegalStateException on
        // every invocation). The facade must hop the mutation to Main.
        withContext(Dispatchers.Default) {
            facade.playTracks(listOf(track("t1")), startIndex = 0)
            facade.enqueueTracks(listOf(track("t2")))
        }

        assertEquals(2, mutationThreads.size)
        // Main is the shared TestDispatcher, which runTest advances on this
        // thread; an inline Default-thread mutation would record a worker name.
        mutationThreads.forEach { assertEquals(Thread.currentThread().name, it) }
    }

    // ── Playlist overload (imageless mapper, untouched) ─────────────────

    @Test
    fun `playPlaylist maps items with imageUrl null and passes startIndex`() = runTest(testDispatcher) {
        val items = listOf(
            PlaylistItem(id = "p1", name = "Song 1", artist = "Artist", album = "Album", runTimeTicks = 10_000_000L),
        )

        val outcome = facade.playPlaylist(items, startIndex = 1)

        val started = outcome as AudioQueueOutcome.Started
        assertEquals(1, started.startIndex)
        val captured = slot<List<AudioQueueItem>>()
        verify(exactly = 1) { queueManager.playQueue(capture(captured), 1) }
        val queueItem = captured.captured.single()
        assertEquals("p1", queueItem.id)
        assertEquals("Song 1", queueItem.name)
        assertEquals("Artist", queueItem.artist)
        assertEquals("Album", queueItem.album)
        assertNull(queueItem.imageUrl)
        assertEquals(1_000L, queueItem.durationMs)
        // Playlist rows never resolve artwork.
        verify(exactly = 0) { imageUrlProvider.getImageUrl(any(), any()) }
    }

    @Test
    fun `playPlaylist on empty input returns Empty without touching the queue`() = runTest(testDispatcher) {
        assertEquals(AudioQueueOutcome.Empty, facade.playPlaylist(emptyList()))
        verify { queueManager wasNot Called }
    }

    @Test
    fun `enqueuePlaylistItem appends the imageless queue item`() = runTest(testDispatcher) {
        facade.enqueuePlaylistItem(PlaylistItem(id = "p1", name = "Song 1"))

        val captured = slot<AudioQueueItem>()
        verify(exactly = 1) { queueManager.addToQueue(capture(captured)) }
        assertEquals("p1", captured.captured.id)
        assertNull(captured.captured.imageUrl)
    }
}
