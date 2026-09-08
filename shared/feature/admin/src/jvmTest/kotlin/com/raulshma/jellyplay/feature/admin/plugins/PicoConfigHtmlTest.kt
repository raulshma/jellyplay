package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure plugin-config WebView builders ([PicoConfigHtml.kt]) that used
 * to live as private helpers in the androidMain `PluginConfigScreen.kt`, where
 * no CI test lane could reach them (the jvmTest lane compiles commonMain +
 * jvmMain only — see [PluginConfigHostJvmTest]'s KDoc for the same split):
 *
 *  - [colorToHex] formats the RGB channels as lowercase `#rrggbb` and DROPS
 *    the alpha channel (Pico CSS variables are opaque; the WebView background
 *    is set natively).
 *  - [buildPicoOverrides] maps the Compose color scheme onto the Pico CSS
 *    variable table: scheme colors thread through verbatim, the alpha-suffixed
 *    hover/focus/overlay variants append their hex suffixes, and the one
 *    dark/light literal branch (`--pico-ins-color`) picks exactly its side.
 *  - [buildWrappedHtml] wraps each of the four HTML shapes so the bridge
 *    script lands at the very top of `<head>` (before any inline script can
 *    read `window.ApiClient`) and the Pico stylesheet + overrides land before
 *    `</head>`; fragments get the viewport/color-scheme metas they lack.
 */
class PicoConfigHtmlTest {

    // ── colorToHex: table incl. alpha handling ───────────────────────────────

    @Test
    fun `opaque colors render as lowercase rrggbb`() {
        assertEquals("#ff0000", colorToHex(Color(0xFFFF0000)))
        assertEquals("#00ff7f", colorToHex(Color(0xFF00FF7F)))
        assertEquals("#102030", colorToHex(Color(0xFF102030)))
        assertEquals("#abcdef", colorToHex(Color(0xFFABCDEF)))
        assertEquals("#000000", colorToHex(Color(0xFF000000)))
        assertEquals("#ffffff", colorToHex(Color(0xFFFFFFFF)))
    }

    @Test
    fun `the alpha channel is dropped not blended`() {
        // Edge: even a fully-transparent color keeps its RGB channels — the
        // overrides are opaque by contract, the WebView paints the background.
        assertEquals("#ff8000", colorToHex(Color(0x00FF8000)))
        assertEquals("#ff8000", colorToHex(Color(0x80FF8000)))
        assertEquals("#ff8000", colorToHex(Color(0xFFFF8000)))
    }

    // ── buildPicoOverrides: colorScheme CSS injection ────────────────────────

    @Test
    fun `scheme colors thread into their pico variables verbatim`() {
        val overrides = buildPicoOverrides(
            isDark = false,
            colors = lightColorScheme(
                primary = Color(0xFF123456),
                background = Color(0xFFFEDCBA),
                onBackground = Color(0xFF0A0B0C),
            ),
        )

        assertTrue(overrides.contains("--pico-primary: #123456;"))
        assertTrue(overrides.contains("--pico-primary-background: #123456;"))
        assertTrue(overrides.contains("--pico-primary-border: #123456;"))
        assertTrue(overrides.contains("--pico-background-color: #fedcba;"))
        assertTrue(overrides.contains("--pico-color: #0a0b0c;"))
        // The derived variants append their hex alpha to the same base color.
        assertTrue(overrides.contains("--pico-primary-underline: #12345680;"))
        assertTrue(overrides.contains("--pico-primary-focus: #12345660;"))
        // Shape constants ride along untouched.
        assertTrue(overrides.contains("--pico-border-radius: 0.75rem;"))
    }

    @Test
    fun `every template variable is substituted`() {
        val overrides = buildPicoOverrides(isDark = true, colors = darkColorScheme())
        // A fully-interpolated override block has no leftover `$` placeholder.
        assertFalse(overrides.contains('$'))
        // The block is a :root rule set.
        assertTrue(overrides.startsWith(":root {"))
    }

    @Test
    fun `the ins-color branch picks exactly its dark or light literal`() {
        val dark = buildPicoOverrides(isDark = true, colors = darkColorScheme())
        val light = buildPicoOverrides(isDark = false, colors = lightColorScheme())

        assertTrue(dark.contains("--pico-ins-color: #62af9a;"))
        assertFalse(dark.contains("#1c6954"))
        assertTrue(light.contains("--pico-ins-color: #1c6954;"))
        assertFalse(light.contains("#62af9a"))
    }

    @Test
    fun `distinct schemes produce distinct overrides`() {
        // The injection reads the CALLER's color scheme, not a baked-in one —
        // two schemes must not collapse to the same CSS.
        val a = buildPicoOverrides(isDark = false, colors = lightColorScheme(primary = Color(0xFF111111)))
        val b = buildPicoOverrides(isDark = false, colors = lightColorScheme(primary = Color(0xFF222222)))
        assertTrue(a.contains("--pico-primary: #111111;"))
        assertTrue(b.contains("--pico-primary: #222222;"))
    }

    // ── buildWrappedHtml: wrapper shapes ─────────────────────────────────────

    private val bridge = "window.ApiClient={};window.Dashboard={};"
    private val overrides = ":root { --pico-primary: #123456; }"

    @Test
    fun `a full document gets the theme attribute bridge script and stylesheet`() {
        val html = buildWrappedHtml(
            originalHtml = "<html lang=\"en\"><head><title>T</title></head><body>X</body></html>",
            theme = "dark",
            overrides = overrides,
            bridgeScript = bridge,
        )

        // data-theme rides the existing <html> tag…
        assertTrue(html.contains("<html lang=\"en\" data-theme=\"dark\">"))
        // …the bridge script is the FIRST thing inside <head> (before the
        // page's own <title>, hence before any inline script)…
        val head = html.indexOf("<head>")
        assertTrue(html.contains("<head><script>$bridge</script>"))
        assertTrue(html.indexOf("<script>$bridge</script>") < html.indexOf("<title>"))
        assertTrue(head >= 0)
        // …and the Pico stylesheet + overrides land before </head>.
        val stylesheet = html.indexOf("pico.min.css")
        val style = html.indexOf("<style>$overrides</style>")
        val headEnd = html.indexOf("</head>")
        assertTrue(stylesheet in 0..headEnd)
        assertTrue(style in 0..headEnd)
        assertTrue(stylesheet < style)
    }

    @Test
    fun `a head-plus-body fragment gets the full synthetic head`() {
        val html = buildWrappedHtml(
            originalHtml = "<head></head><body>X</body>",
            theme = "light",
            overrides = overrides,
            bridgeScript = bridge,
        )

        // Fragments lack charset/viewport/color-scheme — the wrapper supplies
        // them, then the bridge script, then the Pico stylesheet.
        val injected = html.indexOf("<meta charset=\"utf-8\">")
        val viewport = html.indexOf("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        val colorScheme = html.indexOf("<meta name=\"color-scheme\" content=\"light dark\">")
        val script = html.indexOf("<script>$bridge</script>")
        val stylesheet = html.indexOf("pico.min.css")
        assertTrue(injected >= 0)
        assertTrue(viewport > injected)
        assertTrue(colorScheme > viewport)
        assertTrue(script > colorScheme)
        assertTrue(stylesheet > script)
        // No synthetic <html>/data-theme for fragments that never had one.
        assertFalse(html.contains("data-theme"))
    }

    @Test
    fun `a body-only fragment gets the synthetic head after the body tag`() {
        val html = buildWrappedHtml(
            originalHtml = "<body class=\"cfg\">X</body>",
            theme = "dark",
            overrides = overrides,
            bridgeScript = bridge,
        )

        val body = html.indexOf("<body class=\"cfg\">")
        val viewport = html.indexOf("<meta name=\"viewport\"")
        assertTrue(body >= 0)
        assertTrue(viewport > body)
        assertTrue(html.contains("<script>$bridge</script>"))
        assertTrue(html.contains("pico.min.css"))
        assertFalse(html.contains("data-theme"))
    }

    @Test
    fun `a bare fragment is wrapped in a full container document`() {
        val html = buildWrappedHtml(
            originalHtml = "<p>raw config row</p>",
            theme = "dark",
            overrides = overrides,
            bridgeScript = bridge,
        )

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<html lang=\"en\" data-theme=\"dark\">"))
        // The fragment lands inside Pico's container, after the synthetic head.
        val headEnd = html.indexOf("</head>")
        val container = html.indexOf("<main class=\"container\"><p>raw config row</p></main>")
        assertTrue(headEnd >= 0)
        assertTrue(container > headEnd)
        assertTrue(html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"))
        assertTrue(html.contains("<script>$bridge</script>"))
        assertTrue(html.contains("<style>$overrides</style>"))
    }

    @Test
    fun `the pico stylesheet url is the pinned cdn url`() {
        val html = buildWrappedHtml("x", "dark", overrides, bridge)
        assertTrue(
            html.contains(
                "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css",
            ),
        )
    }
}
