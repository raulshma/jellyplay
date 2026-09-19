package com.raulshma.jellyplay.core.data.playback

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
 * Chassis pin for [AudioQueueStateCore] — the commonMain fold of the
 * desktop manager's queue-state machine, driven here over a RECORDING
 * [EngineDispatch] (no engine, no media): every test asserts the DECISIONS
 * (flow writes, undo snapshots/events, hook ordering) and the resulting
 * PORT TRAFFIC (prepare/play/stop/seek/speed command sequences), which is
 * exactly the surface the fold extracted. The manager-level suite
 * (`DesktopAudioQueueManagerTest`, jvmTest) pins the same choreography
 * end-to-end through the real wiring — including resolution, persistence
 * and the ticker — so the two suites deliberately overlap: this one can
 * see the port commands the manager suite can only infer.
 *
 * DETERMINISM MODEL: the manager suite's — an [UnconfinedTestDispatcher]
 * scope makes the transition choreography's reporting block complete
 * inline within the mutation that triggered it, so assertions are direct
 * reads.
 *
 * Android's [AudioPlaybackManager] is not an adopter (declared divergence
 * in the core's KDoc); its chassis stays pinned by the androidHostTest
 * suites.
 */
class AudioQueueStateCoreTest {

    /** Records every port call; `enginePositionMs` feeds the snapshot reads. */
    private class RecordingDispatch : EngineDispatch {
        var live = true
        override val isLive: Boolean get() = live

        val prepared = mutableListOf<Pair<AudioQueueItem, Long>>()
        val commands = mutableListOf<String>()
        var enginePositionMs: Long? = null

        override fun prepare(item: AudioQueueItem, startPositionMs: Long) {
            prepared += item to startPositionMs
        }

        override fun play() {
            commands += "play"
        }

        override fun pause() {
            commands += "pause"
        }

        override fun stop() {
            commands += "stop"
        }

        override fun seekTo(positionMs: Long) {
            commands += "seek:$positionMs"
        }

        override fun setPlaybackSpeed(speed: Float) {
            commands += "speed:$speed"
        }
    }

    /**
     * One wired core over the recording dispatch on the test scheduler.
     * Hook counters make the callback ORDERING visible; the reporter is the
     * real shared one over the fake repository (session rotation is part of
     * what the transition choreography pins).
     */
    private inner class Harness {
        val dispatch = RecordingDispatch()
        val repo = FakePlaybackRepository()
        val undoEvents = mutableListOf<QueueUndoEvent>()

        private val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val lyrics = AudioLyricsManager(FakeLyricsRepository()).also { it.initialize(scope) }

        // Explicit types: reporter's provider lambdas reference `core`
        // (declared below — they run later, at report time), and the
        // compiler needs the anchors to break the initializer cycle.
        val reporter: AudioProgressReporter = AudioProgressReporter(
            scope = scope,
            playbackRepository = repo,
            remoteSessionActive = { false },
            positionMsProvider = { dispatch.enginePositionMs },
            isPlayingProvider = { false },
            itemIdProvider = { core.currentItemId },
            playSessionIdProvider = { core.playSessionId },
            playSessionIdSetter = { core.playSessionId = it },
            reportIntervalMs = 40L,
        )

        var exhausted = 0
        var invalidated = 0
        var shuffleNotifications = 0
        val playRequests = mutableListOf<String>()

        val core: AudioQueueStateCore = AudioQueueStateCore(
            scope = scope,
            playbackRepository = repo,
            lyricsManager = lyrics,
            progressReporter = reporter,
            dispatch = dispatch,
            enginePositionMs = { dispatch.enginePositionMs },
            onQueueShapeInvalidated = { invalidated++ },
            onQueueExhausted = { exhausted++ },
            onShuffleModeChanged = { shuffleNotifications++ },
            onPlayRequested = { playRequests += it },
        ).also { core ->
            scope.launch {
                core.undoEvents.collect { undoEvents += it }
            }
        }

        fun close() {
            scope.cancel()
        }
    }

    private val openHarnesses = mutableListOf<Harness>()

    private fun newHarness(): Harness = Harness().also { openHarnesses += it }

    @AfterTest
    fun tearDownHarnesses() {
        openHarnesses.forEach { it.close() }
        openHarnesses.clear()
    }

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

    /** Seeds a queue + cursor directly (the adapter's start() restore shape). */
    private fun Harness.seed(rows: List<AudioQueueItem>, index: Int) {
        core._queue.value = rows
        core._currentIndex.value = index
    }

    // ── initial state ───────────────────────────────────────────────────────

    @Test
    fun initialStateMatchesTheManagerDefaults() {
        val h = newHarness()
        assertEquals(emptyList<AudioQueueItem>(), h.core.queue.value)
        assertEquals(-1, h.core.currentIndex.value)
        assertNull(h.core.nowPlayingTracker.currentPlayingItemId.value)
        assertFalse(h.core.shuffleMode.value)
        assertEquals(0, h.core.repeatMode.value)
        assertFalse(h.core.isPlaying.value)
        assertEquals(0L, h.core.currentPosition.value)
        assertEquals(0L, h.core.duration.value)
        assertEquals(1.0f, h.core.speed.value)
        assertNull(h.core.playbackError.value)
        assertFalse(h.core.isLoadingItem.value)
        assertEquals(0L, h.core.crossfadeDurationMs.value)
        assertNull(h.core.currentItemId, "no session claim until something plays")
        assertTrue(h.undoEvents.isEmpty())
    }

    // ── playQueue / appends ─────────────────────────────────────────────────

    @Test
    fun playQueueRoutesTheStartItemToThePlayHookAndInvalidatesPriorContext() {
        val h = newHarness()
        h.core.playQueue(items("a", "b"), startIndex = 1)
        assertEquals(listOf("b"), h.playRequests)
        assertEquals(listOf("a", "b"), h.core.queue.value.map { it.id })
        assertEquals(1, h.core.currentIndex.value)
        assertEquals(1, h.invalidated, "a fresh queue invalidates the previous shape")

        // Out-of-bounds start: the queue lands, nothing plays (Android shape).
        h.core.playQueue(items("x"), startIndex = 7)
        assertEquals(listOf("x"), h.core.queue.value.map { it.id })
        assertEquals(7, h.core.currentIndex.value)
        assertEquals(1, h.playRequests.size, "no further play request")
    }

    @Test
    fun appendsNeverMoveTheCursorAndBulkSkipsEmpty() {
        val h = newHarness()
        h.seed(items("a"), index = 0)
        h.core.addToQueue(item("b"))
        h.core.addToQueueAll(items("c", "d"))
        h.core.addToQueueAll(emptyList())
        assertEquals(listOf("a", "b", "c", "d"), h.core.queue.value.map { it.id })
        assertEquals(0, h.core.currentIndex.value, "pure enqueue never moves the cursor")
        assertEquals(2, h.invalidated, "one invalidation per non-empty mutation (empty bulk adds none)")
    }

    // ── skips ───────────────────────────────────────────────────────────────

    @Test
    fun skipToNextAdvancesPreparesTheRowAndBlockedTailPushesNoUndo() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 0)

        h.core.skipToNext()

        assertEquals(1, h.core.currentIndex.value)
        assertEquals(listOf("b" to 0L), h.dispatch.prepared.map { it.first.id to it.second })
        assertEquals(listOf<QueueUndoEvent>(QueueUndoEvent.SkippedToNext), h.undoEvents)

        val undoCount = h.undoEvents.size
        h.core.skipToNext() // tail under RepeatNone → blocked
        assertEquals(1, h.core.currentIndex.value)
        assertEquals(undoCount, h.undoEvents.size, "blocked no-op: no snapshot, no event")
        assertEquals(1, h.dispatch.prepared.size, "and no prepare")

        h.core.setRepeatMode(AudioQueuePolicy.REPEAT_ALL)
        h.core.skipToNext() // wraps to head
        assertEquals(0, h.core.currentIndex.value)
        assertEquals("a", h.dispatch.prepared.last().first.id)
        assertEquals(1, h.invalidated, "repeat flip invalidates the next-item context")
    }

    @Test
    fun skipToPreviousRestartsPastThresholdOtherwiseStepsOrWraps() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 1)

        h.dispatch.enginePositionMs = 5_000L // past the 3 s default threshold
        h.core.skipToPrevious()
        assertEquals(1, h.core.currentIndex.value, "restart-in-place never moves the cursor")
        assertEquals(listOf("seek:0"), h.dispatch.commands, "seek-to-zero is the only port traffic")
        assertTrue(h.undoEvents.isEmpty(), "restart pushes no undo snapshot")
        assertTrue(h.dispatch.prepared.isEmpty(), "restart is NOT a reload")

        h.dispatch.enginePositionMs = 500L
        h.core.skipToPrevious()
        assertEquals(0, h.core.currentIndex.value)
        assertEquals("a", h.dispatch.prepared.last().first.id)

        h.core.skipToPrevious() // head under RepeatNone → blocked
        assertEquals(0, h.core.currentIndex.value)

        h.core.setRepeatMode(AudioQueuePolicy.REPEAT_ALL)
        h.core.skipToPrevious() // wraps head→tail
        assertEquals(1, h.core.currentIndex.value)
        assertEquals("b", h.dispatch.prepared.last().first.id)
    }

    @Test
    fun skipToPreviousWithoutAnEngineIsASilentNoOp() {
        val h = newHarness()
        h.seed(items("a"), index = 0)
        h.dispatch.live = false
        h.dispatch.enginePositionMs = null
        h.core.skipToPrevious()
        assertEquals(0, h.core.currentIndex.value)
        assertTrue(h.dispatch.commands.isEmpty(), "no engine → no restart seek either (the null-player gate)")
    }

    // ── remove / clear / move ───────────────────────────────────────────────

    @Test
    fun removeCurrentTransitionsToTheShiftedInRowAndDrainingParksTheEngine() {
        val h = newHarness()
        h.seed(items("a", "b", "c"), index = 0)

        h.core.removeFromQueue(0) // current
        assertEquals(listOf("b", "c"), h.core.queue.value.map { it.id })
        assertEquals(0, h.core.currentIndex.value, "coerced onto the shifted-in row")
        assertEquals("b", h.dispatch.prepared.single().first.id)
        assertEquals(listOf<QueueUndoEvent>(QueueUndoEvent.ItemRemoved(item("a"))), h.undoEvents)

        h.core.removeFromQueue(1) // below current: pure state
        assertEquals(listOf("b"), h.core.queue.value.map { it.id })
        assertEquals(0, h.core.currentIndex.value)
        assertEquals(1, h.dispatch.prepared.size, "neighbour removal never reloads")

        h.core.removeFromQueue(0) // drains the queue
        assertTrue(h.core.queue.value.isEmpty())
        assertEquals(-1, h.core.currentIndex.value)
        assertFalse(h.core.isPlaying.value)
        assertEquals("stop", h.dispatch.commands.single(), "empty playlist parks the engine idle")
    }

    @Test
    fun removeAboveCurrentShiftsTheCursorDownWithoutTraffic() {
        val h = newHarness()
        h.seed(items("a", "b", "c"), index = 1)
        h.core.removeFromQueue(0)
        assertEquals(listOf("b", "c"), h.core.queue.value.map { it.id })
        assertEquals(0, h.core.currentIndex.value)
        assertTrue(h.dispatch.prepared.isEmpty() && h.dispatch.commands.isEmpty())
    }

    @Test
    fun clearQueueParksAndUndoRestoresAtTheSnapshotPosition() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 1)
        h.dispatch.enginePositionMs = 30_000L

        h.core.clearQueue()
        assertTrue(h.core.queue.value.isEmpty())
        assertEquals(-1, h.core.currentIndex.value)
        assertEquals("stop", h.dispatch.commands.single())
        assertEquals(QueueUndoEvent.QueueCleared, h.undoEvents.single())

        assertTrue(h.core.undoLastQueueOperation())
        assertEquals(listOf("a", "b"), h.core.queue.value.map { it.id })
        assertEquals(1, h.core.currentIndex.value)
        assertEquals("b" to 30_000L, h.dispatch.prepared.single().let { it.first.id to it.second },
            "restore replays AT the engine-position snapshot (setMediaItems(index, pos) parity)")
    }

    @Test
    fun undoWithoutAnEngineLandsTheSnapshotVerbatim() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 0)
        h.core.clearQueue()
        h.dispatch.live = false
        assertTrue(h.core.undoLastQueueOperation())
        assertEquals(listOf("a", "b"), h.core.queue.value.map { it.id })
        assertEquals(0, h.core.currentIndex.value)
        assertTrue(h.dispatch.prepared.isEmpty(), "no engine → no seek/reconcile pass at all")
        assertFalse(h.core.undoLastQueueOperation(), "stack drained")
    }

    @Test
    fun moveQueueItemRemapsTheCursorWithNoEngineTraffic() {
        val h = newHarness()
        h.seed(items("a", "b", "c", "d"), index = 3)
        h.core.moveQueueItem(3, 0) // the current row itself
        assertEquals(0, h.core.currentIndex.value)
        assertEquals("d", h.core.queue.value[0].id)
        h.core.moveQueueItem(2, 0) // [d,a,b,c]: right-to-left crossing → cursor +1
        assertEquals(1, h.core.currentIndex.value)
        assertEquals("b", h.core.queue.value[0].id)
        h.core.moveQueueItem(0, 0) // no-op rejected
        assertEquals(listOf<QueueUndoEvent>(QueueUndoEvent.ItemMoved(item("d")), QueueUndoEvent.ItemMoved(item("b"))), h.undoEvents)
        assertTrue(h.dispatch.prepared.isEmpty() && h.dispatch.commands.isEmpty(), "pure state")
    }

    // ── shuffle ─────────────────────────────────────────────────────────────

    @Test
    fun shuffleRoundTripsAroundTheCurrentRowAndFiresTheContextHookBeforeTheGate() {
        val h = newHarness()
        h.seed(items("a", "b", "c"), index = 1)
        h.core.nowPlayingTracker.publishQueueItem(item("b"))
        h.core.currentItemId = "b"

        h.core.toggleShuffle()

        assertTrue(h.core.shuffleMode.value)
        assertEquals("b", h.core.queue.value.first().id, "current item shuffles to the head")
        assertEquals(setOf("a", "b", "c"), h.core.queue.value.map { it.id }.toSet())
        assertEquals(0, h.core.currentIndex.value)
        assertEquals(1, h.shuffleNotifications, "the flag flip fires the context hook exactly once")

        h.core.toggleShuffle()

        assertFalse(h.core.shuffleMode.value)
        assertEquals(listOf("a", "b", "c"), h.core.queue.value.map { it.id }, "original order restored")
        assertEquals(1, h.core.currentIndex.value, "cursor snaps to the restored slot of the playing item")
        assertEquals(2, h.shuffleNotifications)
    }

    @Test
    fun shuffleWithoutAnEngineFlipsOnlyTheFlag() {
        val h = newHarness()
        h.seed(items("a", "b", "c"), index = -1)
        h.dispatch.live = false
        h.core.toggleShuffle()
        assertTrue(h.core.shuffleMode.value, "the flag always flips")
        assertEquals(listOf("a", "b", "c"), h.core.queue.value.map { it.id },
            "but the ORDER is untouched (the null-player gate)")
        assertEquals(1, h.shuffleNotifications, "the context hook fires BEFORE the gate (Android order)")
        assertEquals(0, h.invalidated, "and the gate holds the shape invalidation")
    }

    @Test
    fun shuffleModeDedupesAndRepeatCyclesWithCoercion() {
        val h = newHarness()
        h.core.setShuffleMode(false) // already off → no-op
        assertFalse(h.core.shuffleMode.value)
        assertEquals(0, h.shuffleNotifications)
        h.core.cycleRepeatMode()
        h.core.cycleRepeatMode()
        h.core.cycleRepeatMode()
        assertEquals(0, h.core.repeatMode.value, "(mode+1)%3 wraps")
        h.core.setRepeatMode(7)
        assertEquals(2, h.core.repeatMode.value)
        h.core.setRepeatMode(-4)
        assertEquals(0, h.core.repeatMode.value)
    }

    // ── playFromQueue / ended matrix ────────────────────────────────────────

    @Test
    fun playFromQueueSameIndexRestartsInPlaceCrossIndexTransitions() {
        val h = newHarness()
        h.seed(items("a", "b", "c"), index = 0)

        h.core.playFromQueue(2)
        assertEquals("c", h.dispatch.prepared.single().first.id)

        h.core.playFromQueue(2) // same index → seek+play, no reload
        assertEquals(1, h.dispatch.prepared.size)
        assertEquals(listOf("seek:0", "play"), h.dispatch.commands)
    }

    @Test
    fun endedMatrixReplaysUnderOneAdvancesMidQueueAndParksAtTheTail() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 0)

        h.core.setRepeatMode(AudioQueuePolicy.REPEAT_ONE)
        h.core.onEngineEnded()
        assertEquals(listOf("play"), h.dispatch.commands, "repeat-one replays via play(), never prepare")
        assertTrue(h.dispatch.prepared.isEmpty())

        h.core.setRepeatMode(AudioQueuePolicy.REPEAT_NONE)
        h.core.onEngineEnded()
        assertEquals("b", h.dispatch.prepared.single().first.id, "mid-queue natural EOF advances")

        h.core.onEngineEnded() // tail under RepeatNone → park
        assertFalse(h.core.isPlaying.value)
        assertEquals(1, h.core.currentIndex.value, "cursor stays parked on the ended item")
        assertEquals(1, h.dispatch.prepared.size, "parked end never prepares anything")
        assertEquals(1, h.exhausted, "the exhaustion hook fired exactly once")
    }

    // ── transition choreography (metadata + reporting) ──────────────────────

    @Test
    fun transitionsPublishQueueItemMetadataReportStopThenStartAndRotateTheSession() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 0)
        h.core.currentItemId = "a"
        h.core.nowPlayingTracker.publishDetail("a", "Track a", "Artist of a", "artist-a", "Album a", "img://a")
        h.core._currentPosition.value = 10_000L // the stop-report tick source
        val sessionBefore = h.core.playSessionId

        h.core.skipToNext()

        // Queue-item publish shape: five fields from the row, artistId kept.
        assertEquals("b", h.core.nowPlayingTracker.currentPlayingItemId.value)
        assertEquals("Track b", h.core.nowPlayingTracker.title.value)
        assertEquals("Artist of b", h.core.nowPlayingTracker.artist.value)
        assertEquals("Album b", h.core.nowPlayingTracker.album.value)
        assertEquals("artist-a", h.core.nowPlayingTracker.artistId.value,
            "queue transitions leave artistId untouched (AudioQueueItem carries none)")

        val stop = h.repo.stops.single()
        assertEquals("a", stop.first, "stop reported for the previous item")
        assertEquals(100_000_000L, stop.third, "ticks = positionMs * 10_000")
        assertEquals(sessionBefore, stop.second, "stop rides the PREVIOUS session id")

        val start = h.repo.starts.single()
        assertEquals("b", start.itemId)
        assertTrue(start.sessionId != sessionBefore, "start(next) always uses the ROTATED session id")
        assertEquals("b", h.dispatch.prepared.single().first.id, "prepare runs after the reporting launch")
    }

    @Test
    fun transitionsWithoutAnEngineMoveOnlyTheIndex() {
        val h = newHarness()
        h.seed(items("a", "b"), index = 0)
        h.dispatch.live = false
        h.core.playFromQueue(1)
        assertEquals(1, h.core.currentIndex.value)
        assertNull(h.core.nowPlayingTracker.currentPlayingItemId.value,
            "no metadata reconciliation without a live engine")
        assertTrue(h.repo.starts.isEmpty() && h.repo.stops.isEmpty())
    }

    // ── transport + engine events ───────────────────────────────────────────

    @Test
    fun seekPublishesOptimisticallyClampsAndSpeedRidesThePort() {
        val h = newHarness()
        h.core.seekTo(90_000L)
        assertEquals(90_000L, h.core.currentPosition.value)
        assertEquals(listOf("seek:90000"), h.dispatch.commands)

        h.core.seekTo(-5L)
        assertEquals(0L, h.core.currentPosition.value, "negative clamps to zero")
        assertEquals("seek:0", h.dispatch.commands.last())

        h.core.changePlaybackSpeed(1.5f)
        assertEquals(1.5f, h.core.speed.value)
        assertEquals("speed:1.5", h.dispatch.commands.last())
    }

    @Test
    fun engineEventCallbacksMirrorOntoTheFlows() {
        val h = newHarness()
        h.core.onEnginePlayingChanged(true)
        assertTrue(h.core.isPlaying.value)
        h.core.onEngineError("boom")
        assertEquals("boom", h.core.playbackError.value)
        h.core.onEnginePlayingChanged(false)
        assertFalse(h.core.isPlaying.value)
    }

    // ── adapter helpers ─────────────────────────────────────────────────────

    @Test
    fun playedItemAppendJumpsTheCursorToTheTail() {
        val h = newHarness()
        h.seed(items("a"), index = 0)
        h.core.appendPlayedItem(item("zz"))
        assertEquals(listOf("a", "zz"), h.core.queue.value.map { it.id })
        assertEquals(1, h.core.currentIndex.value)
        assertEquals("zz", h.core.currentItemOrNull()?.id)
    }

    @Test
    fun releaseResetClearsDisplayButKeepsTheArtistId() {
        val h = newHarness()
        h.core.currentItemId = "a"
        h.core.nowPlayingTracker.publishDetail("a", "Track a", "Artist of a", "artist-a", "Album a", "img://a")
        h.core._currentPosition.value = 5_000L
        h.core._duration.value = 180_000L

        h.core.onEngineReleased()

        assertNull(h.core.currentItemId)
        assertNull(h.core.nowPlayingTracker.currentPlayingItemId.value)
        assertEquals("", h.core.nowPlayingTracker.title.value)
        assertEquals("artist-a", h.core.nowPlayingTracker.artistId.value,
            "declared delta: clear() never resets artistId")
        assertEquals(0L, h.core.currentPosition.value)
        assertEquals(0L, h.core.duration.value)
    }
}
