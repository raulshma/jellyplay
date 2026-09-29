package com.raulshma.jellyplay.feature.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the photo viewer's pinch/pan invariants and its pure gesture decisions —
 * previously ~120 untestable lines inside PhotoViewerScreen's pointerInput
 * (the PageZoomStateTest extraction precedent over in player-book).
 */
class PhotoTransformStateTest {

    private fun state(scale: Float = 1f, offsetX: Float = 0f, offsetY: Float = 0f) =
        PhotoTransformState().apply {
            if (scale != 1f) zoomTo(scale)
            if (offsetX != 0f || offsetY != 0f) addPan(offsetX, offsetY)
        }

    // ── zoom band ──────────────────────────────────────────────────────────

    @Test
    fun `pinch clamps the scale to the 0_5x to 5x band`() {
        assertEquals(5f, state(scale = 4.8f).applyPinch(2f), "above the ceiling clamps at 5x")
        assertEquals(PHOTO_MAX_ZOOM, state(scale = 5f).applyPinch(1.5f))
        assertEquals(PHOTO_MIN_ZOOM, state(scale = 0.6f).applyPinch(0.5f), "below the floor clamps at 0.5x")
        assertEquals(PHOTO_MIN_ZOOM, state(scale = PHOTO_MIN_ZOOM).applyPinch(0.8f))
    }

    @Test
    fun `pinch multiplies the current scale inside the band`() {
        assertEquals(2f, state().applyPinch(2f))
        assertEquals(3f, state(scale = 2f).applyPinch(1.5f))
    }

    @Test
    fun `zoomTo applies the double-tap target directly`() {
        val s = state()
        s.zoomTo(PHOTO_DOUBLE_TAP_ZOOM)
        assertEquals(2.5f, s.scale)
    }

    // ── pan admission ──────────────────────────────────────────────────────

    @Test
    fun `pan is admitted only while zoomed in past 1x`() {
        assertFalse(PhotoGesturePolicy.acceptsPan(1f), "the fitted scale has nothing to pan")
        assertFalse(PhotoGesturePolicy.acceptsPan(0.9f))
        assertTrue(PhotoGesturePolicy.acceptsPan(1.01f))
        assertTrue(PhotoGesturePolicy.acceptsPan(2.5f))
    }

    @Test
    fun `addPan accumulates and resetPan zeroes`() {
        val s = state()
        s.addPan(10f, -4f)
        s.addPan(2f, 1f)
        assertEquals(12f, s.offsetX)
        assertEquals(-3f, s.offsetY)
        s.resetPan()
        assertEquals(0f, s.offsetX)
        assertEquals(0f, s.offsetY)
    }

    // ── reset semantics ────────────────────────────────────────────────────

    @Test
    fun `reset returns the identity transform (photo switch and double-tap zoom-out share it)`() {
        val s = state(scale = 3f, offsetX = 10f, offsetY = -4f)
        s.reset()
        assertEquals(1f, s.scale)
        assertEquals(0f, s.offsetX)
        assertEquals(0f, s.offsetY)
    }

    // ── swipe decision ─────────────────────────────────────────────────────

    @Test
    fun `swipe at and under the 150px threshold is not a swipe`() {
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(150f, 0f), "exactly the threshold stays put")
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(-150f, 0f))
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(149.9f, 0f))
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(0f, 0f))
    }

    @Test
    fun `swipe over the threshold pages right-to-previous and left-to-next`() {
        assertEquals(SwipeDecision.PREVIOUS, PhotoGesturePolicy.swipeDecision(150.5f, 0f), "swipe right -> previous")
        assertEquals(SwipeDecision.NEXT, PhotoGesturePolicy.swipeDecision(-150.5f, 0f), "swipe left -> next")
        assertEquals(SwipeDecision.PREVIOUS, PhotoGesturePolicy.swipeDecision(400f, 0f))
    }

    @Test
    fun `a vertically dominant drag is not a swipe`() {
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(200f, 300f))
        assertEquals(SwipeDecision.NONE, PhotoGesturePolicy.swipeDecision(-160f, 200f))
        assertEquals(SwipeDecision.PREVIOUS, PhotoGesturePolicy.swipeDecision(300f, 200f), "barely-horizontal still pages")
    }

    @Test
    fun `swipe is admitted only at the fitted scale — zoomed in the drag pans instead`() {
        assertTrue(PhotoGesturePolicy.acceptsSwipe(1f))
        assertTrue(PhotoGesturePolicy.acceptsSwipe(0.5f))
        assertFalse(PhotoGesturePolicy.acceptsSwipe(1.01f))
        assertFalse(PhotoGesturePolicy.acceptsSwipe(3f))
    }

    // ── tap / double-tap discrimination ────────────────────────────────────

    @Test
    fun `a tap within the 300ms window is the second tap of a double-tap`() {
        assertTrue(PhotoGesturePolicy.isDoubleTap(nowEpochMs = 299L, lastTapEpochMs = 0L))
        assertTrue(PhotoGesturePolicy.isDoubleTap(nowEpochMs = 5_000L, lastTapEpochMs = 4_800L))
        assertTrue(PhotoGesturePolicy.isDoubleTap(nowEpochMs = 1_000L, lastTapEpochMs = 1_000L), "same-stamp reads as immediate")
    }

    @Test
    fun `a tap at or past the window edge is a fresh single tap`() {
        assertFalse(PhotoGesturePolicy.isDoubleTap(nowEpochMs = 300L, lastTapEpochMs = 0L), "strictly under the window")
        assertFalse(PhotoGesturePolicy.isDoubleTap(nowEpochMs = 301L, lastTapEpochMs = 0L))
    }
}
