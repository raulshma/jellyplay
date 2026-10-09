package com.raulshma.jellyplay.core.data.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Mutable playlist window the stub [ExoPlayer] projects. Top-level (not a
 * nested member of the test class) so [stubPlayer] needs no outer receiver.
 */
private class StubPlaylist {
    val items = mutableListOf<MediaItem>()
    var index = 0
    var positionMs = 0L
    var lastSeekIndex = -1
    var lastSeekPositionMs = -1L
    val removed = mutableListOf<Int>()
    val moved = mutableListOf<Pair<Int, Int>>()
    var rebuilt = 0
}

/**
 * The mirror's pre-warm lookahead ([QueuePlaylistMirror]'s
 * PREWARM_LOOKAHEAD_ITEMS) — pinned here so the window tests assert the
 * exact mirrored prefix the production constant produces.
 */
private const val PREWARM_LOOKAHEAD_ITEMS = 12

/**
 * The stateful stub ExoPlayer: every playlist-mutating Player write is
 * stubbed to keep [StubPlaylist] in sync synchronously (a real ExoPlayer
 * would route writes through its internal playback thread), while
 * `prepare`/`play`/`playWhenReady` stay relaxed no-ops — the engine-write
 * surfaces these pins assert on are the queue-chassis writes (seek /
 * removeMediaItem / moveMediaItem / clearMediaItems / the setMediaItems
 * rebuild) plus the pre-warm mirror writes (`setMediaItem` single-item load,
 * `addMediaItem(s)` appends/prepends).
 */
private fun stubPlayer(playlist: StubPlaylist): ExoPlayer {
    val player = mockk<ExoPlayer>(relaxed = true)
    every { player.mediaItemCount } answers { playlist.items.size }
    every { player.getMediaItemAt(any()) } answers { playlist.items[arg<Int>(0)] }
    every { player.currentMediaItemIndex } answers { playlist.index }
    every { player.currentMediaItem } answers { playlist.items.getOrNull(playlist.index) }
    every { player.currentPosition } answers { playlist.positionMs }
    every { player.isPlaying } answers { false }
    every { player.playbackState } answers { Player.STATE_READY }
    every { player.seekTo(any(), any()) } answers {
        playlist.lastSeekIndex = firstArg()
        playlist.lastSeekPositionMs = secondArg()
        playlist.index = firstArg()
    }
    every { player.seekTo(any<Long>()) } answers {
        playlist.lastSeekPositionMs = firstArg()
    }
    every { player.setMediaItem(any<MediaItem>(), any<Long>()) } answers {
        playlist.items.clear()
        playlist.items += firstArg<MediaItem>()
        playlist.index = 0
    }
    every { player.addMediaItems(any<List<MediaItem>>()) } answers {
        playlist.items += arg<List<MediaItem>>(0)
    }
    every { player.addMediaItems(any<Int>(), any<List<MediaItem>>()) } answers {
        playlist.items.addAll(firstArg<Int>(), arg<List<MediaItem>>(1))
    }
    every { player.addMediaItem(any<MediaItem>()) } answers {
        playlist.items += firstArg<MediaItem>()
    }
    every { player.removeMediaItem(any()) } answers {
        val index = firstArg<Int>()
        playlist.removed += index
        if (index in playlist.items.indices) playlist.items.removeAt(index)
        if (playlist.index >= playlist.items.size) playlist.index = playlist.items.size - 1
    }
    every { player.moveMediaItem(any(), any()) } answers {
        val from = firstArg<Int>()
        val to = secondArg<Int>()
        playlist.moved += from to to
        if (from in playlist.items.indices && to in playlist.items.indices) {
            val item = playlist.items.removeAt(from)
            playlist.items.add(to, item)
        }
        when {
            playlist.index == from -> playlist.index = to
            from < playlist.index && to >= playlist.index -> playlist.index -= 1
            from > playlist.index && to <= playlist.index -> playlist.index += 1
        }
    }
    every { player.setMediaItems(any<List<MediaItem>>(), any(), any()) } answers {
        playlist.items.clear()
        playlist.items += arg<List<MediaItem>>(0)
        playlist.index = arg<Int>(1)
        playlist.rebuilt += 1
    }
    every { player.clearMediaItems() } answers { playlist.items.clear() }
    return player
}

/**
 * Manager-level pin for the [AudioPlaybackManager] queue-chassis adoption
 * (the androidHostTest twin of the jvmTest [AudioQueueStateCoreTest] pins):
 * the eleven hand-rolled queue-state bodies moved into the commonMain
 * [AudioQueueStateCore], and every mutation must keep the SAME user-visible
 * queue semantics — cursor math, shuffle/unshuffle restore, undo snapshots,
 * repeat/wrap, remove-current remap — while the media3 playlist mirror
 * (seek / removeMediaItem / moveMediaItem / clearMediaItems / the shuffle +
 * undo rebuild) stays adapter-side.
 *
 * DETERMINISM MODEL: the manager is constructed over a stateful stub
 * [ExoPlayer] (a mockk-backed playlist window list, see [stubPlayer]) and a
 * [StandardTestDispatcher] scope, so `play()`'s async resolve/pre-warm body
 * stays PARKED on the scheduler forever — no network machinery, no
 * queueLoadingJob guards, no pre-warm player writes ever run. The stub is
 * made the manager's live engine by the `playerFactory` seam itself:
 * `getOrCreatePlayer()` ASSIGNS the factory result to the private
 * `exoPlayer` field (the same tail `createPlayer()` has always had), so the
 * first play-path prefix arms `EngineDispatch.isLive` and every
 * `exoPlayer ?: return` adapter guard synchronously — the former
 * reflection write into the field is retired. The stub never fires player
 * events, so chassis-driven cursor writes are asserted directly, the way
 * the core suite asserts its recording dispatch; the ONE async write the
 * adapter performs (the shuffle/undo playlist rebuild) is awaited via a
 * main-looper drain loop.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlaybackManagerQueueSemanticsTest {

    private class Harness(
        /**
         * Non-null: `getMediaDetail` succeeds with this detail for every id,
         * so play()'s resolve lands on the server-reporting arm and its
         * afterLoad pre-warm actually runs (the failure default parks the
         * resolve on the queue-only local fallback, which never pre-warms).
         */
        private val detail: com.raulshma.jellyplay.core.model.MediaDetail? = null,
    ) {
        val playlist = StubPlaylist()
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val player: ExoPlayer = stubPlayer(playlist)

        // Shared collaborators the manager AND its DI-hoisted
        // [AudioLibraryBrowser] both read (since the constructor diet the
        // browser is a ctor param — the harness constructs it over the same
        // mocks, exactly the production Koin shape).
        private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository =
            mockk(relaxed = true) {
                // Real Result values (value classes cannot be proxied): the
                // resolve ladder fails and buildPlayableMediaItem falls to
                // the local-source arm stubbed below (unless the harness was
                // given a success detail, which reaches the pre-warm arm).
                val detailResult: Result<com.raulshma.jellyplay.core.model.MediaDetail> =
                    detail?.let { Result.success(it) }
                        ?: Result.failure(RuntimeException("test"))
                coEvery { getMediaDetail(any()) } returns detailResult
            }
        private val playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository =
            mockk(relaxed = true)
        private val imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider =
            mockk(relaxed = true)
        private val playbackSourceResolver: PlaybackSourceResolver =
            mockk(relaxed = true) {
                coEvery { resolveLocalSource(any()) } answers {
                    val itemId = firstArg<String>()
                    ResolvedPlaybackSource.Local(
                        itemId = itemId,
                        filePath = "/tmp/$itemId",
                        uri = "http://localhost/$itemId",
                        title = "Track $itemId",
                        download = mockk(relaxed = true),
                        offlineItem = null,
                    )
                }
            }

        private val libraryBrowser = AudioLibraryBrowser(
            scope = scope,
            mediaRepository = mediaRepository,
            musicCatalogue = mockk(relaxed = true),
            libraryApiClient = mockk(relaxed = true),
            playlistRepository = mockk(relaxed = true),
            downloadRepository = mockk(relaxed = true),
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            playbackSourceResolver = playbackSourceResolver,
            streamingQualityProvider = { com.raulshma.jellyplay.core.model.StreamingQuality.AUTO },
            adaptiveBitrateSelector = mockk(relaxed = true),
            audioQueueFacadeProvider = { mockk<AudioQueueFacade>(relaxed = true) },
        )

        val manager: AudioPlaybackManager = AudioPlaybackManager(
            context = ApplicationProvider.getApplicationContext(),
            libraryBrowser = libraryBrowser,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            imageUrlProvider = imageUrlProvider,
            playbackSourceResolver = playbackSourceResolver,
            sessionManager = mockk(relaxed = true),
            audioStore = mockk(relaxed = true),
            audioEffectsStore = mockk(relaxed = true),
            playbackStore = mockk(relaxed = true),
            queuePersistenceHelper = mockk(relaxed = true),
            bandwidthMonitor = mockk(relaxed = true),
            bandwidthInterceptor = mockk(relaxed = true),
            lyricsManager = mockk(relaxed = true),
            effectsProcessor = mockk(relaxed = true) {
                every { pitchSemitones } returns MutableStateFlow(0f)
            },
            sleepCountdown = mockk(relaxed = true),
// Relaxed StateFlow stubs answer .value with a boxed Object and blow up
            // the production Boolean reads (play()'s "Play On" gate) — stub the
            // three flags with real flows, disconnected by default.
            jellyfinRemotePlayCastStrategy = mockk(relaxed = true) {
                every { isAvailable } returns MutableStateFlow(false)
                every { isConnected } returns MutableStateFlow(false)
                every { isConnecting } returns MutableStateFlow(false)
                every { discoveredDevices } returns MutableStateFlow(emptyList())
            },
            audioStreamCache = mockk(relaxed = true),
            audioPrefetchEngine = mockk(relaxed = true),
            playbackScope = scope,
            // getOrCreatePlayer() ASSIGNS the factory result to the private
            // exoPlayer field (the createPlayer() tail shape) — the stub
            // becomes the live engine on the first play-path prefix, and no
            // real media3 engine is ever built.
            playerFactory = { player },
        )

        fun close() {
            scope.cancel()
        }
    }

    private val openHarnesses = mutableListOf<Harness>()

    @Before
    fun setUp() {
        openHarnesses.clear()
    }

    @After
    fun tearDown() {
        openHarnesses.forEach { it.close() }
        openHarnesses.clear()
    }

    private fun newHarness(
        detail: com.raulshma.jellyplay.core.model.MediaDetail? = null,
    ): Harness = Harness(detail).also { openHarnesses += it }

    private fun item(id: String) = AudioQueueItem(
        id = id,
        name = "Track $id",
        artist = "Artist of $id",
        album = "Album $id",
        imageUrl = "art://$id",
        mediaSourceId = "ms-$id",
        durationMs = 180_000L,
        normalizationGain = null,
    )

    private fun items(vararg ids: String) = ids.map { item(it) }

    /**
     * Brings the engine live and seeds the chassis queue; playQueue's
     * onPlayRequested hook runs play()'s synchronous prefix (whose
     * engine acquisition assigns the stub through the playerFactory seam)
     * and parks its resolve body on the test scheduler, then the queue is
     * mirrored into the stub playlist — the shape the windowed queue
     * pre-warm leaves the real player in.
     */
    private fun Harness.seedLive(rows: List<AudioQueueItem>, index: Int) {
        manager.playQueue(rows, index)
        mirrorQueueToPlayer()
    }

    private fun Harness.mirrorQueueToPlayer() {
        playlist.items.clear()
        playlist.items += manager.queue.value.map {
            MediaItem.Builder().setMediaId(it.id).build()
        }
        playlist.index = manager.currentIndex.value
    }

    /**
     * Drains the rebuild pipeline (IO build → the Main write posts to the
     * Robolectric main looper) until [condition] holds, then asserts it.
     */
    private fun awaitMain(condition: () -> Boolean, message: String) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(message, condition())
    }

    // ── playQueue / appends ─────────────────────────────────────────────────

    @Test
    fun playQueueSeedsQueueAndCursorThroughTheChassis() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), 1)
        assertEquals(listOf("a", "b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(1, h.manager.currentIndex.value)
    }

    @Test
    fun aFreshQueueInvalidatesPriorUndoHistory() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), 0)
        h.manager.removeFromQueue(1)
        assertTrue("destructive op is undoable", h.manager.undoLastQueueOperation())
        assertEquals(listOf("a", "b"), h.manager.queue.value.map { it.id })

        h.manager.playQueue(items("x", "y"), 0)
        assertFalse("a fresh queue drains the undo stack", h.manager.undoLastQueueOperation())
    }

    @Test
    fun appendsNeverMoveTheCursorAndBulkSkipsEmpty() {
        val h = newHarness()
        h.manager.playQueue(items("a"), 0)
        h.manager.addToQueue(item("b"))
        h.manager.addToQueueAll(items("c", "d"))
        h.manager.addToQueueAll(emptyList())
        assertEquals(listOf("a", "b", "c", "d"), h.manager.queue.value.map { it.id })
        assertEquals("pure enqueue never moves the cursor", 0, h.manager.currentIndex.value)
    }

    // ── skips ───────────────────────────────────────────────────────────────

    @Test
    fun skipToNextAdvancesSeeksToTheRowAndBlocksAtTheRepeatNoneTail() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c"), 0)

        h.manager.skipToNext()

        assertEquals(1, h.manager.currentIndex.value)
        assertEquals(1, h.playlist.lastSeekIndex)
        assertEquals("skips restart the row at zero", 0L, h.playlist.lastSeekPositionMs)

        h.manager.skipToNext()
        assertEquals(2, h.manager.currentIndex.value)

        h.manager.skipToNext() // RepeatNone tail → blocked
        assertEquals("no wrap under RepeatNone", 2, h.manager.currentIndex.value)
    }

    @Test
    fun skipToPreviousRestartsAboveThresholdOtherwiseStepsAndWraps() {
        val h = newHarness()
        h.seedLive(items("a", "b"), 1)
        h.playlist.positionMs = 5_000L // past the 3 s default threshold

        h.manager.skipToPrevious()
        assertEquals("restart-in-place never moves the cursor", 1, h.manager.currentIndex.value)
        assertEquals("restart is a position seek, not a row seek", -1, h.playlist.lastSeekIndex)
        assertEquals(0L, h.playlist.lastSeekPositionMs)
        assertEquals("optimistic publish snaps to zero", 0L, h.manager.currentPosition.value)

        h.playlist.positionMs = 500L
        h.manager.skipToPrevious()
        assertEquals(0, h.manager.currentIndex.value)

        h.manager.skipToPrevious() // RepeatNone head → blocked
        assertEquals(0, h.manager.currentIndex.value)

        h.manager.setRepeatMode(1)
        h.manager.skipToPrevious()
        assertEquals("head wraps to the tail under RepeatAll", 1, h.manager.currentIndex.value)
        assertEquals(1, h.playlist.lastSeekIndex)
    }

    // ── remove / clear / undo ───────────────────────────────────────────────

    @Test
    fun removeCurrentShiftsTheCursorOntoTheShiftedInRow() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c"), 0)

        h.manager.removeFromQueue(0)

        assertEquals(listOf("b", "c"), h.manager.queue.value.map { it.id })
        assertEquals("cursor coerced onto the shifted-in row", 0, h.manager.currentIndex.value)
        assertEquals("the player row removal is the transition write", listOf(0), h.playlist.removed)
        assertEquals("the removal itself must not also seek", -1, h.playlist.lastSeekIndex)

        h.manager.removeFromQueue(1) // neighbour removal — pure state
        assertEquals(listOf("b"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("neighbour removal never seeks", -1, h.playlist.lastSeekIndex)
    }

    @Test
    fun removingTheLastCurrentRowDrainsQueueCursorAndPlayer() {
        val h = newHarness()
        h.seedLive(items("a"), 0)

        h.manager.removeFromQueue(0)

        assertTrue(h.manager.queue.value.isEmpty())
        assertEquals(-1, h.manager.currentIndex.value)
        assertFalse("the empty park drops isPlaying", h.manager.isPlaying.value)
        assertTrue("the player playlist is cleared (dispatch.stop)", h.playlist.items.isEmpty())
    }

    @Test
    fun removeAboveCurrentShiftsTheCursorDownWithoutASeek() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c"), 1)

        h.manager.removeFromQueue(0)

        assertEquals(listOf("b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("neighbour removal never seeks", -1, h.playlist.lastSeekIndex)
    }

    @Test
    fun clearQueueParksAndUndoRestoresQueueAndCursor() {
        val h = newHarness()
        h.seedLive(items("a", "b"), 1)

        h.manager.clearQueue()
        assertTrue(h.manager.queue.value.isEmpty())
        assertEquals(-1, h.manager.currentIndex.value)
        assertFalse("clear parks the engine (dispatch.stop)", h.manager.isPlaying.value)
        assertTrue("clear drops the player playlist", h.playlist.items.isEmpty())

        assertTrue("clear is undoable", h.manager.undoLastQueueOperation())
        assertEquals(listOf("a", "b"), h.manager.queue.value.map { it.id })
        assertEquals("undo restores the snapshot cursor", 1, h.manager.currentIndex.value)
        assertFalse("stack drained", h.manager.undoLastQueueOperation())
    }

    @Test
    fun undoAfterSkipRestoresTheCursorAndReplaysAtTheSnapshotPosition() {
        val h = newHarness()
        h.seedLive(items("a", "b"), 0)
        h.playlist.positionMs = 20_000L

        h.manager.skipToNext()
        assertEquals(1, h.manager.currentIndex.value)

        assertTrue(h.manager.undoLastQueueOperation())
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("matching playlist → window-seek restore", 0, h.playlist.lastSeekIndex)
        assertEquals("restore replays AT the snapshot position", 20_000L, h.playlist.lastSeekPositionMs)
    }

    // ── move ────────────────────────────────────────────────────────────────

    @Test
    fun moveQueueItemRemapsTheCursorMirrorsThePlayerAndNoOpsRejectedMoves() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c", "d"), 3)

        h.manager.moveQueueItem(3, 0) // the current row itself
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("d", h.manager.queue.value[0].id)
        assertEquals(listOf(3 to 0), h.playlist.moved)

        h.manager.moveQueueItem(2, 0) // right-to-left crossing → cursor +1
        assertEquals(1, h.manager.currentIndex.value)

        h.manager.moveQueueItem(0, 0) // no-op rejected
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals("a rejected move never touches the player", 2, h.playlist.moved.size)
    }

    // ── shuffle ─────────────────────────────────────────────────────────────

    @Test
    fun shuffleWithoutAnEngineFlipsOnlyTheFlag() {
        val h = newHarness()
        // Seed through the chassis cells directly: this pin needs the
        // null-engine gate (no play()/playQueue here, so exoPlayer stays null).
        h.manager.state._queue.value = items("a", "b", "c")
        h.manager.state._currentIndex.value = 1

        h.manager.toggleShuffle()

        assertTrue("the flag always flips", h.manager.shuffleMode.value)
        assertEquals(
            "but the order is untouched (the null-player gate)",
            listOf("a", "b", "c"),
            h.manager.queue.value.map { it.id },
        )

        h.manager.setShuffleMode(false)
        assertFalse(h.manager.shuffleMode.value)
    }

    @Test
    fun shuffleRoundTripsAroundTheCurrentRowAndRebuildsThePlayerPlaylist() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c"), 1)
        // The playing row's claim, as the transition listener would have left it.
        h.manager.state.nowPlayingTracker.publishQueueItem(item("b"))

        h.manager.toggleShuffle()
        assertTrue(h.manager.shuffleMode.value)
        assertEquals("the current row shuffles to the head", "b", h.manager.queue.value.first().id)
        assertEquals(setOf("a", "b", "c"), h.manager.queue.value.map { it.id }.toSet())
        assertEquals(0, h.manager.currentIndex.value)
        awaitMain({ h.playlist.rebuilt > 0 }, "the reorder rebuilds the player playlist")

        h.manager.toggleShuffle()
        assertFalse(h.manager.shuffleMode.value)
        assertEquals(
            "the original order is restored",
            listOf("a", "b", "c"),
            h.manager.queue.value.map { it.id },
        )
        assertEquals(
            "the cursor snaps to the playing row's original slot",
            1,
            h.manager.currentIndex.value,
        )
        awaitMain({ h.playlist.rebuilt > 1 }, "the restore rebuilds the player playlist too")
        assertEquals(listOf("a", "b", "c"), h.playlist.items.map { it.mediaId })
    }

    @Test
    fun shuffleWithASingleRowNeverReorders() {
        val h = newHarness()
        h.seedLive(items("a"), 0)

        h.manager.toggleShuffle()

        assertTrue(h.manager.shuffleMode.value)
        assertEquals("order untouched", listOf("a"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value)
    }

    // ── playFromQueue / repeat ──────────────────────────────────────────────

    @Test
    fun playFromQueueCrossIndexSeeksTheRowAndSameIndexRestartsInPlace() {
        val h = newHarness()
        h.seedLive(items("a", "b", "c"), 0)

        h.manager.playFromQueue(2)
        assertEquals(2, h.manager.currentIndex.value)
        assertEquals(2, h.playlist.lastSeekIndex)
        assertEquals(0L, h.playlist.lastSeekPositionMs)

        h.manager.playFromQueue(2) // same index → restart the row, no reload
        assertEquals(2, h.manager.currentIndex.value)
        assertEquals("same-index restart seeks position zero", 0L, h.playlist.lastSeekPositionMs)
    }

    @Test
    fun outOfBoundsPlayFromQueueIsASilentNoOp() {
        val h = newHarness()
        h.seedLive(items("a", "b"), 0)

        h.manager.playFromQueue(9)

        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("no seek for an out-of-bounds row", -1, h.playlist.lastSeekIndex)
    }

    @Test
    fun repeatModesCoerceAndMapOntoThePlayer() {
        val h = newHarness()
        h.seedLive(items("a"), 0)

        h.manager.setRepeatMode(7)
        assertEquals("coerced into 0..2", 2, h.manager.repeatMode.value)
        verify { h.player.repeatMode = Player.REPEAT_MODE_ONE }

        h.manager.setRepeatMode(-4)
        assertEquals(0, h.manager.repeatMode.value)

        h.manager.setRepeatMode(1)
        verify { h.player.repeatMode = Player.REPEAT_MODE_ALL }

        h.manager.cycleRepeatMode() // 1 → 2 (one)
        h.manager.cycleRepeatMode() // 2 → 0 (none)
        assertEquals("(mode+1) % 3 wraps", 0, h.manager.repeatMode.value)
    }

    // ── windowed queue pre-warm ─────────────────────────────────────────────

    /** Success detail for the play-path resolve — every id shares one shape. */
    private fun detailFor(id: String): com.raulshma.jellyplay.core.model.MediaDetail =
        com.raulshma.jellyplay.core.model.MediaDetail(
            item = com.raulshma.jellyplay.core.model.MediaItem(
                id = id,
                name = "Track $id",
                mediaType = com.raulshma.jellyplay.core.model.MediaType.AUDIO,
            ),
            mediaSources = listOf(
                com.raulshma.jellyplay.core.model.MediaSource(id = "ms-$id", name = "Source"),
            ),
        )

    /**
     * Plays [index] through the full async resolve/load body and drains the
     * pre-warm mirror write (the IO build hops to the real IO dispatcher; the
     * append lands on the Robolectric main looper). `runCurrent` — not
     * `advanceUntilIdle`: a successful load arms the position ticker, whose
     * recurring delay loop would spin the virtual-time idle drain forever.
     */
    private fun Harness.playAndAwaitPrewarm(rows: List<AudioQueueItem>, index: Int) {
        manager.playQueue(rows, index)
        scheduler.runCurrent()
        awaitMain(
            { playlist.items.size == minOf(index + 1 + PREWARM_LOOKAHEAD_ITEMS, rows.size) },
            "the windowed pre-warm mirrors the cursor + lookahead prefix",
        )
    }

    @Test
    fun prewarmMirrorsOnlyTheLookaheadWindowPrefixAroundTheCursor() {
        val h = newHarness(detailFor("row2"))
        val rows = (0 until 60).map { item("row$it") }

        h.playAndAwaitPrewarm(rows, index = 2)

        // Prefix [0, cursor + 1 + lookahead): the full prefix below the
        // cursor (the prepend reconciliation) plus the bounded lookahead
        // ahead of it — rows past the window are never mirrored.
        assertEquals(rows.subList(0, 15).map { it.id }, h.playlist.items.map { it.mediaId })
        assertEquals(2, h.manager.currentIndex.value)
    }

    @Test
    fun skipInsideTheMirroredWindowSeeksInsteadOfRebuilding() {
        val h = newHarness(detailFor("row2"))
        val rows = (0 until 60).map { item("row$it") }
        h.playAndAwaitPrewarm(rows, index = 2)

        h.manager.skipToNext()

        assertEquals(3, h.manager.currentIndex.value)
        assertEquals(3, h.playlist.lastSeekIndex)
        assertEquals("the windowed mirror takes the seek, not the rebuild", 0, h.playlist.rebuilt)
    }

    @Test
    fun playFromQueuePastTheWindowRebuildsTheWindowedPrefix() {
        val h = newHarness(detailFor("row2"))
        val rows = (0 until 60).map { item("row$it") }
        h.playAndAwaitPrewarm(rows, index = 2)

        h.manager.playFromQueue(20)
        awaitMain({ h.playlist.rebuilt > 0 }, "an out-of-window jump falls through to the rebuild")

        // The rebuild mirrors the same prefix-window shape around the target
        // (rows past the lookahead ahead of it stay unmirrored).
        assertEquals(rows.subList(0, 33).map { it.id }, h.playlist.items.map { it.mediaId })
        assertEquals(20, h.playlist.index)
        assertEquals(20, h.manager.currentIndex.value)
    }
}
