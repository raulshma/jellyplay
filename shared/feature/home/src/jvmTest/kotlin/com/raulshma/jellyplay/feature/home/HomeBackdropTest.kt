package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Covers the pure decision functions backing [HomeBackdrop]:
 *  - [shouldRenderBackdrop] (enable + performance gate)
 *  - [resolveBackdropLayer] (blurhash vs ambient)
 *
 * No Compose/Robolectric needed — these are plain functions over a data class.
 */
class HomeBackdropTest {

    private fun state(
        enabled: Boolean = true,
        performanceMode: Boolean = false,
        isLightTheme: Boolean = false,
        blurHash: String? = null,
        backdropUrl: String? = null,
    ) = HomeBackdropState(
        enabled = enabled,
        performanceMode = performanceMode,
        oledMode = false,
        isLightTheme = isLightTheme,
        blurHash = blurHash,
        backdropUrl = backdropUrl,
        backgroundColor = Color.Black,
    )

    // -- shouldRenderBackdrop -------------------------------------------------

    @Test
    fun `renders when enabled and not in performance mode`() {
        assertTrue(shouldRenderBackdrop(state(enabled = true, performanceMode = false)))
    }

    @Test
    fun `does not render when disabled`() {
        assertFalse(shouldRenderBackdrop(state(enabled = false)))
    }

    @Test
    fun `does not render in performance mode even when enabled`() {
        assertFalse(shouldRenderBackdrop(state(enabled = true, performanceMode = true)))
    }

    @Test
    fun `does not render in light theme even when enabled`() {
        // Light mode shows the plain background fill instead — the colourful
        // ambient/blurhash layers destroy text contrast on a light surface.
        assertFalse(shouldRenderBackdrop(state(enabled = true, isLightTheme = true)))
    }

    // -- resolveBackdropLayer -------------------------------------------------

    @Test
    fun `blur hash takes priority over ambient`() {
        assertEquals(
            BackdropLayer.BLUR_HASH,
            resolveBackdropLayer(state(blurHash = "LFE.WF", backdropUrl = "http://x/img")),
        )
    }

    @Test
    fun `falls back to ambient when no blur hash`() {
        assertEquals(
            BackdropLayer.AMBIENT,
            resolveBackdropLayer(state(blurHash = null, backdropUrl = "http://x/img")),
        )
    }

    @Test
    fun `falls back to ambient when hero section off (no blur hash or url)`() {
        assertEquals(
            BackdropLayer.AMBIENT,
            resolveBackdropLayer(state(blurHash = null, backdropUrl = null)),
        )
    }

    @Test
    fun `blank blur hash is treated as absent`() {
        assertEquals(
            BackdropLayer.AMBIENT,
            resolveBackdropLayer(state(blurHash = "", backdropUrl = null)),
        )
    }
}
