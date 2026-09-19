package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the fit-mode render-width math ([computeRenderWidth]): FIT_WIDTH fills
 * the surface, FIT_PAGE scales by the tighter axis, ORIGINAL uses the native
 * page pixels raster-capped at 3× the surface width, and unknown page sizes
 * degrade to FIT_WIDTH (comic archives).
 */
class ReaderFitMathTest {

    // US-Letter-ish page: 612×792 pt against a 1080×1920 surface.
    private val surfaceW = 1080
    private val surfaceH = 1920
    private val pageW = 612f
    private val pageH = 792f

    @Test
    fun `fit width renders at the surface width`() {
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_WIDTH, surfaceW, surfaceH, pageW, pageH))
    }

    @Test
    fun `fit page uses the tighter axis scale`() {
        // Width scale = 1080/612 ≈ 1.765 → 1080; height scale = 1920/792 ≈ 2.424.
        // min → width-bound, so FIT_PAGE == FIT_WIDTH for a page taller than
        // the surface's aspect...
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_PAGE, surfaceW, surfaceH, pageW, pageH))
        // ...and a WIDE page (spread) becomes height-bound: 1080×360 page →
        // scale = min(1080/1080, 1920/360) = 1 → native 1080.
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_PAGE, 1080, 1920, 1080f, 360f))
        // 1600×900 page → width scale 0.675, height scale 2.133 → 0.675 → 1080.
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_PAGE, 1080, 1920, 1600f, 900f))
        // A page constrained by height: 500×2000 → width scale 2.16, height
        // scale 0.96 → 0.96 × 500 = 480.
        assertEquals(480, computeRenderWidth(ReaderFitMode.FIT_PAGE, 1080, 1920, 500f, 2000f))
    }

    @Test
    fun `original renders native pixels under the raster cap`() {
        // Native 612 pt is below 3× the surface — rendered verbatim.
        assertEquals(612, computeRenderWidth(ReaderFitMode.ORIGINAL, surfaceW, surfaceH, pageW, pageH))
        // A huge native page is capped at 3× the surface width (raster safety).
        assertEquals(3240, computeRenderWidth(ReaderFitMode.ORIGINAL, surfaceW, surfaceH, 5000f, 4000f))
    }

    @Test
    fun `unknown page size degrades to fit width`() {
        // Comic archives report no pageSize before decoding.
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_PAGE, surfaceW, surfaceH, 0f, 0f))
        assertEquals(1080, computeRenderWidth(ReaderFitMode.ORIGINAL, surfaceW, surfaceH, 0f, 0f))
        assertEquals(1080, computeRenderWidth(ReaderFitMode.FIT_PAGE, surfaceW, surfaceH, -1f, 100f))
    }

    @Test
    fun `degenerate surfaces coerce to at least one pixel`() {
        assertEquals(1, computeRenderWidth(ReaderFitMode.FIT_WIDTH, 0, 0, pageW, pageH))
        assertEquals(1, computeRenderWidth(ReaderFitMode.FIT_PAGE, 0, 0, pageW, pageH))
    }
}
