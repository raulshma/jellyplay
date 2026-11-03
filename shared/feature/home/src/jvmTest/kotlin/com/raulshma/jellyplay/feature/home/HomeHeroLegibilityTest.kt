package com.raulshma.jellyplay.feature.home

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Covers the pure legibility factories backing the home hero's metadata leg
 * ([heroLegibilityColors], [heroScrimColorStops]):
 *  - the dark branch reproduces the shipped inline chrome exactly (dark mode
 *    must stay byte-identical),
 *  - the light branch frosts the badges light so near-black `onSurface` text
 *    keeps WCAG AA contrast even over a worst-case black backdrop,
 *  - the light scrim washes the text zone onto the background early, while the
 *    dark branches keep the shipped gradients.
 *
 * No Compose composition needed — plain functions over colours.
 */
class HomeHeroLegibilityTest {

    private val lightScheme = lightColorScheme(
        primary = Color(0xFF6750A4),
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1B1B1F),
        outline = Color(0xFF79747E),
    )

    private val backgroundColor = Color(0xFFF7F2FA)

    // -- heroLegibilityColors -------------------------------------------------

    @Test
    fun `dark branch reproduces the shipped chrome exactly`() {
        val onSurface = lightScheme.onSurface
        val dark = heroLegibilityColors(lightScheme, isLightTheme = false)
        assertEquals(onSurface.copy(alpha = 0.1f), dark.badgeContainer)
        assertEquals(onSurface.copy(alpha = 0.15f), dark.badgeBorder)
        assertEquals(onSurface.copy(alpha = 0.9f), dark.badgeText)
        assertEquals(lightScheme.primaryContainer.copy(alpha = 0.2f), dark.accentContainer)
        assertEquals(lightScheme.primary.copy(alpha = 0.4f), dark.accentBorder)
        assertEquals(lightScheme.primary, dark.accentText)
        assertEquals(onSurface.copy(alpha = 0.12f), dark.genreContainer)
        assertEquals(onSurface.copy(alpha = 0.08f), dark.genreBorder)
        assertEquals(onSurface.copy(alpha = 0.85f), dark.genreText)
        assertEquals(onSurface.copy(alpha = 0.1f), dark.pillContainer)
        assertEquals(lightScheme.outline.copy(alpha = 0.15f), dark.pillBorder)
        assertEquals(onSurface, dark.pillText)
        assertEquals(onSurface.copy(alpha = 0.35f), dark.pauseContainer)
        assertEquals(onSurface, dark.pauseContent)
        assertEquals(onSurface.copy(alpha = 0.85f), dark.kickerText)
        assertEquals(onSurface.copy(alpha = 0.7f), dark.overviewText)
    }

    @Test
    fun `light branch frosts the badges onto the light scheme containers`() {
        val light = heroLegibilityColors(lightScheme, isLightTheme = true)
        assertEquals(lightScheme.surface.copy(alpha = 0.8f), light.badgeContainer)
        assertEquals(lightScheme.surface.copy(alpha = 0.72f), light.genreContainer)
        assertEquals(lightScheme.primaryContainer.copy(alpha = 0.9f), light.accentContainer)
        assertEquals(lightScheme.onPrimaryContainer, light.accentText)
        // Text goes to (near-)full-strength onSurface — the shipped 0.7–0.9
        // alphas were tuned against the dark ambient backdrop.
        assertEquals(lightScheme.onSurface, light.badgeText)
        assertEquals(lightScheme.onSurface, light.pillText)
    }

    @Test
    fun `light badge container keeps WCAG AA over a worst-case black backdrop`() {
        val light = heroLegibilityColors(lightScheme, isLightTheme = true)
        // The chrome must not depend on the scrim being behind it: composite
        // each container straight onto pure black artwork and check the text.
        assertTrue(
            contrastRatio(compositeOver(light.badgeContainer, Color.Black), light.badgeText) >= 4.5f,
            "badge text over black under the badge container",
        )
        assertTrue(
            contrastRatio(compositeOver(light.genreContainer, Color.Black), light.genreText) >= 4.5f,
            "genre text over black under the genre container",
        )
        assertTrue(
            contrastRatio(compositeOver(light.accentContainer, Color.Black), light.accentText) >= 4.5f,
            "official-rating text over black under the accent container",
        )
        assertTrue(
            contrastRatio(compositeOver(light.pillContainer, Color.Black), light.pillText) >= 4.5f,
            "details pill text over black under the pill container",
        )
    }

    // -- heroScrimColorStops --------------------------------------------------

    @Test
    fun `dark with backdrop keeps the shipped top-only scrim`() {
        assertEquals(
            listOf(
                0.0f to Color.Black.copy(alpha = 0.45f),
                0.3f to Color.Transparent,
                0.6f to Color.Transparent,
                1.0f to Color.Transparent,
            ),
            heroScrimColorStops(backgroundColor, homeBackdropEnabled = true, isLightTheme = false),
        )
    }

    @Test
    fun `dark without backdrop keeps the shipped fade to the background`() {
        // The shipped call built this as an evenly-spaced colors list; the stop
        // form renders identically (an infinite endY maps onto the draw size).
        assertEquals(
            listOf(
                0.0f to Color.Black.copy(alpha = 0.45f),
                1f / 3f to Color.Transparent,
                2f / 3f to backgroundColor.copy(alpha = 0.3f),
                1.0f to backgroundColor,
            ),
            heroScrimColorStops(backgroundColor, homeBackdropEnabled = false, isLightTheme = false),
        )
    }

    @Test
    fun `light scrim keeps the top scrim and washes the text zone onto the background`() {
        val stops = heroScrimColorStops(backgroundColor, homeBackdropEnabled = true, isLightTheme = true)
        // Shipped top scrim, kept.
        assertEquals(Color.Black.copy(alpha = 0.45f), stops.first().second)
        assertEquals(Color.Transparent, stops.first { it.first == 0.3f }.second)
        // The artwork's DstIn dissolve only starts at ~0.55 — the wash must be
        // well under way before that so the text zone never rides on raw art.
        val washStart = stops.first { it.first == 0.48f }.second
        assertEquals(backgroundColor.copy(alpha = 0.6f), washStart)
        // Fully opaque by 0.8 → seamless handoff into the flat light fill that
        // replaces the ambient backdrop in light mode.
        val last = stops.last()
        assertEquals(1.0f, last.first)
        assertEquals(backgroundColor, last.second)
        assertEquals(1f, last.second.alpha)
    }

    @Test
    fun `light scrim ignores the backdrop pref (the ambient layer is suppressed in light)`() {
        assertEquals(
            heroScrimColorStops(backgroundColor, homeBackdropEnabled = true, isLightTheme = true),
            heroScrimColorStops(backgroundColor, homeBackdropEnabled = false, isLightTheme = true),
        )
    }

    // -- WCAG helpers ---------------------------------------------------------

    /** WCAG 2.x relative luminance of a fully-opaque colour. */
    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f
            else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** Source-over composite of [top] (with its alpha) onto an opaque [bottom]. */
    private fun compositeOver(top: Color, bottom: Color): Color = Color(
        red = top.red * top.alpha + bottom.red * (1f - top.alpha),
        green = top.green * top.alpha + bottom.green * (1f - top.alpha),
        blue = top.blue * top.alpha + bottom.blue * (1f - top.alpha),
        alpha = 1f,
    )
}
