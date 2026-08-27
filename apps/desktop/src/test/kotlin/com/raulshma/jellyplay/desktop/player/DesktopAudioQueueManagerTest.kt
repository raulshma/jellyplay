package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.QueueUndoEvent
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Queue-semantics suite pinning [DesktopAudioQueueManager] against the Android
 * media3 AudioPlaybackManager behavior table (the semantics source of truth,
 * legacy core:data `AudioPlaybackManager.kt`). Collaborators come from the
 * shared fixtures file (hand-rolled fakes — this module's test source set has
 * no mocking library): a scriptable [AudioTrackResolver], a recording
 * [com.raulshma.jellyplay.core.data.repository.PlaybackRepository], a
 * thread-safe in-memory Room DAO under the REAL QueuePersistenceHelper, and
 * [FakeMediaEngine] standing in for mpv.
 *
 * DETERMINISM MODEL. The manager runs on an [UnconfinedTestDispatcher] scope:
 * every launched effect (persistence chain, stop/start reporting, lyrics,
 * per-item resolution, engine loads, ENDED auto-advance) completes INLINE
 * within the mutation that triggered it — the fakes suspend for nothing — and
 * the ticker/progress-reporter `delay()` cadences run on VIRTUAL time advanced
 * explicitly via [Harness.tick]. Assertions are therefore direct reads after
 * each action: no wall-clock polls, no host-load sensitivity, and stronger
 * pins as a side effect (e.g. one bulk append MUST persist before
 * `addToQueueAll` even returns). The real-engine WAV suite keeps wall clocks;
 * that is native-mpv territory where virtual time cannot reach.
 *
 * Each case cites the Android behavior it pins; declared divergences live in
 * the manager's KDoc, not here.
 */
class DesktopAudioQueueManagerTest {

    /**
     * One wired manager over shared fakes on the test scheduler. `preSeed`
     * fills the DAO before [DesktopAudioQueueManager.start] restores from it;
     * `callStart=false` skips start() so pre-start StateFlow defaults can be
     * pinned (Android constructs its manager with the same defaults).
     */
    private inner class Harness(
        private val callStart: Boolean = true,
        private val preSeed: suspend (InMemoryQueueDao) -> Unit = {},
    ) {
        val engines = mutableListOf<FakeMediaEngine>()
        val resolver = FakeResolver().apply { seed("a", "b", "c", "d", "r", "zz") }
        val repo = FakePlaybackRepository()
        val dao = InMemoryQueueDao()
        val undoEvents = CopyOnWriteEventLog()
        val effects = DesktopAudioEffectsManager()

        private val dispatcher = UnconfinedTestDispatcher()
        private val scheduler = dispatcher.scheduler

        /** The manager's scope — drive ALL of its coroutines from tests. */
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val manager = DesktopAudioQueueManager(
            trackResolver = resolver,
            playbackRepository = repo,
            imageUrlProvider = FakeImages(),
            queuePersistenceHelper = QueuePersistenceHelper(dao),
            lyricsManager = AudioLyricsManager(FakeLyricsRepository()),
            sleepTimerManager = SleepTimerManager(TestTimeSource()),
            scope = scope,
            engineFactory = { FakeMediaEngine().also { engines += it } },
            mainThreadGuard = false,
            progressReportIntervalMs = 40L,
            effectsManager = effects,
        ).also { manager ->
            scope.launch {
                manager.undoEvents.collect { event -> undoEvents += event }
            }
        }

        init {
            kotlinx.coroutines.runBlocking { preSeed(dao) }
            if (callStart) manager.start()
            // start()'s restore coroutine ran inline (Unconfined); flush any
            // follow-up scheduling deterministically.
            drain()
        }

        /** The most recently created engine (mpv holds one live session at a time too). */
        val engine: FakeMediaEngine get() = engines.first()

        /** Flushes all currently-resumable tasks without advancing time. */
        fun drain() {
            scheduler.runCurrent()
        }

        /** Advances virtual time by [ms], then flushes resumed tasks. */
        fun tick(ms: Long) {
            scheduler.advanceTimeBy(ms)
            scheduler.runCurrent()
        }

        fun close() {
            scope.cancel()
        }
    }

    /** Thread-safe tiny event log for the buffered undo SharedFlow. */
    private class CopyOnWriteEventLog : java.util.concurrent.CopyOnWriteArrayList<QueueUndoEvent>()

    private val openHarnesses = mutableListOf<Harness>()

    private fun newHarness(
        callStart: Boolean = true,
        preSeed: suspend (InMemoryQueueDao) -> Unit = {},
    ): Harness = Harness(callStart, preSeed).also { openHarnesses += it }

    @AfterTest
    fun tearDownHarnesses() {
        openHarnesses.forEach { it.close() }
        openHarnesses.clear()
    }

    private fun item(id: String, durationMs: Long = 180_000L) = AudioQueueItem(
        id = id,
        name = "Track $id",
        artist = "Artist of $id",
        album = "Album $id",
        imageUrl = "art://$id",
        mediaSourceId = "ms-$id",
        durationMs = durationMs,
        normalizationGain = null,
    )

    private fun items(vararg ids: String) = ids.map { item(it) }

    // ── seeds / flows ─────────────────────────────────────────────────────

    @Test
    fun initialStateMatchesAndroidDefaults() {
        val h = newHarness(callStart = false)
        assertEquals(emptyList<AudioQueueItem>(), h.manager.queue.value)
        assertEquals(-1, h.manager.currentIndex.value)
        assertNull(h.manager.currentPlayingItemId.value)
        assertFalse(h.manager.shuffleMode.value)
        assertEquals(0, h.manager.repeatMode.value)
        assertEquals("", h.manager.title.value)
        assertEquals("", h.manager.artist.value)
        assertNull(h.manager.artistId.value)
        assertEquals("", h.manager.album.value)
        assertEquals("", h.manager.albumArtUrl.value)
        assertFalse(h.manager.isPlaying.value)
        assertEquals(0L, h.manager.currentPosition.value)
        assertEquals(0L, h.manager.duration.value)
        assertEquals(1.0f, h.manager.speed.value)
        assertNull(h.manager.playbackError.value)
        assertFalse(h.manager.isLoadingItem.value)
        assertEquals(0L, h.manager.crossfadeDurationMs.value)
        assertNull(h.manager.abLoopStartMs.value)
        assertNull(h.manager.abLoopEndMs.value)
        assertTrue(h.engines.isEmpty(), "engine stays lazy until the first play()")
    }

    @Test
    fun startRestoresPersistedQueueAndStateFromRoom() {
        val h = newHarness(
            preSeed = { dao ->
                dao.insertAll(
                    listOf(
                        AudioQueueEntity(id = "x1", position = 0, name = "Track x1", artist = "A"),
                        AudioQueueEntity(id = "x2", position = 1, name = "Track x2", artist = "B"),
                    ),
                )
                dao.saveState(
                    AudioQueueStateEntity(
                        currentIndex = 1,
                        currentPositionMs = 42_000L,
                        repeatMode = 2,
                        shuffleEnabled = true,
                        playbackSpeed = 1.5f,
                    ),
                )
                Unit
            },
        )
        assertEquals(listOf("x1", "x2"), h.manager.queue.value.map { it.id })
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals(42_000L, h.manager.currentPosition.value)
        assertEquals(2, h.manager.repeatMode.value)
        assertTrue(h.manager.shuffleMode.value)
        assertEquals(1.5f, h.manager.speed.value)
        assertNull(h.manager.currentPlayingItemId.value, "restored index must not claim a playing session")
        assertTrue(h.engines.isEmpty(), "restore never spins up the engine — the first play() does")
    }

    @Test
    fun addToQueueAllPersistsWholeBatchAsOneFullListReplace() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b", "c"))
        // Determinism bonus over the polling variant: the batch is persisted
        // BEFORE addToQueueAll returns — exactly the single-emission intent.
        assertEquals(3, h.dao.rowsSnapshot.size)
        assertEquals(1, h.dao.replaceQueueCalls, "one bulk append = exactly one full-list REPLACE (the O(N²) fix)")
        assertEquals(listOf("a", "b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(-1, h.manager.currentIndex.value, "pure enqueue never moves the cursor")
    }

    @Test
    fun destructiveOpsEmitUndoEventsAndUndoRestoresThePreClearQueue() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b", "c"))
        assertEquals(3, h.dao.rowsSnapshot.size)

        h.manager.removeFromQueue(0)
        assertEquals(QueueUndoEvent.ItemRemoved(item("a")), h.undoEvents.lastOrNull(), "Android pushes ItemRemoved")
        assertEquals(listOf("b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(-1, h.manager.currentIndex.value, "with no current row, removal leaves the cursor at -1")

        h.manager.moveQueueItem(0, 1)
        // After the removal the head row is b; move(b:0→1) snapshots b.
        assertEquals(QueueUndoEvent.ItemMoved(item("b")), h.undoEvents.lastOrNull())
        assertEquals(listOf("c", "b"), h.manager.queue.value.map { it.id })

        h.manager.clearQueue()
        assertEquals(QueueUndoEvent.QueueCleared, h.undoEvents.lastOrNull())
        assertTrue(h.manager.queue.value.isEmpty())
        assertEquals(-1, h.manager.currentIndex.value)

        assertTrue(h.manager.undoLastQueueOperation())
        assertEquals(listOf("c", "b"), h.manager.queue.value.map { it.id }, "undo reapplied the pre-clear queue")
        // Android: null player (nothing ever played here) → applyQueueSnapshot
        // lands the snapshot verbatim; no coerce onto row 0, no reload.
        assertEquals(-1, h.manager.currentIndex.value)
        assertNull(h.manager.currentPlayingItemId.value)
        assertTrue(h.engines.isEmpty())
    }

    @Test
    fun playQueueClearsTheUndoHistoryOfThePreviousQueue() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b"))
        h.manager.clearQueue() // pushes QueueCleared into the undo stack…
        assertTrue(h.manager.undoLastQueueOperation(), "sanity: restore works before the fresh queue")
        h.manager.clearQueue() // destructive again → stack holds that snapshot

        // …a playQueue invalidates it (Android `queueUndoStack.clear()` first line).
        h.manager.playQueue(items("x"), startIndex = 0)
        assertFalse(
            h.manager.undoLastQueueOperation(),
            "undo history from the previous queue must NOT survive a fresh playQueue",
        )
    }

    // ── play() semantics ───────────────────────────────────────────────────

    @Test
    fun playLoadsStartItemReconcilesFlowsAndReportsStart() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 1)

        val e = h.engines.single()
        assertEquals(listOf("a", "b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals("Track b", h.manager.title.value)
        assertEquals("Artist of b", h.manager.artist.value)
        assertEquals("artist-b", h.manager.artistId.value)
        assertEquals("Album b", h.manager.album.value)
        assertEquals("img://b", h.manager.albumArtUrl.value)
        assertEquals(listOf("https://stream.example/b"), e.loadedRequests.map { it.uri })
        assertEquals(0L, e.loadedRequests.single().startPositionMs, "fresh play starts at zero")
        assertEquals(listOf("b" to 0L), h.resolver.resolveCalls.filter { it.first == "b" }, "resolver contract (itemId, pos=0)")

        val start = h.repo.starts.single()
        assertEquals("b", start.itemId)
        assertEquals("ms-b", start.mediaSourceId)
        assertNull(start.startPositionTicks, "zero-position starts omit resume ticks exactly like Android")
    }

    @Test
    fun playSameItemWhileReadyIsANoOp() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 0)
        assertEquals(1, h.engine.loadedRequests.size)
        val startsBefore = h.repo.starts.size

        h.manager.play("a") // same item, engine READY → Android early-returns

        assertEquals(1, h.engine.loadedRequests.size, "READY same-item replay must not reload")
        assertEquals(startsBefore, h.repo.starts.size, "and must not re-report start")
    }

    @Test
    fun playResumesFromServerReportedTicks() {
        val h = newHarness()
        // 120_000_000 ticks = 12 s on Jellyfin's 10 MHz clock (10_000 ticks/ms).
        h.resolver.tracks["r"] = resolvedTrack("r", resumePositionTicks = 120_000_000L)
        h.manager.play("r")

        assertEquals(12_000L, h.engine.loadedRequests.single().startPositionMs, "ticks/10_000 → ms")
        assertEquals(120_000_000L, h.repo.starts.single().startPositionTicks)
        assertTrue(h.repo.stops.isEmpty(), "nothing played before this — no stop report, no rotation")
    }

    @Test
    fun playUnresolvableItemSurfacesErrorWithoutTouchingTheQueue() {
        val h = newHarness()
        h.resolver.unresolved += "gone"
        h.manager.play("gone")

        assertEquals("Failed to load track", h.manager.playbackError.value)
        assertTrue(h.manager.queue.value.isEmpty())
        assertFalse(h.manager.isLoadingItem.value, "loading flag cleared once resolution failed")
    }

    @Test
    fun playAppendsOutOfQueueItemToTheEndAndJumpsToIt() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b"))
        assertEquals(2, h.dao.rowsSnapshot.size)

        h.manager.play("zz")
        assertEquals(listOf("a", "b", "zz"), h.manager.queue.value.map { it.id })
        assertEquals(2, h.manager.currentIndex.value, "out-of-queue play appends at the tail and points there")
        assertEquals("zz", h.manager.currentPlayingItemId.value)
    }

    // ── queue mutations around playback ───────────────────────────────────

    @Test
    fun removeFromQueueCurrentLoadsShiftedInItemWithStopThenRotatedStart() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        h.engine.currentPositionMs = 8_000L
        h.tick(300) // ticker mirrors engine position onto the flow (prevPosTicks source)

        h.manager.removeFromQueue(0)

        assertEquals(listOf("b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value, "removed-current coerces onto the shifted-in row")
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals("https://stream.example/b", h.engine.loadedRequests.last().uri)
        val stop = h.repo.stops.single()
        assertEquals("a", stop.first, "stop reported for the removed-out previous item")
        assertTrue(stop.third > 0)
        val bStart = h.repo.starts.last { it.itemId == "b" }
        assertTrue(bStart.sessionId != stop.second, "session id rotates BEFORE the start report (sync-rotation parity)")
        assertEquals("ms-b", bStart.mediaSourceId)
    }

    @Test
    fun removeFromQueueAroundCurrentRowAdjustsIndexOnlyNeverReloads() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c", "d"), startIndex = 1) // playing b @1
        assertEquals(1, h.engine.loadedRequests.size)
        val loadsBefore = h.engine.loadedRequests.size

        h.manager.removeFromQueue(0) // ABOVE current ("a")
        assertEquals(listOf("b", "c", "d"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value)
        h.manager.removeFromQueue(2) // BELOW current ("d")
        assertEquals(listOf("b", "c"), h.manager.queue.value.map { it.id })
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals(loadsBefore, h.engine.loadedRequests.size, "neighbour removals never reload the player")
    }

    @Test
    fun removeFromQueueCurrentOnLastRowEmptiesQueueParksEngineIdleKeepsMetadata() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0) // playing the ONLY row
        assertEquals("a", h.manager.currentPlayingItemId.value)

        h.manager.removeFromQueue(0) // removing the current last row empties everything

        assertTrue(h.manager.queue.value.isEmpty())
        assertEquals(-1, h.manager.currentIndex.value)
        assertFalse(h.manager.isPlaying.value)
        assertEquals(EngineProbe.IDLE, EngineProbe.of(h), "empty playlist parks the engine idle")
        assertEquals(
            "Track a", h.manager.title.value,
            "playlist-empty parks the player but metadata is kept (Android clear-media-items shape)",
        )
    }

    @Test
    fun clearQueueSnapshotsForUndoAndRestoreJumpsBackAtTheCapturedPosition() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 1)
        assertEquals("b", h.manager.currentPlayingItemId.value)
        h.tick(300)
        h.engine.currentPositionMs = 30_000L
        h.tick(300) // snapshot positionMs comes from live engine position
        val startsBefore = h.repo.starts.size

        h.manager.clearQueue()
        assertTrue(h.manager.queue.value.isEmpty())
        assertEquals(-1, h.manager.currentIndex.value)
        assertEquals(EngineProbe.IDLE, EngineProbe.of(h))

        assertTrue(h.manager.undoLastQueueOperation())
        assertEquals(listOf("a", "b"), h.manager.queue.value.map { it.id })
        assertEquals(1, h.manager.currentIndex.value, "cursor restored to the snapshot row")
        assertEquals(
            30_000L,
            h.engine.loadedRequests.last().startPositionMs,
            "restore replays AT the captured position (setMediaItems(index,pos) parity)",
        )
        assertTrue(h.repo.starts.size > startsBefore, "the restore transition reports start")
        assertEquals("b", h.manager.currentPlayingItemId.value)
    }

    @Test
    fun moveQueueItemRemapsCursorPerAndroidFormulaWithoutReload() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c", "d"), startIndex = 3) // playing d @3
        assertEquals(1, h.engine.loadedRequests.size)
        val loadsBefore = h.engine.loadedRequests.size

        // from(a@0) < current(3), to=1 still left of current → cursor unchanged.
        h.manager.moveQueueItem(0, 1)
        assertEquals(3, h.manager.currentIndex.value)
        // Moving the CURRENT item itself (d sits @3): cursor follows to its new slot.
        h.manager.moveQueueItem(3, 0)
        assertEquals(0, h.manager.currentIndex.value, "moving the current row carries the cursor along")
        assertEquals("d", h.manager.queue.value[0].id)
        // Crossing: moving a right-side row past the cursor pushes the cursor down.
        // [d,b,a,c] cursor@0; move(a@2 → head): from>current && to<=current → cursor+1.
        h.manager.moveQueueItem(2, 0)
        assertEquals(1, h.manager.currentIndex.value, "right-to-left crossing shifts the cursor down")
        assertEquals("d", h.manager.queue.value[1].id, "the PLAYING row tracks the cursor")
        assertEquals(loadsBefore, h.engine.loadedRequests.size, "move is pure state — nothing reloads")
    }

    // ── skips ──────────────────────────────────────────────────────────────

    @Test
    fun skipToNextAdvancesMidQueueNoOpsAtRepeatNoneEndWrapsUnderAll() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)

        h.manager.skipToNext()
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals("a", h.repo.stops.single().first, "advance reports stop(prev)")

        val undosBefore = h.undoEvents.size
        h.manager.skipToNext() // end of queue + RepeatNone → blocked
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals(undosBefore, h.undoEvents.size, "blocked no-op pushes no SkippedToNext snapshot/event")

        h.manager.setRepeatMode(1)
        h.manager.skipToNext()
        assertEquals("a", h.manager.currentPlayingItemId.value, "RepeatAll wraps to head")
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals(QueueUndoEvent.SkippedToNext, h.undoEvents.lastOrNull())
    }

    @Test
    fun skipToPreviousRestartsPastThresholdOtherwiseStepsBackOrWraps() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 1)
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals(1, h.engine.loadedRequests.size)
        val loadsBefore = h.engine.loadedRequests.size

        h.engine.currentPositionMs = 5_000L // past the 3 s default threshold
        h.manager.skipToPrevious()
        assertEquals(0L, h.engine.currentPositionMs, "restart-in-place seek only")
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals(loadsBefore, h.engine.loadedRequests.size, "restart is NOT a reload")

        // Below threshold with a prior row: ordinary step back to head.
        h.engine.currentPositionMs = 500L
        h.manager.skipToPrevious()
        assertEquals(0, h.manager.currentIndex.value)

        // At HEAD without a prior row: blocked under RepeatNone.
        h.manager.skipToPrevious()
        assertEquals(0, h.manager.currentIndex.value)

        // RepeatAll wraps head→tail.
        h.manager.setRepeatMode(1)
        h.manager.skipToPrevious()
        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals(1, h.manager.currentIndex.value)
    }

    @Test
    fun skipToPreviousWithoutAnEngineIsASilentNoOpLikeAndroidNullPlayerPath() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a"))
        assertEquals(1, h.dao.rowsSnapshot.size)
        h.manager.skipToPrevious() // player still null — Android `exoPlayer ?: return`
        assertEquals(-1, h.manager.currentIndex.value, "enqueue never opened a cursor; nothing moves")
        assertTrue(h.engines.isEmpty())
    }

    // ── track-end matrix ───────────────────────────────────────────────────

    @Test
    fun endedMidQueueAutoAdvancesReconcilesAndRotatesSession() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        h.engine.currentPositionMs = 10_000L
        h.tick(300)
        val playedSession = h.repo.starts.first().sessionId

        h.engine.simulateEnded()

        assertEquals("b", h.manager.currentPlayingItemId.value, "natural EOF auto-advances mid-queue")
        assertEquals(1, h.manager.currentIndex.value)
        assertEquals("https://stream.example/b", h.engine.loadedRequests.last().uri)
        val stop = h.repo.stops.single()
        assertEquals("a", stop.first, "natural end reports stop(prev)")
        assertTrue(stop.third > 0)
        assertEquals(playedSession, stop.second, "stop rides the PREVIOUS session id")
        val bStart = h.repo.starts.last { it.itemId == "b" }
        assertTrue(bStart.sessionId != playedSession, "start(next) always uses the ROTATED session id")
    }

    @Test
    fun endedAtRepeatNoneEndParksCursorAndMetadataWithPlayingFlagOff() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)

        h.engine.simulateEnded()

        assertEquals(0, h.manager.currentIndex.value, "end-of-queue under RepeatNone keeps the cursor parked")
        assertEquals("a", h.manager.currentPlayingItemId.value, "metadata kept")
        assertFalse(h.manager.isPlaying.value, "STATE_ENDED flips isPlaying off")
        assertEquals(EngineProbe.ENDED, EngineProbe.of(h))
        assertEquals(1, h.engine.loadedRequests.size, "parked end never reloads anything")
    }

    @Test
    fun repeatOneReplaysSameTrackFromEndedWithoutReload() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        h.manager.setRepeatMode(2)
        val loadsBefore = h.engine.loadedRequests.size

        h.engine.simulateEnded()

        assertEquals(loadsBefore, h.engine.loadedRequests.size, "repeat-one replays via play(), never load()")
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        assertEquals(EngineProbe.READY, EngineProbe.of(h), "V2b play-from-ENDED seeks 0 + unpauses")
        assertTrue(h.manager.isPlaying.value)
        assertEquals(0L, h.manager.currentPosition.value, "replay restarts at position zero")
    }

    @Test
    fun repeatAllAtEndWrapsHeadAndReloadsIt() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 1)
        assertEquals("b", h.manager.currentPlayingItemId.value)
        h.manager.setRepeatMode(1)

        h.engine.simulateEnded()

        assertEquals("a", h.manager.currentPlayingItemId.value, "wrap lands on the head")
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("https://stream.example/a", h.engine.loadedRequests.last().uri, "head RELOADED")
    }

    // ── shuffle / repeat mode surface ──────────────────────────────────────

    @Test
    fun shuffleTogglesRoundTripOverALiveEngineWithoutReload() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 1) // playing b @1
        assertEquals("b", h.manager.currentPlayingItemId.value)
        val loadsBefore = h.engine.loadedRequests.size

        h.manager.toggleShuffle()

        assertTrue(h.manager.shuffleMode.value)
        assertEquals("b", h.manager.queue.value.first().id, "current item shuffles to the head")
        assertEquals(setOf("a", "b", "c"), h.manager.queue.value.map { it.id }.toSet())
        assertEquals(0, h.manager.currentIndex.value)
        assertEquals("b", h.manager.currentPlayingItemId.value)

        h.manager.toggleShuffle()

        assertFalse(h.manager.shuffleMode.value)
        assertEquals(listOf("a", "b", "c"), h.manager.queue.value.map { it.id }, "original order restored")
        assertEquals(1, h.manager.currentIndex.value, "cursor snaps to the restored slot of the playing item")
        assertEquals(loadsBefore, h.engine.loadedRequests.size, "shuffle is state-only on desktop — no reload")
    }

    @Test
    fun shuffleBeforeAnyPlaybackFlipsOnlyTheFlagLikeAndroidNullPlayer() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b", "c"))
        assertEquals(3, h.dao.rowsSnapshot.size)

        h.manager.toggleShuffle()

        assertTrue(h.manager.shuffleMode.value, "the flag always flips")
        assertEquals(
            listOf("a", "b", "c"),
            h.manager.queue.value.map { it.id },
            "but with no live engine the ORDER is untouched (Android `exoPlayer ?: return` parity)",
        )
        assertEquals(-1, h.manager.currentIndex.value)
    }

    @Test
    fun setShuffleModeDedupesAndRepeatModesCycleCoerce() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b"))
        assertEquals(2, h.dao.rowsSnapshot.size)

        h.manager.setShuffleMode(false) // already off → dedupe no-op
        assertFalse(h.manager.shuffleMode.value)
        h.manager.cycleRepeatMode()
        assertEquals(1, h.manager.repeatMode.value)
        h.manager.cycleRepeatMode()
        assertEquals(2, h.manager.repeatMode.value)
        h.manager.cycleRepeatMode()
        assertEquals(0, h.manager.repeatMode.value, "(mode+1)%3 wraps")
        h.manager.setRepeatMode(7)
        assertEquals(2, h.manager.repeatMode.value, "coerced into 0..2")
        h.manager.setRepeatMode(-4)
        assertEquals(0, h.manager.repeatMode.value)
    }

    // ── playFromQueue ──────────────────────────────────────────────────────

    @Test
    fun playFromQueueCrossIndexReloadsSameIndexSeeksZeroWithoutReload() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)

        h.manager.playFromQueue(2)
        assertEquals("c", h.manager.currentPlayingItemId.value)
        assertEquals(2, h.manager.currentIndex.value)
        assertEquals("https://stream.example/c", h.engine.loadedRequests.last().uri)
        val cLoads = h.engine.loadedRequests.count { it.uri.endsWith("/c") }

        h.manager.playFromQueue(2) // same-index click: seek(current,0)+play, NOT a reload
        assertEquals(cLoads, h.engine.loadedRequests.count { it.uri.endsWith("/c") }, "same-index click never reloads")
        assertEquals(EngineProbe.READY, EngineProbe.of(h))
        assertTrue(h.manager.isPlaying.value)
        assertEquals(0L, h.manager.currentPosition.value, "click restarts the current track at zero")
    }

    @Test
    fun playFromQueueWithoutEngineMovesTheIndexOnly() {
        val h = newHarness()
        h.manager.addToQueueAll(items("a", "b"))
        assertEquals(2, h.dao.rowsSnapshot.size)

        h.manager.playFromQueue(1)

        assertEquals(1, h.manager.currentIndex.value, "Android assigns _currentIndex before the null-player return")
        assertTrue(h.engines.isEmpty(), "and no engine spins up")
    }

    // ── A→B loop / seek / progress trace ───────────────────────────────────

    @Test
    fun abLoopCycleTracksEnginePositionsAndClears() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        h.engine.currentPositionMs = 12_000L

        h.manager.cycleAbLoop() // set A (reads the ENGINE position directly)
        assertEquals(12_000L, h.manager.abLoopStartMs.value)

        h.engine.currentPositionMs = 18_000L
        h.manager.cycleAbLoop() // set B
        assertEquals(18_000L, h.manager.abLoopEndMs.value)

        h.manager.cycleAbLoop() // both set → clear
        assertNull(h.manager.abLoopStartMs.value)
        assertNull(h.manager.abLoopEndMs.value)
    }

    @Test
    fun progressReporterStreamsOnThePlaySessionAndDedupesPausedStalePositions() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        h.engine.currentPositionMs = 4_000L

        h.tick(80) // two reporter cycles at the injected 40 ms cadence
        val playingRows = h.repo.progresses.filter { !it.isPaused }
        assertTrue(playingRows.size >= 2, "progress streams on cadence")
        val session = h.repo.starts.single().sessionId
        assertTrue(playingRows.all { it.sessionId == session }, "progress rides the current play session id")
        assertTrue(playingRows.all { it.positionTicks == 40_000_000L }, "10 kHz math: ms*10_000")

        // Paused + unmoving → exactly ONE paused row ever flushes; duplicates dedupe.
        h.engine.pause()
        h.tick(200)
        val pausedRows = h.repo.progresses.count { it.isPaused }
        assertEquals(1, pausedRows, "identical paused positions dedupe via lastPausedPositionTicks")
        val totalRows = h.repo.progresses.size
        h.tick(200)
        assertEquals(totalRows, h.repo.progresses.size, "still zero NEW rows while paused and unmoving")
    }

    @Test
    fun optimisticSeekPublishesImmediatelyAndClampsNegatives() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0)
        assertEquals("a", h.manager.currentPlayingItemId.value)
        // The ticker is virtual now: without an explicit tick it cannot fight
        // the optimistic write, which is precisely the pin below.
        h.manager.seekTo(90_000L)
        assertEquals(90_000L, h.manager.currentPosition.value, "seek bar snaps immediately (Android rationale)")
        assertEquals(90_000L, h.engine.currentPositionMs)

        h.manager.seekTo(-5L)
        assertEquals(0L, h.manager.currentPosition.value, "negative clamps to zero")

        h.tick(300) // next poll cycle echoes the engine's authoritative value
        assertEquals(0L, h.manager.currentPosition.value, "poll reconciles to the engine state after the clamp")
    }

    // ── effects wiring (wave 14C): state → engine af config ──────────────

    @Test
    fun engineCreationPushesInitialEffectsSnapshotAndMutationsRepush() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0)
        // Engine-create snapshot + the per-track ReplayGain context push that
        // play() performs right after resolution (deterministic inline).
        assertEquals(2, h.engine.appliedConfigs.size)
        assertFalse(h.engine.appliedConfigs[0].audioEffects.equalizerEnabled)

        h.effects.toggleEqualizer()

        assertEquals(3, h.engine.appliedConfigs.size, "live mutation repushes the snapshot")
        assertTrue(h.engine.appliedConfigs.last().audioEffects.equalizerEnabled)
    }

    @Test
    fun replayGainContextFlowsThroughTheSnapshotWithAndroidAlbumShuffleRule() {
        val h = newHarness()
        h.effects.setReplayGainMode(AudioNormalizationMode.TRACK)
        h.manager.playQueue(listOf(item("a").copy(normalizationGain = 2.5f)), startIndex = 0)
        // TRACK: item gain + pre-amp (0) — AudioEffectsProcessor.applyReplayGain.
        assertEquals(2.5f, h.engine.appliedConfigs.last().audioEffects.replayGainEffectiveDb)

        // ALBUM + shuffled pins the gain at exactly 0.
        h.manager.toggleShuffle() // single-row queue: flag flips, order untouched
        h.effects.setReplayGainMode(AudioNormalizationMode.ALBUM)
        assertEquals(0f, h.engine.appliedConfigs.last().audioEffects.replayGainEffectiveDb)
    }

    @Test
    fun channelMixAndPitchStateReachTheEngineConfig() {
        val h = newHarness()
        h.manager.playQueue(items("a"), startIndex = 0)

        h.effects.setChannelMix(ChannelMixMode.MONO, enabled = true)
        h.effects.setPitchSemitones(2f)

        val fx = h.engine.appliedConfigs.last().audioEffects
        assertEquals(ChannelMixMode.MONO, fx.channelMixMode)
        assertTrue(fx.channelMixEnabled)
        assertEquals(2f, fx.pitchSemitones)
    }

    @Test
    fun autoEqByGenreResolvesGenrePresetOntoTheEqualizerLikeAndroid() {
        val h = newHarness()
        h.effects.setAutoEqByGenre(true)
        h.effects.applyAutoEqForGenre(listOf("Space Rock"))
        assertEquals(EqualizerPreset.ROCK, h.effects.equalizerPreset.value)
        assertEquals(
            EqualizerSettings(EqualizerPreset.ROCK.bandLevels()),
            h.effects.equalizerSettings.value,
        )

        // Flag off → the resolver is inert (Android verbatim).
        h.effects.setAutoEqByGenre(false)
        h.effects.resetEqualizer()
        h.effects.applyAutoEqForGenre(listOf("Rock"))
        assertEquals(EqualizerPreset.FLAT, h.effects.equalizerPreset.value)
    }

    // ── next-item prefetch (wave 14C: "pre-warm is next-item-only") ──────

    @Test
    fun prefetchResolvesTheNextItemBehindCurrentWithoutLoadingTheEngine() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 0)
        val loadsBefore = h.engine.loadedRequests.size
        val resolvesBefore = h.resolver.resolveCalls.size // just "a"

        h.tick(2_100) // past the 2 s prefetch delay (virtual time)

        assertEquals(
            listOf("b" to 0L),
            h.resolver.resolveCalls.drop(resolvesBefore),
            "exactly the NEXT row resolved, at position zero",
        )
        assertEquals(loadsBefore, h.engine.loadedRequests.size, "prefetch never loads the engine")
    }

    @Test
    fun advanceConsumesThePrefetchedTrackWithoutASecondResolve() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b"), startIndex = 0)
        h.tick(2_100) // b now cached
        assertEquals(1, h.resolver.resolveCalls.count { it.first == "b" })

        h.engine.simulateEnded() // auto-advance onto b

        assertEquals("b", h.manager.currentPlayingItemId.value)
        assertEquals(
            1,
            h.resolver.resolveCalls.count { it.first == "b" },
            "the advance must consume the prefetch instead of resolving again",
        )
    }

    @Test
    fun queueMutationCancelsTheInFlightPrefetch() {
        val h = newHarness()
        h.manager.playQueue(items("a", "b", "c"), startIndex = 0)

        h.manager.removeFromQueue(1) // remove b — the item about to be prefetched
        h.tick(2_100)
        assertEquals(
            0,
            h.resolver.resolveCalls.count { it.first == "b" },
            "the mutation must cancel the scheduled prefetch (wasted-fetch degradation only)",
        )

        // Advance still lands correctly via the ordinary resolve path.
        h.engine.simulateEnded()
        assertEquals("c", h.manager.currentPlayingItemId.value)
        assertEquals(1, h.resolver.resolveCalls.count { it.first == "c" })
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** Readable mirror of the engine state enum for assertion messages. */
    private enum class EngineProbe { IDLE, BUFFERING, READY, ENDED, ERROR;

        companion object {
            fun of(h: Harness): EngineProbe = EngineProbe.valueOf(h.engine.playbackState.value.name)
        }
    }
}
