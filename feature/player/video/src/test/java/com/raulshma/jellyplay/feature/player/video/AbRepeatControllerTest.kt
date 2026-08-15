package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
}
