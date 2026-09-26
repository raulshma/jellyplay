package com.raulshma.jellyplay.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM truth table for [OverlayDragPolicy] — the drag/tap state machine that
 * lived inline in FloatingPlayerService's `attachDragHandler` closure. Every
 * rule is transcribed from that original: the raw-10px strict-`>` slop (exactly
 * 10 is STILL a tap), the toward-zero truncation of the move targets relative
 * to the down point, the latch of the drag flag for the whole gesture, and the
 * one-relayout-per-frame coalescing (first move arms, the postOnAnimation
 * flush discharges, ACTION_UP drains a still-pending arm).
 */
class OverlayDragPolicyTest {

    private fun policy(
        windowX: Int = 16,
        windowY: Int = 100,
        touchRawX: Float = 0f,
        touchRawY: Float = 0f,
    ) = OverlayDragPolicy().apply {
        onDown(windowX, windowY, touchRawX, touchRawY)
    }

    // ── Drag-vs-tap decision ───────────────────────────────────────────────

    @Test
    fun `exactly 10px on either axis is still a tap — the slop comparison is strict`() {
        val p = policy()
        p.onMove(touchRawX = 10f, touchRawY = 0f)
        assertTrue(p.onUp().isTap)
    }

    @Test
    fun `exactly 10px on both axes is still a tap`() {
        val p = policy()
        p.onMove(touchRawX = 10f, touchRawY = -10f)
        assertTrue(p.onUp().isTap)
    }

    @Test
    fun `just past the slop on either axis alone starts a drag`() {
        val x = policy().apply { onMove(touchRawX = 10.5f, touchRawY = 0f) }
        assertFalse(x.onUp().isTap)

        val y = policy().apply { onMove(touchRawX = 0f, touchRawY = -10.5f) }
        assertFalse(y.onUp().isTap)
    }

    @Test
    fun `the drag flag latches — later sub-slop moves keep it a drag`() {
        val p = policy()
        p.onMove(touchRawX = 50f, touchRawY = 0f)
        p.onMove(touchRawX = 50.5f, touchRawY = 0f) // 0.5px back toward the anchor
        val up = p.onUp()
        assertFalse(up.isTap)
    }

    @Test
    fun `a tap without any moves needs no flush and no click-suppression`() {
        val up = policy().onUp()
        assertTrue(up.isTap)
        assertFalse(up.flushFrame)
    }

    @Test
    fun `a new down starts a fresh gesture on the same instance`() {
        val p = policy()
        p.onMove(touchRawX = 40f, touchRawY = 0f) // drag…
        assertFalse(p.onUp().isTap)

        // …then a small second gesture on the SAME instance is a tap again.
        p.onDown(windowX = 0, windowY = 0, touchRawX = 0f, touchRawY = 0f)
        p.onMove(touchRawX = 3f, touchRawY = 4f)
        assertTrue(p.onUp().isTap)
    }

    // ── Move targets ────────────────────────────────────────────────────────

    @Test
    fun `move targets truncate toward zero and offset from the down position`() {
        val p = policy(windowX = 16, windowY = 100, touchRawX = 100f, touchRawY = 200f)

        val move = p.onMove(touchRawX = 103.7f, touchRawY = 195.2f)

        // +3.7 → +3 (truncation, not rounding); −4.8 → −4 (toward zero,
        // NOT floor's −5).
        assertEquals(16 + 3, move.x)
        assertEquals(100 - 4, move.y)
    }

    @Test
    fun `moves are relative to the down point, not the previous move`() {
        val p = policy(windowX = 0, windowY = 0, touchRawX = 0f, touchRawY = 0f)

        val first = p.onMove(touchRawX = 50f, touchRawY = 0f)
        val second = p.onMove(touchRawX = 60f, touchRawY = 0f)

        assertEquals(50, first.x)
        assertEquals(60, second.x) // not 110
    }

    @Test
    fun `sub-slop moves still re-aim the window`() {
        val move = policy(windowX = 0, windowY = 0).onMove(touchRawX = 5f, touchRawY = 0f)
        assertEquals(5, move.x)
    }

    // ── One-relayout-per-frame coalescing ──────────────────────────────────

    @Test
    fun `the first move arms the frame and later moves only re-aim`() {
        val p = policy(windowX = 0, windowY = 0)

        val first = p.onMove(touchRawX = 1f, touchRawY = 0f)
        val second = p.onMove(touchRawX = 2f, touchRawY = 0f)
        val third = p.onMove(touchRawX = 3f, touchRawY = 0f)

        assertTrue(first.armFrame)
        assertFalse(second.armFrame)
        assertFalse(third.armFrame)
        assertEquals(3, third.x)
    }

    @Test
    fun `the postOnAnimation flush discharges the arm — the next move re-arms`() {
        val p = policy()
        p.onMove(touchRawX = 1f, touchRawY = 0f)

        p.onFrameFlushed()

        assertTrue(p.onMove(touchRawX = 2f, touchRawY = 0f).armFrame)
    }

    @Test
    fun `action up drains a still-pending frame exactly once`() {
        val p = policy()
        p.onMove(touchRawX = 1f, touchRawY = 0f) // armed, never flushed by a frame

        val up = p.onUp()

        assertTrue(up.flushFrame)

        // A frame-less follow-up gesture has nothing to drain.
        p.onDown(windowX = 0, windowY = 0, touchRawX = 0f, touchRawY = 0f)
        assertFalse(p.onUp().flushFrame)
    }

    @Test
    fun `action up after the frame already flushed needs no drain`() {
        val p = policy()
        p.onMove(touchRawX = 1f, touchRawY = 0f)
        p.onFrameFlushed()

        val up = p.onUp()

        assertTrue(up.isTap) // the 1px gesture never crossed slop
        assertFalse(up.flushFrame)
    }

    @Test
    fun `a full drag gesture arms on move and drains at up`() {
        val p = policy(windowX = 16, windowY = 100)

        p.onDown(16, 100, touchRawX = 200f, touchRawY = 400f)
        val move = p.onMove(touchRawX = 218f, touchRawY = 395f)
        val up = p.onUp()

        assertTrue(move.armFrame)
        assertEquals(16 + 18, move.x)
        assertEquals(100 - 5, move.y)
        assertFalse(up.isTap)
        assertTrue(up.flushFrame)
    }
}
