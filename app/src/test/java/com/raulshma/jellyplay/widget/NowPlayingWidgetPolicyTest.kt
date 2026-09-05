package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.ui.components.formatDurationMsNoHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the Now Playing widget's policy module — the responsive
 * visibility ladder, the position label formatters, the seek math, and the
 * progress level, split out of `NowPlayingWidget` so the rules are testable
 * without RemoteViews or a widget host.
 *
 * Pins:
 *  - every rung of the responsive ladder at its exact thresholds (width
 *    180/280, height 100/70, strict inequalities) on both dimensions,
 *    including that the height rules run after the width rules and fully
 *    re-decide the progress row, so a compact-but-tall widget still shows
 *    progress;
 *  - formatMs/formatPosition: zero, sub-minute, the 60s boundary, past an
 *    hour (no hour branch — plain minutes), negative clamping, the unknown
 *    duration placeholder, and the "Paused ·" prefix;
 *  - seek math: the closed 0–100 broadcast gate, percent→position
 *    truncation, endpoint percents, out-of-range clamping, and the unknown
 *    duration no-op;
 *  - the progress level's 0–1000 scale and clamping, the metadata fallback
 *    labels, and the seek-zone constants.
 */
class NowPlayingWidgetPolicyTest {

    // ── responsiveNowPlayingLayout (width ladder) ────────────────────────

    @Test
    fun `compact width below 180dp drops artwork, subtitle and secondary seek buttons`() {
        val layout = responsiveNowPlayingLayout(widthDp = 179, heightDp = 110)

        assertFalse(layout.showAlbumArt)
        assertFalse(layout.showSubtitle)
        assertFalse(layout.showRewind)
        assertFalse(layout.showForward)
        // The transport row survives every rung — compact keeps prev/next so
        // the widget stays usable down to its 110dp min-resize width.
        assertTrue(layout.showPrev)
        assertTrue(layout.showNext)
        assertTrue(layout.showPlayPause)
    }

    @Test
    fun `compact width keeps the progress row when the widget is tall`() {
        // The height rules run after the width rules and re-decide the
        // progress row in both arms, so the compact branch's GONE for it is
        // superseded — progress visibility depends on height alone.
        val layout = responsiveNowPlayingLayout(widthDp = 110, heightDp = 250)

        assertFalse(layout.showAlbumArt)
        assertTrue(layout.showProgressContainer)
        assertTrue(layout.showPosition)
    }

    @Test
    fun `medium width from 180dp restores artwork and subtitle but not rewind-forward`() {
        val layout = responsiveNowPlayingLayout(widthDp = 180, heightDp = 110)

        assertTrue(layout.showAlbumArt)
        assertTrue(layout.showSubtitle)
        assertFalse(layout.showRewind)
        assertFalse(layout.showForward)
    }

    @Test
    fun `the secondary seek buttons return exactly at 280dp`() {
        assertFalse(responsiveNowPlayingLayout(widthDp = 279, heightDp = 110).showRewind)
        val layout = responsiveNowPlayingLayout(widthDp = 280, heightDp = 110)

        assertTrue(layout.showRewind)
        assertTrue(layout.showForward)
    }

    @Test
    fun `a full-size widget shows everything`() {
        assertEquals(
            NowPlayingWidgetLayout(
                showAlbumArt = true,
                showProgressContainer = true,
                showPosition = true,
                showSubtitle = true,
                showRewind = true,
                showForward = true,
                showPrev = true,
                showNext = true,
                showPlayPause = true,
            ),
            responsiveNowPlayingLayout(widthDp = 300, heightDp = 110),
        )
    }

    // ── responsiveNowPlayingLayout (height ladder) ───────────────────────

    @Test
    fun `height below 100dp hides the progress row even at full width`() {
        val layout = responsiveNowPlayingLayout(widthDp = 300, heightDp = 99)

        assertFalse(layout.showProgressContainer)
        assertFalse(layout.showPosition)
        // The width-driven elements are untouched by the height rule.
        assertTrue(layout.showAlbumArt)
        assertTrue(layout.showSubtitle)
    }

    @Test
    fun `height 100dp is the inclusive progress threshold`() {
        val layout = responsiveNowPlayingLayout(widthDp = 300, heightDp = 100)

        assertTrue(layout.showProgressContainer)
        assertTrue(layout.showPosition)
    }

    @Test
    fun `height below 70dp additionally hides the subtitle`() {
        val layout = responsiveNowPlayingLayout(widthDp = 300, heightDp = 69)

        assertFalse(layout.showSubtitle)
        // 69 < 100, so the progress row is gone too.
        assertFalse(layout.showProgressContainer)
        assertFalse(layout.showPosition)
    }

    @Test
    fun `height 70dp is the inclusive subtitle threshold`() {
        assertTrue(responsiveNowPlayingLayout(widthDp = 300, heightDp = 70).showSubtitle)
    }

    // ── formatMs ─────────────────────────────────────────────────────────

    @Test
    fun `milliseconds floor to whole seconds`() {
        assertEquals("0:00", formatDurationMsNoHours(0L))
        assertEquals("0:00", formatDurationMsNoHours(999L))
        assertEquals("0:01", formatDurationMsNoHours(1_000L))
        assertEquals("0:01", formatDurationMsNoHours(1_999L))
    }

    @Test
    fun `seconds zero-pad to two digits while minutes stay unpadded`() {
        assertEquals("0:05", formatDurationMsNoHours(5_000L))
        assertEquals("0:59", formatDurationMsNoHours(59_999L))
        assertEquals("9:59", formatDurationMsNoHours(599_999L))
        assertEquals("10:00", formatDurationMsNoHours(600_000L))
    }

    @Test
    fun `exactly sixty seconds rolls into the minutes place`() {
        assertEquals("1:00", formatDurationMsNoHours(60_000L))
    }

    @Test
    fun `durations past an hour render as plain minutes`() {
        // No hour branch — 61 minutes 1 second, not 1:01:01.
        assertEquals("61:01", formatDurationMsNoHours(3_661_000L))
    }

    @Test
    fun `negative input clamps to zero`() {
        assertEquals("0:00", formatDurationMsNoHours(-1L))
        assertEquals("0:00", formatDurationMsNoHours(-61_000L))
    }

    // ── formatPosition ───────────────────────────────────────────────────

    @Test
    fun `playing renders current over total`() {
        assertEquals("1:05 / 3:20", formatPosition(65_000L, 200_000L, isPlaying = true))
    }

    @Test
    fun `paused prefixes the label`() {
        assertEquals("Paused · 1:05 / 3:20", formatPosition(65_000L, 200_000L, isPlaying = false))
    }

    @Test
    fun `unknown duration renders the em dash regardless of state`() {
        assertEquals("—", formatPosition(65_000L, 0L, isPlaying = true))
        assertEquals("—", formatPosition(65_000L, -1L, isPlaying = false))
    }

    @Test
    fun `zero position renders zero over the total`() {
        assertEquals("0:00 / 1:00", formatPosition(0L, 60_000L, isPlaying = true))
    }

    // ── seek percent gate + target ───────────────────────────────────────

    @Test
    fun `the broadcast gate accepts exactly the closed 0 to 100 range`() {
        // -1 is the missing-extra default from getIntExtra.
        assertFalse(isValidSeekPercent(-1))
        assertTrue(isValidSeekPercent(0))
        assertTrue(isValidSeekPercent(50))
        assertTrue(isValidSeekPercent(100))
        assertFalse(isValidSeekPercent(101))
    }

    @Test
    fun `endpoint and midpoint percents map exactly`() {
        assertEquals(0L, seekTargetMs(0, 200_000L))
        assertEquals(100_000L, seekTargetMs(50, 200_000L))
        assertEquals(200_000L, seekTargetMs(100, 200_000L))
    }

    @Test
    fun `percent math truncates rather than rounds`() {
        // 33% of 100_001ms is 33_000.33ms → 33_000ms.
        assertEquals(33_000L, seekTargetMs(33, 100_001L))
        assertEquals(17_000L, seekTargetMs(17, 100_000L))
    }

    @Test
    fun `unknown duration yields no seek`() {
        assertNull(seekTargetMs(50, 0L))
        assertNull(seekTargetMs(50, -1L))
    }

    @Test
    fun `out-of-range percents clamp to the endpoints`() {
        // Defensive: the broadcast gate rejects these before the math runs,
        // but the clamp keeps the arithmetic total.
        assertEquals(0L, seekTargetMs(-20, 200_000L))
        assertEquals(200_000L, seekTargetMs(150, 200_000L))
    }

    // ── progressPerMille ─────────────────────────────────────────────────

    @Test
    fun `unknown duration renders an empty bar`() {
        assertEquals(0, progressPerMille(65_000L, 0L))
        assertEquals(0, progressPerMille(65_000L, -1L))
    }

    @Test
    fun `the level is per-mille of the duration`() {
        assertEquals(0, progressPerMille(0L, 200_000L))
        assertEquals(500, progressPerMille(100_000L, 200_000L))
        assertEquals(1_000, progressPerMille(200_000L, 200_000L))
    }

    @Test
    fun `positions outside the duration clamp to the bar ends`() {
        assertEquals(1_000, progressPerMille(250_000L, 200_000L))
        assertEquals(0, progressPerMille(-5_000L, 200_000L))
    }

    @Test
    fun `the level truncates rather than rounds`() {
        // 999/2000 = 499.5‰ → 499.
        assertEquals(499, progressPerMille(999L, 2_000L))
    }

    // ── metadata fallbacks + seek-zone constants ─────────────────────────

    @Test
    fun `blank metadata falls back to the em dash title and space subtitle`() {
        assertEquals("—", widgetDisplayTitle(null))
        assertEquals("—", widgetDisplayTitle(" "))
        assertEquals("Episode 1", widgetDisplayTitle("Episode 1"))
        assertEquals(" ", widgetDisplaySubtitle(null))
        assertEquals(" ", widgetDisplaySubtitle("  "))
        assertEquals("Artist", widgetDisplaySubtitle("Artist"))
    }

    @Test
    fun `the seek zones are seven evenly spaced stops across the duration`() {
        assertEquals(listOf(0, 17, 33, 50, 67, 83, 100), SEEK_PERCENTS.toList())
        assertEquals(10_000L, SEEK_DELTA_MS)
    }
}
