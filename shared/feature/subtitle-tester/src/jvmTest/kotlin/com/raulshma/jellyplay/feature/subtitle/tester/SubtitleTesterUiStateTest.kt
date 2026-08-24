package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilityMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * NEW with the subtitle-tester conveyor (no direct legacy equivalent): pins
 * the commonMain [SubtitleTesterUiState] surface — dirty semantics, mode
 * routing and the EngineCapabilityMatrix wiring unlocked by the concurrent
 * move of the matrix into :shared:core:player-contract.
 */
class SubtitleTesterUiStateTest {

    @Test
    fun defaults_areSdrExoPlayerDialoguePreset() {
        val state = SubtitleTesterUiState()
        assertEquals(SubtitleStyleMode.SDR, state.mode)
        assertEquals(PlayerType.EXO_PLAYER, state.previewEngine)
        assertEquals("dialogue", state.samplePresetId)
        assertFalse(state.hdrSubtitleEnabled)
        assertFalse(state.isApplying)
        assertFalse(state.isDirty)
    }

    @Test
    fun activeWorkingStyle_routesByMode() {
        val sdr = SubtitleStyle(fontSize = 30)
        val hdr = SubtitleStyle(fontSize = 40)
        val state = SubtitleTesterUiState(
            mode = SubtitleStyleMode.SDR,
            workingSdrStyle = sdr,
            workingHdrStyle = hdr,
        )
        assertEquals(sdr, state.activeWorkingStyle)
        assertEquals(hdr, state.copy(mode = SubtitleStyleMode.HDR).activeWorkingStyle)
    }

    @Test
    fun isDirty_ignoresApplyCustomStyleDivergenceOnly() {
        // A working copy that differs ONLY in applyCustomStyle is clean (the
        // tester forces the flag on; a saved pref with it off must not read
        // as dirty)…
        val clean = SubtitleTesterUiState(
            workingSdrStyle = SubtitleStyle(fontSize = 28, applyCustomStyle = true),
            originalSdrStyle = SubtitleStyle(fontSize = 28, applyCustomStyle = false),
        )
        assertFalse(clean.isDirty)
        // …but any real style divergence is dirty.
        assertTrue(clean.copy(workingSdrStyle = SubtitleStyle(fontSize = 36)).isDirty)
    }

    @Test
    fun isDirty_coversBothChannels() {
        val base = SubtitleTesterUiState(originalSdrStyle = SubtitleStyle(), originalHdrStyle = SubtitleStyle())
        assertTrue(base.copy(workingHdrStyle = SubtitleStyle(fontSize = 44)).isDirty)
        assertTrue(base.copy(workingSdrStyle = SubtitleStyle(fontFamilyName = "X")).isDirty)
    }

    @Test
    fun engineCapabilities_delegatesToEngineCapabilityMatrix() {
        // Same-package delegation into :shared:core:player-contract — the
        // canonical constant per backend, not a copy.
        PlayerType.entries.forEach { type ->
            val state = SubtitleTesterUiState(previewEngine = type)
            assertSame(EngineCapabilityMatrix.forType(type), state.engineCapabilities)
        }
    }
}
