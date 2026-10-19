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
 *  - [withFocusBackdrop] (the focus overlay + throttle fallback)
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

    // -- withFocusBackdrop ----------------------------------------------

    @Test
    fun `no focus keeps the hero-driven state`() {
        // No TV focus (or the pref off → the controller
        // never produces inputs) must leave the hero artwork pair untouched.
        val hero = state(blurHash = "HERO", backdropUrl = "http://x/hero")
        assertEquals(hero, hero.withFocusBackdrop(focus = null))
    }

    @Test
    fun `focus without content falls back to the hero-driven state`() {
        // The throttle fallback: arrival on a card with no backdrop BlurHash
        // (before its URL settles) and the focus-exit reset both produce empty
        // inputs — the hero passes through untouched, no default-palette flash.
        val hero = state(blurHash = "HERO", backdropUrl = "http://x/hero")
        assertEquals(hero, hero.withFocusBackdrop(FocusBackdrop(blurHash = null, backdropUrl = null)))
        assertEquals(hero, hero.withFocusBackdrop(FocusBackdrop(blurHash = "", backdropUrl = "")))
    }

    @Test
    fun `focus arrival replaces the hero blur hash`() {
        val resolved = state(blurHash = "HERO", backdropUrl = "http://x/hero")
            .withFocusBackdrop(FocusBackdrop(blurHash = "FOCUS", backdropUrl = null))
        assertEquals("FOCUS", resolved.blurHash)
        // The layer keys off the blurhash alone, so the (not-yet-settled) URL
        // is inert — but it must not keep the hero URL around either.
        assertEquals(BackdropLayer.BLUR_HASH, resolveBackdropLayer(resolved))
    }

    @Test
    fun `focus url without blur hash drives the ambient layer`() {
        // A focused card with no BlurHash shows its palette through the URL
        // half once the settle window has passed.
        val resolved = state(blurHash = "HERO", backdropUrl = "http://x/hero")
            .withFocusBackdrop(FocusBackdrop(blurHash = null, backdropUrl = "http://x/focus"))
        assertEquals(BackdropLayer.AMBIENT, resolveBackdropLayer(resolved))
        assertEquals("http://x/focus", resolved.backdropUrl)
    }

    @Test
    fun `settled focus replaces both hero inputs`() {
        val resolved = state(blurHash = "HERO", backdropUrl = "http://x/hero")
            .withFocusBackdrop(FocusBackdrop(blurHash = "FOCUS", backdropUrl = "http://x/focus"))
        assertEquals("FOCUS", resolved.blurHash)
        assertEquals("http://x/focus", resolved.backdropUrl)
        assertEquals(BackdropLayer.BLUR_HASH, resolveBackdropLayer(resolved))
    }

    @Test
    fun `focus overlay does not bypass the render gates`() {
        // The focus inputs ride the same state shape, so a disabled backdrop,
        // performance mode or a light theme still suppress the whole layer —
        // the pref ∧ TV gate upstream never reaches here.
        val focused = state(
            enabled = false,
            blurHash = "HERO",
        ).withFocusBackdrop(FocusBackdrop(blurHash = "FOCUS", backdropUrl = null))
        assertFalse(shouldRenderBackdrop(focused))
    }
}
