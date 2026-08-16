package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AbRepeatStateTest {

    @Test
    fun `isActive false when disabled`() {
        assertFalse(AbRepeatState(enabled = false, aMs = 0, bMs = 100).isActive)
    }

    @Test
    fun `isActive false when points missing`() {
        assertFalse(AbRepeatState(enabled = true, aMs = null, bMs = 100).isActive)
        assertFalse(AbRepeatState(enabled = true, aMs = 0, bMs = null).isActive)
    }

    @Test
    fun `isActive false when a not less than b`() {
        assertFalse(AbRepeatState(enabled = true, aMs = 100, bMs = 100).isActive)
        assertFalse(AbRepeatState(enabled = true, aMs = 200, bMs = 100).isActive)
    }

    @Test
    fun `isActive true when enabled and a less than b`() {
        assertTrue(AbRepeatState(enabled = true, aMs = 50, bMs = 100).isActive)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AbRepeatControllerTest {

    private fun makeController(
        position: Long,
        onSeek: (Long) -> Unit = {},
    ): Pair<AbRepeatController, MutableStateFlow<Long>> {
        val positionFlow = MutableStateFlow(position)
        val engine = mockk<MediaEngine>(relaxed = true) {
            every { seekTo(any()) } answers { onSeek(firstArg()) }
        }
        val controller = AbRepeatController(
            scope = TestScope(UnconfinedTestDispatcher()),
            getEngine = { engine },
            positionFlow = positionFlow,
        )
        controller.start()
        return controller to positionFlow
    }

    @Test
    fun `point A clamped below B when set after B`() {
        val (controller, _) = makeController(0)
        controller.setPointB(1000)
        controller.setPointA(1500) // above B → clamped to 999
        assertEquals(999L, controller.state.value.aMs)
        assertEquals(1000L, controller.state.value.bMs)
    }

    @Test
    fun `point B clamped above A when set after A`() {
        val (controller, _) = makeController(0)
        controller.setPointA(1000)
        controller.setPointB(500) // below A → clamped to 1001
        assertEquals(1000L, controller.state.value.aMs)
        assertEquals(1001L, controller.state.value.bMs)
    }

    @Test
    fun `enabled and both points set reaches threshold active`() {
        val (controller, _) = makeController(0)
        controller.setEnabled(true)
        controller.setPointA(1000)
        controller.setPointB(5000)
        assertTrue(controller.state.value.isActive)
    }

    @Test
    fun `clear resets state`() {
        val (controller, _) = makeController(0)
        controller.setEnabled(true)
        controller.setPointA(1000)
        controller.setPointB(5000)
        controller.clear()
        assertFalse(controller.state.value.isActive)
        assertNull(controller.state.value.aMs)
        assertNull(controller.state.value.bMs)
    }

    /**
     * Item-switch semantics: the window does NOT persist
     * across episodes — resetForItem clears both points (and disables), so a
     * previous episode's points can neither seek the new episode nor be
     * resurrected by a single tap on the toggle. This is the named fix for the
     * former mirror/state divergence bug (see VideoPlayerResetEquivalenceTest).
     */
    @Test
    fun `resetForItem clears points and disarms`() {
        var seekCalls = 0
        val (controller, positionFlow) = makeController(0, onSeek = { seekCalls++ })
        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)

        controller.resetForItem()

        assertEquals(AbRepeatState(), controller.state.value)

        // Advancing past the previous B must NOT seek back to the old A —
        // the loop monitor no longer has a window.
        positionFlow.value = 6_000
        assertEquals(0, seekCalls)
    }

    /** After resetForItem the monitor stays inert even when re-enabled without new points. */
    @Test
    fun `resetForItem leaves controller inert when re-enabled`() {
        var seekCalls = 0
        val (controller, positionFlow) = makeController(0, onSeek = { seekCalls++ })
        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)
        controller.resetForItem()

        controller.setEnabled(true)
        positionFlow.value = 10_000
        assertEquals(0, seekCalls)
    }

    /** Crossing B with an active window seeks back to A exactly once. */
    @Test
    fun `crossing B seeks back to A`() {
        val seeks = mutableListOf<Long>()
        val (controller, positionFlow) = makeController(0, onSeek = { seeks += it })
        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)

        positionFlow.value = 6_000

        assertEquals(listOf(1_000L), seeks)
    }

    /**
     * Toggling off wipes the window: no stale points (or seekbar markers) may
     * resurrect when the toggle flips on again. Announced as Cleared since the
     * user just dissolved a visible loop.
     */
    @Test
    fun `disabling wipes points and emits cleared`() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val controller = AbRepeatController(
            scope = testScope,
            getEngine = { mockk<MediaEngine>(relaxed = true) },
            positionFlow = MutableStateFlow(0L),
        )
        val events = mutableListOf<AbRepeatEvent>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            controller.events.collect { events += it }
        }
        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)
        events.clear()

        controller.setEnabled(false)

        val state = controller.state.value
        assertFalse(state.enabled)
        assertNull(state.aMs)
        assertNull(state.bMs)
        assertEquals(listOf<AbRepeatEvent>(AbRepeatEvent.Cleared), events)
    }

    /**
     * Re-arm hysteresis: while the position is still at/after B no further
     * seek fires; only after dropping below B does the next crossing seek
     * again. Also the regression guard for the former nested-collector leak —
     * many crossings keep producing exactly one seek each.
     */
    @Test
    fun `re-arm requires dropping below B and repeats crossings seek each time`() {
        val seeks = mutableListOf<Long>()
        val (controller, positionFlow) = makeController(0, onSeek = { seeks += it })
        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)

        repeat(5) {
            positionFlow.value = 6_000 // crossing → seek to A
            positionFlow.value = 6_500 // still ≥ B while disarmed → no extra seek
            positionFlow.value = 2_000 // back inside the window → re-armed
        }

        assertEquals(List(5) { 1_000L }, seeks)
    }

    @Test
    fun `user actions emit badge events`() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val controller = AbRepeatController(
            scope = testScope,
            getEngine = { mockk<MediaEngine>(relaxed = true) },
            positionFlow = MutableStateFlow(0L),
        )
        val events = mutableListOf<AbRepeatEvent>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            controller.events.collect { events += it }
        }

        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)
        controller.clear()

        assertEquals(
            listOf(
                AbRepeatEvent.Enabled,
                AbRepeatEvent.PointASet(1_000),
                AbRepeatEvent.PointBSet(1_000, 5_000),
                AbRepeatEvent.Cleared,
            ),
            events,
        )
    }

    /** PointBSet only announces a completed loop — a disabled window stays silent. */
    @Test
    fun `pointBSet not emitted when window not active`() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val controller = AbRepeatController(
            scope = testScope,
            getEngine = { mockk<MediaEngine>(relaxed = true) },
            positionFlow = MutableStateFlow(0L),
        )
        val events = mutableListOf<AbRepeatEvent>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            controller.events.collect { events += it }
        }

        controller.setPointA(1_000)
        controller.setPointB(5_000) // enabled=false → not active → no PointBSet

        assertTrue(events.none { it is AbRepeatEvent.PointBSet })
    }

    /** Item switches must not surface a badge — resetForItem stays silent. */
    @Test
    fun `resetForItem emits no events`() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val controller = AbRepeatController(
            scope = testScope,
            getEngine = { mockk<MediaEngine>(relaxed = true) },
            positionFlow = MutableStateFlow(0L),
        )
        val events = mutableListOf<AbRepeatEvent>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            controller.events.collect { events += it }
        }

        controller.setEnabled(true)
        controller.setPointA(1_000)
        controller.setPointB(5_000)
        events.clear()

        controller.resetForItem()

        assertTrue(events.isEmpty())
    }
}
