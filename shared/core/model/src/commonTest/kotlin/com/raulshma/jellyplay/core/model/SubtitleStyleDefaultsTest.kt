package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the canonical default subtitle style that governs when a stored style
 * is applied verbatim across every engine.
 *
 * Background: before this, each engine (ExoPlayer / MPV / LibVLC) hardcoded its
 * own "default" for the applyCustomStyle=false state, and those defaults
 * diverged. [SubtitleStyle.DEFAULT] is now the single value every engine reads
 * for a no-edit user. (The style-resolution semantics — Override toggle,
 * HDR and high-contrast branches — live with the player's
 * `SubtitleStyleResolver` and its test since the legacy aggregate went away.)
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
}
