package com.raulshma.jellyplay.core.data.playback

import android.graphics.Rect
import android.util.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [PipController]'s state-holder contract:
 *
 * - Every mirror flow starts at its idle value and reflects the last setter
 *   call (pip mode, playing, auto-enter, aspect ratio, source rect, controls
 *   locked).
 * - Auto-exit and pip-dismissed are one-shot flags: set → consumed/cleared.
 * - `reset()` clears everything, including the registered [PipTransport],
 *   hasNext flag and the locked flag — playback-end must not leak state into
 *   the next session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PipControllerTest {

    @Test
    fun `initial state is idle`() {
        val pip = PipController()

        assertFalse(pip.isInPipMode.value)
        assertFalse(pip.shouldAutoEnterPip.value)
        assertFalse(pip.pipDismissed.value)
        assertFalse(pip.isPlaying.value)
        assertFalse(pip.autoExitPip.value)
        assertFalse(pip.pipHasNext)
        assertFalse(pip.isControlsLocked)
        assertNull(pip.pipAspectRatio.value)
        assertNull(pip.pipSourceRect)
        assertNull(pip.pipTransport)
    }

    @Test
    fun `mirrored state reflects the latest setter call`() {
        val pip = PipController()

        pip.setPipMode(true)
        pip.setPlaying(true)
        pip.requestAutoEnterPip(true)
        pip.pipHasNext = true
        val ratio = Rational(16, 9)
        pip.setPipAspectRatio(ratio)

        assertTrue(pip.isInPipMode.value)
        assertTrue(pip.isPlaying.value)
        assertTrue(pip.shouldAutoEnterPip.value)
        assertTrue(pip.pipHasNext)
        assertSame(ratio, pip.pipAspectRatio.value)
    }

    @Test
    fun `a null aspect ratio clears the flow`() {
        val pip = PipController()
        pip.setPipAspectRatio(Rational(2, 3))

        pip.setPipAspectRatio(null)

        assertNull(pip.pipAspectRatio.value)
    }

    @Test
    fun `source rect hint stores and clears`() {
        val pip = PipController()
        val rect = Rect(0, 0, 100, 50)

        pip.updatePipSourceRect(rect)
        assertSame(rect, pip.pipSourceRect)

        pip.updatePipSourceRect(null)
        assertNull(pip.pipSourceRect)
    }

    @Test
    fun `auto-exit is a one-shot request consumed by the host`() {
        val pip = PipController()

        pip.requestAutoExitPip()
        assertTrue(pip.autoExitPip.value)

        pip.consumeAutoExitPip()
        assertFalse(pip.autoExitPip.value)
    }

    @Test
    fun `pip dismissed is a sticky flag cleared by the UI`() {
        val pip = PipController()

        pip.notifyPipDismissed()
        assertTrue(pip.pipDismissed.value)
        // Survives a mode flip (STOPPED→STARTED transitions).
        pip.setPipMode(true)
        assertTrue(pip.pipDismissed.value)

        pip.clearPipDismissed()
        assertFalse(pip.pipDismissed.value)
    }

    @Test
    fun `controls lock mirrors the player UI state`() {
        val pip = PipController()

        pip.setControlsLocked(true)
        assertTrue(pip.isControlsLocked)

        pip.setControlsLocked(false)
        assertFalse(pip.isControlsLocked)
    }

    @Test
    fun `reset clears every piece of state including the transport`() {
        val pip = PipController()
        val dispatched = mutableListOf<PipAction>()
        pip.pipTransport = PipTransport { dispatched.add(it) }
        pip.setPipMode(true)
        pip.setPlaying(true)
        pip.requestAutoEnterPip(true)
        pip.requestAutoExitPip()
        pip.notifyPipDismissed()
        pip.setPipAspectRatio(Rational(16, 9))
        pip.updatePipSourceRect(Rect(1, 2, 3, 4))
        pip.pipHasNext = true
        pip.setControlsLocked(true)

        pip.reset()

        assertNull(pip.pipTransport)
        assertFalse(pip.isInPipMode.value)
        assertFalse(pip.isPlaying.value)
        assertFalse(pip.shouldAutoEnterPip.value)
        assertFalse(pip.autoExitPip.value)
        assertFalse(pip.pipDismissed.value)
        assertFalse(pip.pipHasNext)
        assertFalse(pip.isControlsLocked)
        assertNull(pip.pipAspectRatio.value)
        assertNull(pip.pipSourceRect)
    }

    @Test
    fun `transport dispatches pip actions to the registered handler`() {
        val pip = PipController()
        val dispatched = mutableListOf<PipAction>()
        pip.pipTransport = PipTransport { dispatched.add(it) }

        pip.pipTransport?.handle(PipAction.NEXT)
        pip.pipTransport?.handle(PipAction.PLAY)

        assertEquals(listOf(PipAction.NEXT, PipAction.PLAY), dispatched)
    }
}
