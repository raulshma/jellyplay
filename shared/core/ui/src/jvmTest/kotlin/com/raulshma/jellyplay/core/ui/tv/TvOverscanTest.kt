package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.TvOverscan
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the TV overscan safe-area fold: each edge insets by the
 * calibration's percent of ITS screen dimension — width for the horizontal
 * pair, height for the vertical pair — and `OFF` collapses to zero regardless
 * of the supplied panel size. This is the exact fold [TvScaffold] applies to
 * the TV shell content.
 */
class TvOverscanTest {

    @Test
    fun `off collapses to zero padding at any panel size`() {
        assertEquals(
            androidx.compose.foundation.layout.PaddingValues(0.dp),
            TvOverscan.OFF.overscanSafeAreaPadding(1920.dp, 1080.dp),
        )
        assertEquals(
            androidx.compose.foundation.layout.PaddingValues(0.dp),
            TvOverscan.OFF.overscanSafeAreaPadding(960.dp, 540.dp),
        )
    }

    @Test
    fun `five percent pads each edge by its own dimension`() {
        // A 960x540dp panel: 5% of width = 48dp on the horizontal pair,
        // 5% of height = 27dp on the vertical pair.
        val padding = TvOverscan.FIVE.overscanSafeAreaPadding(960.dp, 540.dp)
        assertEquals(48.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(48.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(27.dp, padding.calculateTopPadding())
        assertEquals(27.dp, padding.calculateBottomPadding())
    }

    @Test
    fun `ten percent doubles the safe area`() {
        val padding = TvOverscan.TEN.overscanSafeAreaPadding(960.dp, 540.dp)
        assertEquals(96.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(96.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(54.dp, padding.calculateTopPadding())
        assertEquals(54.dp, padding.calculateBottomPadding())
    }

    @Test
    fun `asymmetric panels keep each edge proportional to its own dimension`() {
        // 1280x600dp: 5% → 64dp horizontal, 30dp vertical (the edges are NOT
        // one shared margin — each track matches its screen dimension).
        val padding = TvOverscan.FIVE.overscanSafeAreaPadding(1280.dp, 600.dp)
        assertEquals(64.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(30.dp, padding.calculateTopPadding())
    }
}
