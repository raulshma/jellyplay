package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * JVM tests for [GestureSeekController] using fake lambdas (no Compose, no
 * Android `Window`/`AudioManager`). Pins the commit-vs-cancel asymmetry and the
 * live volume/brightness gesture behavior.
 *
 * Uses the default [runTest] dispatcher (StandardTestDispatcher) so the
 * dismiss-delay coroutine launched by `onClearOverlays` doesn't run eagerly —
 * only the dismiss-specific tests advance virtual time.
 */
class GestureSeekControllerTest {

    private fun engine(currentPositionMs: Long = 0L, durationMs: Long = 120_000L): MediaEngine =
        mockk(relaxed = true) {
            every { this@mockk.currentPositionMs } returns currentPositionMs
            every { this@mockk.durationMs } returns durationMs
        }

    private data class FakeIo(
        var windowBrightness: Float = -1f,
        var streamCurrent: Int = 5,
        val streamMax: Int = 10,
        var restoredBrightness: Float? = null,
    )

    private fun controller(
        scope: TestScope,
        engine: MediaEngine,
        swipeSeekMaxMs: Long = 120_000L,
        castConnected: Boolean = false,
        castVolume: Float = 1f,
        io: FakeIo = FakeIo(),
        doSeekTo: (Long) -> Unit = {},
        saveBrightness: (Float) -> Unit = {},
        setCastVolume: (Float) -> Unit = {},
    ) = GestureSeekController(
        scope = scope,
        getEngine = { engine },
        getSwipeSeekMaxMs = { swipeSeekMaxMs },
        isCastConnected = { castConnected },
        getCastVolume = { castVolume },
        readWindowBrightness = { io.windowBrightness },
        writeWindowBrightness = { io.windowBrightness = it },
        restoreWindowBrightness = { restored ->
            io.restoredBrightness = restored
            io.windowBrightness = if (restored >= 0f) restored else -1f
        },
        readStreamVolume = { io.streamCurrent to io.streamMax },
        writeStreamVolume = { steps -> io.streamCurrent = steps },
        doSeekTo = doSeekTo,
        saveBrightness = saveBrightness,
        setCastVolume = setCastVolume,
        dismissDelayMs = 800L,
    )

    // ---- onClearOverlays commits ----

    @Test
    fun `onClearOverlays seeks to the clamped target`() = runTest {
        val engine = engine(currentPositionMs = 60_000L, durationMs = 120_000L)
        var seekTarget: Long? = null
        val c = controller(this, engine, doSeekTo = { seekTarget = it })

        c.onSeekGesture(30_000L) // 60s + 30s = 90s
        c.onClearOverlays()

        assertEquals(90_000L, seekTarget)
    }

    @Test
    fun `onClearOverlays persists brightness when in range`() = runTest {
        val engine = engine()
        val io = FakeIo(windowBrightness = 0.4f)
        var savedBrightness: Float? = null
        val c = controller(this, engine, io = io, saveBrightness = { savedBrightness = it })

        c.onBrightnessGesture(0.2f) // 0.4 + 0.2 = 0.6
        c.onClearOverlays()

        assertEquals(0.6f, savedBrightness!!, 0.0001f)
    }

    @Test
    fun `onClearOverlays does not persist brightness when never touched`() = runTest {
        val engine = engine()
        var savedBrightness: Float? = null
        val c = controller(this, engine, saveBrightness = { savedBrightness = it })

        c.onClearOverlays()

        assertEquals(null, savedBrightness)
    }

    @Test
    fun `onClearOverlays schedules delayed indicator hide`() = runTest {
        val engine = engine()
        val io = FakeIo(windowBrightness = 0.5f)
        val c = controller(this, engine, io = io, saveBrightness = {})

        c.onBrightnessGesture(0.2f)
        assertEquals(0.7f, c.brightnessOverlay.value, 0.0001f)
        c.onClearOverlays()
        // Immediately after commit, the indicator is still visible (delay not elapsed).
        assertEquals(0.7f, c.brightnessOverlay.value, 0.0001f)

        advanceTimeBy(801L)
        assertEquals(-1f, c.brightnessOverlay.value, 0.0001f)
    }

    // ---- onCancelOverlays discards ----

    @Test
    fun `onCancelOverlays does NOT seek`() = runTest {
        val engine = engine(currentPositionMs = 60_000L)
        var seekTarget: Long? = null
        val c = controller(this, engine, doSeekTo = { seekTarget = it })

        c.onSeekGesture(30_000L)
        c.onCancelOverlays()

        assertEquals(null, seekTarget)
    }

    @Test
    fun `onCancelOverlays restores brightness captured at gesture start`() = runTest {
        val engine = engine()
        val io = FakeIo(windowBrightness = 0.3f)
        val c = controller(this, engine, io = io)

        c.onStartGesture() // captures 0.3f
        io.windowBrightness = 0.8f // simulate a brightness gesture mid-flight
        c.onBrightnessGesture(0.1f)
        c.onCancelOverlays()

        assertEquals(0.3f, io.restoredBrightness!!, 0.0001f)
    }

    @Test
    fun `onCancelOverlays clears overlays immediately`() = runTest {
        val engine = engine()
        val io = FakeIo(windowBrightness = 0.5f)
        val c = controller(this, engine, io = io)

        c.onBrightnessGesture(0.2f)
        c.onVolumeGesture(0.3f)
        c.onCancelOverlays()

        assertEquals(-1f, c.brightnessOverlay.value, 0.0001f)
        assertEquals(-1f, c.volumeOverlay.value, 0.0001f)
        assertEquals(false, c.isSeeking.value)
    }

    @Test
    fun `onCancelOverlays cancels pending dismiss job`() = runTest {
        val engine = engine()
        val io = FakeIo(windowBrightness = 0.5f)
        val c = controller(this, engine, io = io, saveBrightness = {})

        c.onBrightnessGesture(0.2f)
        c.onClearOverlays() // schedules dismiss
        c.onCancelOverlays() // should cancel it
        advanceTimeBy(801L)
        // After cancel + delay, nothing re-hides (already -1f from cancel).
        assertEquals(-1f, c.brightnessOverlay.value, 0.0001f)
    }

    // ---- cast vs local volume branches ----

    @Test
    fun `onVolumeGesture cast branch calls setCastVolume immediately`() = runTest {
        val engine = engine()
        var castVol: Float? = null
        val c = controller(this, engine, castConnected = true, castVolume = 0.4f, setCastVolume = { castVol = it })

        c.onVolumeGesture(0.3f)

        assertEquals(0.7f, castVol!!, 0.0001f)
    }

    @Test
    fun `onVolumeGesture local branch quantizes to hardware steps`() = runTest {
        val engine = engine()
        val io = FakeIo(streamCurrent = 5, streamMax = 10)
        val c = controller(this, engine, io = io)

        c.onVolumeGesture(0.3f) // 3 steps → streamCurrent 8

        assertEquals(8, io.streamCurrent)
    }
}
