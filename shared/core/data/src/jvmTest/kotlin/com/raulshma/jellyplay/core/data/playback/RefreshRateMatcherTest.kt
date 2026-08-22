package com.raulshma.jellyplay.core.data.playback

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class RefreshRateMatcherTest {

    private fun mode(id: Int, w: Int, h: Int, rate: Float) =
        RefreshRateMatcher.DisplayMode(id, w, h, rate)

    @Test
    fun `tier 1 - exact resolution and exact refresh rate`() {
        val modes = listOf(
            mode(1, 1920, 1080, 60f),
            mode(2, 1920, 1080, 24f),
            mode(3, 3840, 2160, 60f),
        )
        val current = mode(99, 1920, 1080, 60f)
        val result = RefreshRateMatcher.findDisplayMode(modes, current, 24f, allowResolutionSwitch = true)
        assertEquals(2, result?.modeId)
    }

    @Test
    fun `frame rate only prefers a current-resolution cadence match over a higher-res exact match`() {
        val modes = listOf(
            mode(1, 1920, 1080, 60f), // current res; 60 cadences cleanly with 24 (2.5x)
            mode(2, 3840, 2160, 24f), // 4K has the exact rate but is a different res
        )
        val current = mode(99, 1920, 1080, 60f)
        val result = RefreshRateMatcher.findDisplayMode(modes, current, 24f, allowResolutionSwitch = false)
        // Tier 1: 60 matches 24 via 2.5x cadence at current resolution → mode 1.
        // Resolution switch is disallowed, so the 4K@24 mode is never considered.
        assertEquals(1, result?.modeId)
    }

    @Test
    fun `resolution mode upgrades when current resolution has no cadence match`() {
        // Current res only offers 50 Hz (no clean cadence with 24). A higher-res
        // panel mode at exactly 24 Hz exists, so a resolution switch should occur.
        val modes = listOf(
            mode(1, 1920, 1080, 50f),
            mode(2, 3840, 2160, 24f),
        )
        val current = mode(99, 1920, 1080, 50f)
        val result = RefreshRateMatcher.findDisplayMode(modes, current, 24f, allowResolutionSwitch = true)
        assertEquals(2, result?.modeId)
    }

    @Test
    fun `24 to 60 cadence counts as exact match`() {
        // Panel only exposes 60 Hz modes; 24 fps content should match via 2.5x cadence.
        val modes = listOf(
            mode(1, 1920, 1080, 60f),
            mode(2, 3840, 2160, 60f),
        )
        val current = mode(99, 1920, 1080, 60f)
        val result = RefreshRateMatcher.findDisplayMode(modes, current, 24f, allowResolutionSwitch = false)
        // Tier 1/3: 60 matches 24 via 2.5x cadence at current resolution.
        assertEquals(1, result?.modeId)
    }

    @Test
    fun `integer multiple cadence matches`() {
        assertTrue(RefreshRateMatcher.frameRateMatches(60f, 30f))  // 2x
        assertTrue(RefreshRateMatcher.frameRateMatches(120f, 24f)) // 5x
        assertTrue(RefreshRateMatcher.frameRateMatches(48f, 24f))  // 2x
    }

    @Test
    fun `unrelated rates do not match`() {
        assertTrue(!RefreshRateMatcher.frameRateMatches(60f, 50f))
        assertTrue(!RefreshRateMatcher.frameRateMatches(50f, 24f))
    }

    @Test
    fun `null when no suitable mode and resolution switching disabled`() {
        val modes = listOf(mode(1, 1920, 1080, 30f))
        val current = mode(99, 1920, 1080, 30f)
        // Target 24fps; only 30Hz available (no cadence, no tolerance match).
        // Resolution switching off so tier 5 (largest res) never runs → null.
        assertNull(RefreshRateMatcher.findDisplayMode(modes, current, 24f, allowResolutionSwitch = false))
    }

    @Test
    fun `zero or negative target returns null`() {
        val modes = listOf(mode(1, 1920, 1080, 60f))
        val current = mode(99, 1920, 1080, 60f)
        assertNull(RefreshRateMatcher.findDisplayMode(modes, current, 0f, allowResolutionSwitch = true))
        assertNull(RefreshRateMatcher.findDisplayMode(modes, current, -5f, allowResolutionSwitch = true))
    }
}
