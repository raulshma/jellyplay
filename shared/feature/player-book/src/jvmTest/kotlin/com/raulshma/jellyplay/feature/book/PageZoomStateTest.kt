package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the paged tile's zoom/pan invariants and its two pure decisions —
 * previously ~170 untestable lines inside the composable file (only the fit
 * math had a suite).
 */
class PageZoomStateTest {

    private fun state(
        zoom: Float = 1f,
        raster: Float = 1f,
        pan: Offset = Offset.Zero,
        tile: IntSize = IntSize(1000, 2000),
    ) = PageZoomState().apply {
        this.zoom = zoom
        this.raster = raster
        this.pan = pan
        this.tileSize = tile
    }

    @Test
    fun `visual scale multiplies raster when re-rastered`() {
        assertEquals(2f, state(zoom = 2f, raster = 1f).visual)
        assertEquals(4f, state(zoom = 2f, raster = 2f).visual, "at raster>1 the bitmap pixels already carry raster×")
    }

    @Test
    fun `setVisual divides out the raster and clamps the zoom band`() {
        val s = state(raster = 2f)
        s.setVisual(4f)
        assertEquals(2f, s.zoom, 1e-4f)
        s.setVisual(100f)
        assertTrue(s.zoom <= 20f, "zoom clamps at the 20× band ceiling")
    }

    @Test
    fun `pan clamps to half the visual overflow per axis`() {
        val s = state(zoom = 3f, tile = IntSize(1000, 2000), pan = Offset(9_999f, -9_999f))
        s.clampPan()
        assertEquals(Offset(1000f, -2000f), s.pan)
    }

    @Test
    fun `pan collapses to zero at 1x`() {
        val s = state(zoom = 1f, pan = Offset(50f, 50f))
        s.clampPan()
        assertEquals(Offset.Zero, s.pan)
    }

    @Test
    fun `reset returns the tile to the fitted base`() {
        val s = state(zoom = 3f, raster = 2f, pan = Offset(10f, 10f))
        s.reset()
        assertEquals(1f, s.zoom)
        assertEquals(1f, s.raster)
        assertEquals(Offset.Zero, s.pan)
    }

    @Test
    fun `raster retarget skips the base scale and the dead band`() {
        assertNull(pageRasterTarget(1f, 1f), "no zoom at all — nothing to re-raster")
        assertNull(pageRasterTarget(1.005f, 1f), "below the 1.01 threshold")
        assertNull(pageRasterTarget(2.02f, 2f), "within 0.05 of the current raster — not worth a re-render")
    }

    @Test
    fun `raster retarget caps at the 3x raster ceiling`() {
        assertEquals(3f, pageRasterTarget(visual = 6f, currentRaster = 1f))
        assertEquals(2.5f, pageRasterTarget(visual = 2.5f, currentRaster = 1f))
    }

    @Test
    fun `gesture claim needs two fingers or an existing zoom`() {
        assertFalse(shouldClaimZoomGesture(pointerCount = 1, visual = 1f), "single finger at 1× is the pager's swipe")
        assertTrue(shouldClaimZoomGesture(pointerCount = 2, visual = 1f))
        assertTrue(shouldClaimZoomGesture(pointerCount = 1, visual = 1.5f))
    }
}
