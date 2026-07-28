package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the canonical default subtitle style and the resolution pivot that
 * guarantees the stored style is always authoritative across every engine.
 *
 * Background: before this, each engine (ExoPlayer / MPV / LibVLC) hardcoded its
 * own "default" for the applyCustomStyle=false state, and those defaults
 * diverged. [SubtitleStyle.DEFAULT] is now the single value every engine reads
 * for a no-edit user, and [UserPreferences.resolvedSubtitleStyle] stamps
 * applyCustomStyle=true so no read path can land in the engine-ignored state.
 */
class SubtitleStyleDefaultsTest {

    @Test
    fun default_isAlwaysAuthoritative() {
        // The stored style is authoritative by construction; the assOverride
        // field (default SCALE) governs embedded ASS authoring per-track.
        assertTrue(SubtitleStyle.DEFAULT.applyCustomStyle)
    }

    @Test
    fun default_matchesCanonicalNoEditValues() {
        // These exact values are what a fresh-install user sees on every engine.
        // If a value changes here, update the per-engine mapping tests in lockstep.
        val d = SubtitleStyle.DEFAULT
        assertEquals(24, d.fontSize)
        assertEquals(SubtitleColor.WHITE, d.fontColor)
        assertEquals(SubtitleColor.BLACK, d.backgroundColor)
        assertEquals(0.0f, d.backgroundOpacity)
        assertEquals(SubtitleEdgeType.OUTLINE, d.edgeType)
        assertEquals(SubtitleColor.BLACK, d.edgeColor)
        assertEquals(0.05f, d.verticalPosition)
        assertEquals(AssOverrideMode.SCALE, d.assOverride)
        assertEquals(SubtitleBorderStyle.OUTLINE_AND_SHADOW, d.borderStyle)
    }

    @Test
    fun default_isEqualToZeroArgConstructor() {
        // DEFAULT is just the no-arg constructor with applyCustomStyle forced true,
        // so old DataStore entries (which default the flag to false) deserialize
        // to identical *visual* values; only the gate differs.
        assertEquals(SubtitleStyle().copy(applyCustomStyle = true), SubtitleStyle.DEFAULT)
    }

    @Test
    fun resolvedSubtitleStyle_forcesApplyCustomStyleForBaseStyle() {
        // A preference whose stored style has applyCustomStyle=false (e.g. an old
        // DataStore entry never re-written) still resolves authoritative.
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(applyCustomStyle = false, fontSize = 30),
        )
        val resolved = prefs.resolvedSubtitleStyle()
        assertTrue("base style must resolve with applyCustomStyle=true", resolved.applyCustomStyle)
        assertEquals(30, resolved.fontSize)
    }

    @Test
    fun resolvedSubtitleStyle_forcesApplyCustomStyleForHdrStyle() {
        val prefs = UserPreferences(
            hdrSubtitleStyleEnabled = true,
            hdrSubtitleStyle = SubtitleStyle(applyCustomStyle = false, fontSize = 28),
        )
        val resolved = prefs.resolvedSubtitleStyle(isHdr = true)
        assertTrue("HDR style must resolve with applyCustomStyle=true", resolved.applyCustomStyle)
        assertEquals(28, resolved.fontSize)
    }

    @Test
    fun resolvedSubtitleStyle_highContrastOverridesEverything() {
        val prefs = UserPreferences(
            highContrastSubtitles = true,
            hdrSubtitleStyleEnabled = true,
            subtitleStyle = SubtitleStyle(fontColor = SubtitleColor.CYAN, fontSize = 18),
        )
        val resolved = prefs.resolvedSubtitleStyle(isHdr = true)
        assertTrue(resolved.applyCustomStyle)
        assertEquals(SubtitleColor.YELLOW, resolved.fontColor)
        assertEquals(1.0f, resolved.backgroundOpacity)
        assertEquals(SubtitleEdgeType.OUTLINE, resolved.edgeType)
    }

    @Test
    fun resolvedSubtitleStyle_nonHdrIgnoresHdrStyle() {
        val prefs = UserPreferences(
            hdrSubtitleStyleEnabled = true,
            subtitleStyle = SubtitleStyle(fontSize = 20),
            hdrSubtitleStyle = SubtitleStyle(fontSize = 40),
        )
        assertEquals(20, prefs.resolvedSubtitleStyle(isHdr = false).fontSize)
        assertEquals(40, prefs.resolvedSubtitleStyle(isHdr = true).fontSize)
    }

    @Test
    fun resolvedSubtitleStyle_preservesDelayAndPosition() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(offsetMs = 1200L, verticalPosition = 0.15f),
        )
        val resolved = prefs.resolvedSubtitleStyle()
        assertEquals(1200L, resolved.offsetMs)
        assertEquals(0.15f, resolved.verticalPosition)
    }

    @Test
    fun resolvedSubtitleStyle_doesNotMutateStoredStyle() {
        // Resolution must not leak applyCustomStyle=true back into the stored
        // preference — copy semantics keep the source intact.
        val stored = SubtitleStyle(applyCustomStyle = false)
        val prefs = UserPreferences(subtitleStyle = stored)
        prefs.resolvedSubtitleStyle()
        assertFalse("stored style must remain untouched", prefs.subtitleStyle.applyCustomStyle)
    }
}
