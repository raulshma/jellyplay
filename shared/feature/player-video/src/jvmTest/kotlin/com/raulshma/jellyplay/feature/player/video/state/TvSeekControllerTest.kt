package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [TvSeekController] — the TV/touch seek-bar state
 * machine extracted from `PlayerControls.TvControllableSeekBar` (the
 * [GestureSeekControllerTest] pattern: fake lambdas, no Compose). Pins the
 * five behaviors the former inline lambdas carried with zero coverage:
 *
 *  - the 30s-TV / 10s-touch seek-step divergence ([tvSeekStepFraction]);
 *  - the directional D-pad clamps (1.0 forward, 0.0 back);
 *  - flush-on-unfocus commits the pending seek (and a stray flush is a
 *    no-op);
 *  - the duration-less (live) guards: ticks unconsumed, progress 0, seeding
 *    floored at 0;
 *  - the drag-vs-TV-vs-live progress priority.
 */
class TvSeekControllerTest {

    /** Records every seek callback the controller fires — the test's probe. */
    private class RecordingCallbacks {
        var starts = 0
        val previews = mutableListOf<Long>()
        var ends = 0
    }

    private val callbacks = RecordingCallbacks()

    private fun controller(durationMs: Long): TvSeekController {
        val controller = TvSeekController(
            onSeekStart = { callbacks.starts++ },
            onSeekPreview = { callbacks.previews += it },
            onSeekEnd = { callbacks.ends++ },
        )
        controller.durationMs = durationMs
        return controller
    }

    // ---- tvSeekStepFraction: the step divergence ----

    @Test
    fun `seek step diverges - 30s per tick on TV, 10s on touch`() {
        assertEquals(0.25f, tvSeekStepFraction(isTv = true, durationMs = 120_000L))
        assertEquals(10_000f / 120_000f, tvSeekStepFraction(isTv = false, durationMs = 120_000L))
        // The TV tick is exactly three times the touch tick at any duration.
        assertEquals(
            tvSeekStepFraction(isTv = true, durationMs = 97_000L),
            tvSeekStepFraction(isTv = false, durationMs = 97_000L) * 3f,
        )
    }

    @Test
    fun `a TV tick advances 30s and a touch tick 10s at the same duration`() {
        val tv = controller(durationMs = 120_000L)
        tv.onFocusGained(livePositionMs = 0L)
        val touch = controller(durationMs = 120_000L)
        touch.onFocusGained(livePositionMs = 0L)

        tv.onDpadTick(direction = +1, step = tvSeekStepFraction(true, 120_000L))
        touch.onDpadTick(direction = +1, step = tvSeekStepFraction(false, 120_000L))

        assertEquals(30_000L, callbacks.previews[0])
        assertEquals(10_000L, callbacks.previews[1])
    }

    // ---- focus entry seeding ----

    @Test
    fun `focus entry seeds the thumb at the live position and starts nothing`() {
        val seek = controller(durationMs = 200_000L)

        seek.onFocusGained(livePositionMs = 50_000L)

        assertEquals(0.25f, seek.tvSeekPosition.value)
        assertEquals(0, callbacks.starts)
        // While focused, the TV fraction wins over the live position.
        assertEquals(0.25f, seek.progress(livePositionMs = 123_456L))
    }

    @Test
    fun `focus entry on a duration-less stream seeds zero`() {
        val seek = controller(durationMs = 0L)

        seek.onFocusGained(livePositionMs = 50_000L)

        assertEquals(0f, seek.tvSeekPosition.value)
    }

    // ---- D-pad accumulate, preview and clamps ----

    @Test
    fun `dpad tick starts the seek once and previews per tick`() {
        val seek = controller(durationMs = 120_000L)
        seek.onFocusGained(livePositionMs = 0L)

        assertTrue(seek.onDpadTick(direction = +1, step = 0.25f))
        assertTrue(seek.onDpadTick(direction = +1, step = 0.25f))

        assertEquals(1, callbacks.starts)
        assertEquals(listOf(30_000L, 60_000L), callbacks.previews)
        assertEquals(0.5f, seek.tvSeekPosition.value)
    }

    @Test
    fun `dpad clamps at the top and at zero`() {
        val seek = controller(durationMs = 100_000L)
        seek.onFocusGained(livePositionMs = 0L)

        // Three +0.4 ticks: 0.4 -> 0.8 -> clamped 1.0.
        repeat(3) { seek.onDpadTick(direction = +1, step = 0.4f) }
        assertEquals(1.0f, seek.tvSeekPosition.value)
        assertEquals(100_000L, callbacks.previews.last())

        // Three -0.4 ticks: 0.6 -> 0.2 -> floored 0.0.
        repeat(3) { seek.onDpadTick(direction = -1, step = 0.4f) }
        assertEquals(0.0f, seek.tvSeekPosition.value)
        assertEquals(0L, callbacks.previews.last())
    }

    // ---- flush / commit semantics ----

    @Test
    fun `select flush commits the pending seek and a second flush is a no-op`() {
        val seek = controller(durationMs = 120_000L)
        seek.onFocusGained(livePositionMs = 0L)
        seek.onDpadTick(direction = +1, step = 0.25f)

        seek.flush()
        assertEquals(1, callbacks.ends)
        assertFalse(seek.tvSeekStarted.value)

        seek.flush()
        assertEquals(1, callbacks.ends)

        // A committed seek is done: the next tick starts a NEW seek.
        seek.onDpadTick(direction = +1, step = 0.25f)
        assertEquals(2, callbacks.starts)
    }

    @Test
    fun `focus loss commits the pending seek (flush-on-unfocus)`() {
        val seek = controller(durationMs = 120_000L)
        seek.onFocusGained(livePositionMs = 0L)
        seek.onDpadTick(direction = +1, step = 0.25f)

        seek.onFocusLost()

        assertEquals(1, callbacks.ends)
        assertFalse(seek.tvSeekStarted.value)
        assertFalse(seek.isFocused.value)
    }

    @Test
    fun `focus loss without a pending seek ends nothing`() {
        val seek = controller(durationMs = 120_000L)
        seek.onFocusGained(livePositionMs = 60_000L)

        seek.onFocusLost()

        assertEquals(0, callbacks.ends)
        assertEquals(0, callbacks.starts)
    }

    // ---- duration-less (live) guards ----

    @Test
    fun `duration-less stream - ticks unconsumed and no seek started`() {
        val seek = controller(durationMs = 0L)
        seek.onFocusGained(livePositionMs = 0L)

        assertFalse(seek.onDpadTick(direction = +1, step = 0.25f))
        assertFalse(seek.onDpadTick(direction = -1, step = 0.25f))

        assertEquals(0, callbacks.starts)
        assertTrue(callbacks.previews.isEmpty())
        assertFalse(seek.tvSeekStarted.value)
    }

    @Test
    fun `duration-less stream - progress is zero even mid-drag or focused`() {
        val seek = controller(durationMs = 0L)

        seek.onFocusGained(livePositionMs = 40_000L)
        assertEquals(0f, seek.progress(livePositionMs = 40_000L))

        seek.onDragStart(fraction = 0.7f)
        assertEquals(0f, seek.progress(livePositionMs = 40_000L))
    }

    // ---- drag-vs-tv-vs-live priority ----

    @Test
    fun `drag wins over tv which wins over live`() {
        val seek = controller(durationMs = 200_000L)
        // Seed at 50s/200s = 0.25.
        seek.onFocusGained(livePositionMs = 50_000L)
        // TV (focused) beats live.
        assertEquals(0.25f, seek.tvSeekPosition.value)
        assertEquals(0.25f, seek.progress(livePositionMs = 40_000L))

        // Drag beats TV. DragStart opens the seek: start + first preview.
        seek.onDragStart(fraction = 0.4f)
        assertEquals(1, callbacks.starts)
        assertEquals(listOf(80_000L), callbacks.previews.takeLast(1))
        assertTrue(seek.isDragging.value)
        assertEquals(0.4f, seek.progress(livePositionMs = 40_000L))

        // DragTo previews each move against the current duration.
        seek.onDragTo(fraction = 0.9f)
        assertEquals(0.9f, seek.dragFraction.value)
        assertEquals(180_000L, callbacks.previews.last())

        // DragEnd commits and drops the dragging flag — back to the TV arm
        // (focus was never lost), still above live.
        seek.onDragEnd()
        assertEquals(1, callbacks.ends)
        assertFalse(seek.isDragging.value)
        assertEquals(0.25f, seek.progress(livePositionMs = 40_000L))
    }

    @Test
    fun `without focus or drag progress tracks the live position`() {
        val seek = controller(durationMs = 200_000L)

        assertEquals(0.5f, seek.progress(livePositionMs = 100_000L))
        // The transition into a seek arm requires an event: none fired.
        assertEquals(0, callbacks.starts)
        assertEquals(0, callbacks.ends)
        assertTrue(callbacks.previews.isEmpty())
    }
}
