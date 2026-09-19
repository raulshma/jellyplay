package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the core-owned position reconcile and item-load handshake of
 * [SyncPlayPlaybackCore]:
 *
 *  1. [SyncPlayPlaybackCore.ReconcileLane] keeps the two DECLARED per-lane
 *     tolerance variants (500 ms scheduled-unpause echo gate vs. 300 ms
 *     queue-update) — deliberately not unified;
 *  2. `reconcileToServerPosition` (the former bridge inline copy) seeks only
 *     beyond the lane's tolerance, clamps to the media duration, and mirrors
 *     the group's play/pause state;
 *  3. `scheduleUnpause`'s no-op-echo gate reuses the SCHEDULED_UNPAUSE
 *     tolerance: within it nothing moves (no seek, no chip pulse); beyond it
 *     the engine is landed on the server position;
 *  4. the `beginPendingItemLoad` → STATE_READY-clear lifecycle: commands drop
 *     while the item load is pending and apply again after READY clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayPlaybackCoreReconcileTest {

    private val scheduler = StandardTestDispatcher()
    private val timeSyncManager: TimeSyncManager = mockk()
    private val controller: SyncPlayController = mockk(relaxed = true)
    private val castStore: SyncPlayCastStore = mockk()

    /**
     * Fixed fake remote clock: with `whenMs == remoteNow` the estimate's
     * elapsed term is 0, so the projected position equals the raw ticks —
     * every tolerance comparison below is exact.
     */
    private val fixedNowMs = 1_000_000L

    private class RecordingCallbacks(
        var position: Long = 0L,
        var playing: Boolean = false,
        var duration: Long = 10_000_000L,
    ) : PlaybackCoreCallbacks {
        val seeks = mutableListOf<Long>()
        var playCount = 0
        var pauseCount = 0
        val syncStates = mutableListOf<Pair<Boolean, Boolean>>()

        override fun localPlay() { playCount++ }
        override fun localPause() { pauseCount++ }
        override fun localSeek(positionMs: Long) { seeks += positionMs }
        override fun setPlaybackRate(rate: Float) {}
        override fun currentPositionMs(): Long = position
        override fun durationMs(): Long = duration
        override fun isPlaying(): Boolean = playing
        override fun isBuffering(): Boolean = false
        override fun onSyncStateChanged(synced: Boolean, syncing: Boolean) {
            syncStates += synced to syncing
        }
    }

    private lateinit var core: SyncPlayPlaybackCore

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(scheduler)
        every { timeSyncManager.remoteNow() } returns fixedNowMs
        every { timeSyncManager.toLocal(any()) } answers { firstArg<Long>() }
        every { castStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
        timeSyncManager.remoteNow()
        timeSyncManager.toLocal(0L)
        core = SyncPlayPlaybackCore(timeSyncManager, controller, castStore)
    }

    @AfterTest
    fun tearDown() {
        core.reset()
        Dispatchers.resetMain()
    }

    private fun msToTicks(ms: Long) = TimeSyncManager.msToTicks(ms)

    // ── ReconcileLane variants ────────────────────────────────────────────────

    @Test
    fun `reconcile lane tolerances stay the declared per-lane variants`() {
        assertEquals(500L, SyncPlayPlaybackCore.ReconcileLane.SCHEDULED_UNPAUSE.toleranceMs)
        assertEquals(300L, SyncPlayPlaybackCore.ReconcileLane.QUEUE_UPDATE.toleranceMs)
    }

    // ── reconcileToServerPosition (QUEUE_UPDATE lane) ─────────────────────────

    @Test
    fun `queue-update reconcile does not seek within its 300ms tolerance but mirrors playback`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 0L, playing = false)
        core.setCallbacks(cb)

        // 200ms of drift: under the 300ms queue-update tolerance → no seek,
        // but the group is playing and the engine is paused → play mirror.
        core.reconcileToServerPosition(
            serverTicks = msToTicks(200L),
            whenMs = fixedNowMs,
            lane = SyncPlayPlaybackCore.ReconcileLane.QUEUE_UPDATE,
            groupIsPlaying = true,
        )
        advanceUntilIdle()

        assertEquals(emptyList(), cb.seeks)
        assertEquals(1, cb.playCount)
        assertEquals(0, cb.pauseCount)
    }

    @Test
    fun `queue-update reconcile seeks beyond its 300ms tolerance`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 0L, playing = false)
        core.setCallbacks(cb)

        // 400ms of drift: over the tolerance → one seek to the estimated
        // (clamped) server position, plus the play mirror.
        core.reconcileToServerPosition(
            serverTicks = msToTicks(400L),
            whenMs = fixedNowMs,
            lane = SyncPlayPlaybackCore.ReconcileLane.QUEUE_UPDATE,
            groupIsPlaying = true,
        )
        advanceUntilIdle()

        assertEquals(listOf(400L), cb.seeks)
        assertEquals(1, cb.playCount)
    }

    @Test
    fun `queue-update reconcile clamps the estimated position to the media duration`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 0L, playing = false, duration = 1_000L)
        core.setCallbacks(cb)

        core.reconcileToServerPosition(
            serverTicks = msToTicks(6_000L),
            whenMs = fixedNowMs,
            lane = SyncPlayPlaybackCore.ReconcileLane.QUEUE_UPDATE,
            groupIsPlaying = true,
        )
        advanceUntilIdle()

        assertEquals(listOf(1_000L), cb.seeks, "the estimated position is clamped to the duration")
    }

    @Test
    fun `queue-update reconcile pauses a playing engine when the group is paused`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 0L, playing = true)
        core.setCallbacks(cb)

        core.reconcileToServerPosition(
            serverTicks = msToTicks(200L),
            whenMs = fixedNowMs,
            lane = SyncPlayPlaybackCore.ReconcileLane.QUEUE_UPDATE,
            groupIsPlaying = false,
        )
        advanceUntilIdle()

        assertEquals(emptyList(), cb.seeks)
        assertEquals(1, cb.pauseCount)
        assertEquals(0, cb.playCount)
    }

    // ── scheduleUnpause's no-op-echo gate (SCHEDULED_UNPAUSE lane) ────────────

    private fun unpauseCommand(positionMs: Long) = SyncPlayPlaybackCommand(
        command = "Unpause",
        whenMs = fixedNowMs,
        positionTicks = msToTicks(positionMs),
        playlistItemId = "pl-item",
        emittedAtMs = fixedNowMs,
    )

    @Test
    fun `unpause within the 500ms echo tolerance moves nothing`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 5_000L, playing = true)
        core.setCallbacks(cb)

        core.applyCommand(unpauseCommand(positionMs = 5_000L))
        runCurrent()

        assertEquals(emptyList(), cb.seeks, "a no-op echo must not seek")
        assertEquals(0, cb.playCount)
        assertEquals(listOf(true to false), cb.syncStates, "flips straight to synced — no chip pulse")

        core.reset()
    }

    @Test
    fun `unpause beyond the 500ms echo tolerance lands the engine on the server position`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 5_000L, playing = true)
        core.setCallbacks(cb)

        core.applyCommand(unpauseCommand(positionMs = 5_600L))
        runCurrent()

        assertEquals(listOf(5_600L), cb.seeks)
        assertEquals(1, cb.playCount)

        core.reset()
    }

    // ── beginPendingItemLoad → READY-clear lifecycle ──────────────────────────

    @Test
    fun `commands drop while an item load is pending and apply after READY clears it`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = 0L, playing = false)
        core.setCallbacks(cb)

        val pause = SyncPlayPlaybackCommand(
            command = "Pause",
            whenMs = fixedNowMs,
            positionTicks = 0L,
            playlistItemId = "",
            emittedAtMs = fixedNowMs,
        )

        core.beginPendingItemLoad()
        core.applyCommand(pause)
        runCurrent()
        assertEquals(emptyList(), cb.seeks, "commands are dropped while the item load is pending")
        assertEquals(0, cb.pauseCount)

        // STATE_READY (3): the handshake completes — the core pauses and
        // reports ready, and the pending flag clears.
        core.onPlaybackStateChanged(3)
        runCurrent()
        assertEquals(1, cb.pauseCount, "the READY handshake pauses the engine")
        coVerify { controller.reportReady(0L, false, null, fixedNowMs) }
        assertEquals(listOf(false to true), cb.syncStates)

        // The same command now applies instead of dropping: schedulePause
        // seeks to the (zero) server position and pauses.
        core.applyCommand(pause)
        runCurrent()
        assertEquals(listOf(0L), cb.seeks, "the command applies once the READY-clear released the gate")
        assertEquals(2, cb.pauseCount, "one handshake pause + one command pause")

        core.reset()
    }
}
