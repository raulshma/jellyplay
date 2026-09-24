package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipTransportReArmTest {

    private lateinit var pip: FakePipController

    @BeforeTest
    fun setUp() {
        pip = FakePipController()
    }

    @Test
    fun nullControllerIsANoOp() {
        // Live's platform-conditional seam: platforms without PiP bind null.
        // Must neither throw nor invoke the handler.
        reArmPipTransport(null) { error("handler must not run without a controller") }
    }

    @Test
    fun assignsTransportOnController() {
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)
    }

    @Test
    fun dispatchedActionsReachTheHostHandler() {
        val received = mutableListOf<PipAction>()
        reArmPipTransport(pip) { received.add(it) }

        PipAction.entries.forEach { pip.pipTransport?.handle(it) }

        assertEquals(PipAction.entries.toList(), received)
    }

    @Test
    fun reArmOverwritesThePriorTransport() {
        val first = mutableListOf<PipAction>()
        reArmPipTransport(pip) { first.add(it) }
        val staleTransport = pip.pipTransport

        val second = mutableListOf<PipAction>()
        reArmPipTransport(pip) { second.add(it) }

        // The controller now routes through the second handler only.
        pip.pipTransport?.handle(PipAction.PLAY)
        assertEquals(listOf(PipAction.PLAY), second)
        assertTrue(first.isEmpty())
        // The displaced transport is detached, not still reachable.
        assertNotNull(staleTransport)
        assertTrue(staleTransport !== pip.pipTransport)
    }

    @Test
    fun resetDetachesTransportUntilReArmed() {
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)

        pip.reset()

        assertNull(pip.pipTransport)
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)
    }

    /** Minimal [PipController] stand-in: only the transport seam matters. */
    private class FakePipController : PipController {
        override val isInPipMode: StateFlow<Boolean> = MutableStateFlow(false)
        override val pipDismissed: StateFlow<Boolean> = MutableStateFlow(false)
        override var pipTransport: PipTransport? = null
        override var pipHasNext: Boolean = false
        override fun setPlaying(playing: Boolean) = Unit
        override fun setControlsLocked(locked: Boolean) = Unit
        override fun requestAutoEnterPip(shouldEnter: Boolean) = Unit
        override fun requestAutoExitPip() = Unit
        override fun consumeAutoExitPip() = Unit
        override fun clearPipDismissed() = Unit
        override fun setPipAspectRatio(aspect: Pair<Int, Int>?) = Unit
        override fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) = Unit
        override fun reset() {
            pipTransport = null
        }
    }
}
