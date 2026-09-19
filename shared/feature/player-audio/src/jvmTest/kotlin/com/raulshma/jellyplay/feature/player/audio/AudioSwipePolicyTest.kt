package com.raulshma.jellyplay.feature.player.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the swipe/double-tap decision table lifted verbatim from
 * `AudioPlayerScreen` (the `PlayerScreenPolicies` precedent): the 10px
 * direction lock, the open-queue (−80 offset / −150 total) and dismiss
 * (150 offset / 200 total) vertical ladders incl. their order, the ±180px
 * skip thresholds, and the double-tap thirds. All comparisons strict.
 */
class AudioSwipePolicyTest {

    // ── Direction lock (10f, strict on both the dominance and the threshold) ──

    @Test
    fun `direction locks vertical only past 10px and only when y dominates`() {
        assertEquals(AudioDragDirection.VERTICAL, audioDragDirection(0f, 11f))
        assertNull(audioDragDirection(0f, 10f), "the lock threshold is strict")
        assertNull(audioDragDirection(0f, 9.9f))
    }

    @Test
    fun `direction locks horizontal only past 10px and only when x dominates`() {
        assertEquals(AudioDragDirection.HORIZONTAL, audioDragDirection(11f, 0f))
        assertNull(audioDragDirection(10f, 0f))
        assertNull(audioDragDirection(9.9f, 0f))
    }

    @Test
    fun `equal magnitudes never lock`() {
        assertNull(audioDragDirection(5f, 5f))
        assertNull(audioDragDirection(11f, 11f))
    }

    @Test
    fun `direction lock honors either sign of the dominant axis`() {
        assertEquals(AudioDragDirection.VERTICAL, audioDragDirection(0f, -11f))
        assertEquals(AudioDragDirection.HORIZONTAL, audioDragDirection(-11f, 0f))
    }

    // ── Vertical end ladder: open queue / dismiss / settle ──

    @Test
    fun `upward swipe opens the queue past the offset or total threshold`() {
        assertEquals(AudioVerticalSwipeAction.OpenQueue, audioVerticalSwipeAction(-80.01f, 0f))
        assertEquals(AudioVerticalSwipeAction.OpenQueue, audioVerticalSwipeAction(0f, -150.01f))
    }

    @Test
    fun `queue thresholds are strict`() {
        assertEquals(AudioVerticalSwipeAction.Settle, audioVerticalSwipeAction(-80f, 0f))
        assertEquals(AudioVerticalSwipeAction.Settle, audioVerticalSwipeAction(0f, -150f))
        assertEquals(AudioVerticalSwipeAction.Settle, audioVerticalSwipeAction(-79.99f, -149.99f))
    }

    @Test
    fun `downward swipe dismisses past the offset or total threshold`() {
        assertEquals(AudioVerticalSwipeAction.Dismiss, audioVerticalSwipeAction(150.01f, 0f))
        assertEquals(AudioVerticalSwipeAction.Dismiss, audioVerticalSwipeAction(0f, 200.01f))
    }

    @Test
    fun `dismiss thresholds are strict`() {
        assertEquals(AudioVerticalSwipeAction.Settle, audioVerticalSwipeAction(150f, 0f))
        assertEquals(AudioVerticalSwipeAction.Settle, audioVerticalSwipeAction(0f, 200f))
    }

    @Test
    fun `the queue ladder is checked before the dismiss ladder`() {
        // A dominant upward fling wins even when the cumulative drag also
        // crossed the downward total — the original if/else order, pinned.
        assertEquals(AudioVerticalSwipeAction.OpenQueue, audioVerticalSwipeAction(-90f, 250f))
    }

    // ── Horizontal end ladder: skip next / skip previous / settle ──

    @Test
    fun `swipe left past 180px skips next`() {
        assertEquals(AudioHorizontalSwipeAction.SkipNext, audioHorizontalSwipeAction(-180.01f))
        assertEquals(AudioHorizontalSwipeAction.Settle, audioHorizontalSwipeAction(-180f))
    }

    @Test
    fun `swipe right past 180px skips previous`() {
        assertEquals(AudioHorizontalSwipeAction.SkipPrevious, audioHorizontalSwipeAction(180.01f))
        assertEquals(AudioHorizontalSwipeAction.Settle, audioHorizontalSwipeAction(180f))
    }

    @Test
    fun `sub-threshold horizontal drags settle`() {
        assertEquals(AudioHorizontalSwipeAction.Settle, audioHorizontalSwipeAction(0f))
        assertEquals(AudioHorizontalSwipeAction.Settle, audioHorizontalSwipeAction(-179.99f))
        assertEquals(AudioHorizontalSwipeAction.Settle, audioHorizontalSwipeAction(179.99f))
    }

    // ── Double-tap thirds (0.35 / 0.65, strict on both edges) ──

    @Test
    fun `leading third seeks back, trailing third seeks forward`() {
        assertEquals(DoubleTapSeekAction.SeekBack, doubleTapSeekAction(0f, 1000f))
        assertEquals(DoubleTapSeekAction.SeekBack, doubleTapSeekAction(300f, 1000f))
        assertEquals(DoubleTapSeekAction.SeekForward, doubleTapSeekAction(700f, 1000f))
        assertEquals(DoubleTapSeekAction.SeekForward, doubleTapSeekAction(1000f, 1000f))
    }

    @Test
    fun `middle third toggles play pause`() {
        assertEquals(DoubleTapSeekAction.TogglePlayPause, doubleTapSeekAction(500f, 1000f))
        assertEquals(DoubleTapSeekAction.TogglePlayPause, doubleTapSeekAction(400f, 1000f))
        assertEquals(DoubleTapSeekAction.TogglePlayPause, doubleTapSeekAction(600f, 1000f))
    }

    @Test
    fun `zone edges are strict and fall to the middle`() {
        val width = 1000f
        val leftEdge = width * 0.35f
        val rightEdge = width * 0.65f
        assertEquals(DoubleTapSeekAction.TogglePlayPause, doubleTapSeekAction(leftEdge, width))
        assertEquals(DoubleTapSeekAction.TogglePlayPause, doubleTapSeekAction(rightEdge, width))
        assertEquals(DoubleTapSeekAction.SeekBack, doubleTapSeekAction(leftEdge - 0.01f, width))
        assertEquals(DoubleTapSeekAction.SeekForward, doubleTapSeekAction(rightEdge + 0.01f, width))
    }
}
