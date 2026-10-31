package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seam test for [collectTrickplayThumbnails] — the ONE collector behind BOTH
 * trickplay feeds. The seek-bar feed's position/gate arrive as Compose state;
 * the gesture feed's arrive as `StateFlow` reads. `snapshotFlow` observes only
 * snapshot state — a `StateFlow.value` read inside the block registers no
 * dependency, so the gesture feed ran its block exactly once and never again
 * (gesture trickplay never showed while the seek-bar feed worked). The fix
 * announces flow-side changes through `changePulses`; these tests pin BOTH
 * shapes at the real collector.
 */
class TrickplayGestureFeedCollectorTest {

    /** Manual frame clock: snapshotFlow awaits a frame before each re-check. */
    private class ManualFrameClock : MonotonicFrameClock {
        private val frames = Channel<Long>(Channel.UNLIMITED)
        fun advanceFrame() {
            frames.trySend(System.nanoTime())
        }

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
    }

    private val info = TrickplayInfo(
        width = 320, height = 240, tileWidth = 10, tileHeight = 10,
        thumbnailCount = 575, interval = 10_000, bandwidth = 10_310,
    )

    private suspend fun yieldAFew() {
        repeat(6) { yield() }
    }

    private suspend fun drainList(channel: Channel<Long>): List<Long> {
        val out = mutableListOf<Long>()
        while (true) {
            val next = channel.tryReceive().getOrNull() ?: break
            out += next
        }
        return out
    }

    @Test
    fun `seek feed - collector reacts to compose-state driven gate and position`() = runTest {
        val clock = ManualFrameClock()
        val gate = mutableStateOf(false)
        val position = mutableStateOf(0L)
        val fetched = Channel<Long>(Channel.UNLIMITED)
        val collector = launch(clock) {
            collectTrickplayThumbnails(
                positionMs = { position.value },
                gate = { gate.value },
                trickplayInfo = { info },
                onFetch = { pos, _ -> fetched.trySend(pos) },
            )
        }
        yieldAFew()
        assertEquals(0, drainList(fetched).size)

        gate.value = true
        position.value = 5_000L
        Snapshot.sendApplyNotifications()
        clock.advanceFrame()
        runCurrent()

        assertTrue(5_000L in drainList(fetched), "Compose-state feed: onFetch never fired after gate+position flip")
        collector.cancelAndJoin()
    }

    @Test
    fun `gesture feed - stateflow gate and position fire through changePulses`() = runTest {
        val clock = ManualFrameClock()
        val gateFlow = MutableStateFlow(false)
        val positionFlow = MutableStateFlow(0L)
        val fetched = Channel<Long>(Channel.UNLIMITED)
        val collector = launch(clock) {
            // The gesture feed's exact shape: StateFlow-backed getters +
            // the drag flows as changePulses (see rememberTrickplayFeeds).
            collectTrickplayThumbnails(
                positionMs = { positionFlow.value },
                gate = { gateFlow.value },
                trickplayInfo = { info },
                onFetch = { pos, _ -> fetched.trySend(pos) },
                changePulses = combine(gateFlow, positionFlow) { _, _ -> },
            )
        }
        yieldAFew()
        assertEquals(0, drainList(fetched).size)

        // Drag start + first drag frame: the gate and position StateFlows
        // flip — the pulses must re-run the gate check and fire onFetch.
        gateFlow.value = true
        positionFlow.value = 5_000L
        runCurrent()

        assertTrue(5_000L in drainList(fetched), "StateFlow feed: onFetch never fired after gate+position flip")

        // Subsequent drag frames keep feeding while the gate stays true.
        positionFlow.value = 15_000L
        runCurrent()
        assertTrue(15_000L in drainList(fetched), "StateFlow feed: drag-frame position change never re-fetched")

        // Drag end: gate false — no further fetches.
        gateFlow.value = false
        positionFlow.value = 20_000L
        runCurrent()
        assertEquals(0, drainList(fetched).size, "StateFlow feed: onFetch fired with gate false")
        collector.cancelAndJoin()
    }

    @Test
    fun `gesture feed - no fetch before the gate opens`() = runTest {
        val clock = ManualFrameClock()
        val gateFlow = MutableStateFlow(false)
        val positionFlow = MutableStateFlow(0L)
        val fetched = Channel<Long>(Channel.UNLIMITED)
        val collector = launch(clock) {
            collectTrickplayThumbnails(
                positionMs = { positionFlow.value },
                gate = { gateFlow.value },
                trickplayInfo = { info },
                onFetch = { pos, _ -> fetched.trySend(pos) },
                changePulses = combine(gateFlow, positionFlow) { _, _ -> },
            )
        }
        yieldAFew()

        // Position changes with the gate closed (a vertical volume swipe
        // reusing the drag surface, wheel scrolls…) — no fetch may fire.
        positionFlow.value = 7_000L
        runCurrent()
        assertEquals(0, drainList(fetched).size, "StateFlow feed: onFetch fired with gate false")
        collector.cancelAndJoin()
    }
}
