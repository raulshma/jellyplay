package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives [SyncPlayPlaybackCore]'s periodic sync-correction loop against a fake
 * engine whose position reporting is either ms-precise (ExoPlayer/VLC, and mpv
 * since `time-pos` is observed as MPV_FORMAT_DOUBLE) or quantized to whole
 * seconds (mpv while `time-pos` was observed as MPV_FORMAT_INT64): an
 * in-sync engine that can only report floor(position/1s) presents a 0..1000ms
 * sawtooth "drift" against the continuously-advancing server-expected
 * position, and every correction tick that lands ≥ the SkipToSync threshold
 * fires a seek plus a syncing→synced chip pulse — the endless
 * "Syncing…Synced…" cycle SyncPlay showed when playback used mpv.
 *
 * The precise-case test is the regression guard: ms-precision position
 * reporting is load-bearing for any engine wired into SyncPlay. The mpv
 * observer itself has no JVM test seam (native lib), so the format choice is
 * guarded here at the core seam instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayPlaybackCoreCorrectionPulseTest {

    private val scheduler = StandardTestDispatcher()
    private val timeSyncManager: TimeSyncManager = mockk()
    private val controller: SyncPlayController = mockk(relaxed = true)
    private val castStore: SyncPlayCastStore = mockk()

    /** True media position at wall-clock `now`, ms — the engine is perfectly in sync. */
    private val t0Ms = System.currentTimeMillis()
    private val basePosMs = 1_200_700L // fractional-second offset ⇒ quantized report lags 700ms

    private fun truePosMs(nowMs: Long): Long = basePosMs + (nowMs - t0Ms)

    private class RecordingCallbacks(val position: () -> Long) : PlaybackCoreCallbacks {
        val seekCalls = mutableListOf<Long>()
        var syncingPulses = 0
        var playing = true

        override fun localPlay() {}
        override fun localPause() {}
        override fun localSeek(positionMs: Long) { seekCalls += positionMs }
        override fun setPlaybackRate(rate: Float) {}
        override fun currentPositionMs(): Long = position()
        override fun durationMs(): Long = 10_000_000L
        override fun isPlaying(): Boolean = playing
        override fun isBuffering(): Boolean = false
        override fun onSyncStateChanged(synced: Boolean, syncing: Boolean) {
            if (syncing) syncingPulses++
        }
    }

    private lateinit var core: SyncPlayPlaybackCore

    @Before
    fun setup() {
        Dispatchers.setMain(scheduler)
        every { timeSyncManager.remoteNow() } answers { System.currentTimeMillis() }
        every { timeSyncManager.toLocal(any()) } answers { firstArg<Long>() }
        every { castStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
        // Warm the mockk stubs: their first invocation performs kotlin-reflect
        // initialization that can stall for hundreds of ms. Unwarmed, that
        // stall lands inside scheduleUnpause's drift check (estimated position
        // reads remoteNow, engine position is read after) and flakes the
        // ±500ms no-op-echo decision.
        timeSyncManager.remoteNow()
        timeSyncManager.toLocal(0L)
        core = SyncPlayPlaybackCore(timeSyncManager, controller, castStore)
    }

    @After
    fun tearDown() {
        core.reset()
        Dispatchers.resetMain()
    }

    private fun unpauseCommand() = SyncPlayPlaybackCommand(
        command = "Unpause",
        whenMs = t0Ms,
        positionTicks = basePosMs * 10_000,
        playlistItemId = "pl-item",
        emittedAtMs = t0Ms,
    )

    /** ms-precise engine (ExoPlayer/VLC): no drift ⇒ no correction seeks, no chip pulses. */
    @Test
    fun `precise position never pulses the sync chip`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = { truePosMs(System.currentTimeMillis()) })
        core.setCallbacks(cb)
        core.applyCommand(unpauseCommand())
        advanceTimeBy(12_000)
        // The correction loop is an intentional infinite while(syncEnabled)
        // loop on the singleton's scope — runTest's post-body idle-wait would
        // otherwise spin it forever. Stop it inside the body, not @After.
        core.reset()

        assertEquals(0, cb.syncingPulses)
        assertEquals(0, cb.seekCalls.size)
    }

    /**
     * Whole-second position reporting (the mpv INT64 time-pos failure mode):
     * the same perfectly-in-sync engine, but its reported position floors to
     * seconds — the correction loop misreads the quantization gap as drift
     * and SkipToSync-seeks on most ticks, pulsing the chip each time.
     */
    @Test
    fun `quantized position drives endless skip-to-sync pulses`() = runTest(scheduler) {
        val cb = RecordingCallbacks(position = {
            (truePosMs(System.currentTimeMillis()) / 1000L) * 1000L
        })
        core.setCallbacks(cb)
        core.applyCommand(unpauseCommand())
        advanceTimeBy(12_000)
        core.reset()

        assertTrue(
            "Expected repeated syncing pulses from quantized position, got ${cb.syncingPulses}",
            cb.syncingPulses >= 3,
        )
        assertTrue(
            "Expected repeated SkipToSync seeks, got ${cb.seekCalls.size}",
            cb.seekCalls.size >= 3,
        )
    }
}
