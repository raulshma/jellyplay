package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ports the state-machine half of the legacy SubtitleTesterViewModelTest
 * (subtitle-tester conveyor): the ViewModel itself lives in this module's
 * androidMain (engine factory / font provider / raw-asset deps are
 * Android types) and the AGP 9 KMP library plugin exposes no androidMain
 * unit-test compilation, so its `_uiState.update { ... }` bodies were
 * extracted verbatim into [SubtitleTesterStateReducer] (commonMain) and are
 * pinned here instead. The engine-lifecycle half of the legacy
 * suite (13 tests: mock MediaEngine release/reload/loop/activeEngine
 * surfacing, preset-registry integrity, applyAndExit/reset persistence
 * writes, mode-copy updates, null-font-install no-op) has no portable
 * equivalent and is covered only by compileAndroidMain — documented in
 * docs/kmp-migration-plan.md §Phase V3.
 */
class SubtitleTesterStateReducerTest {

    @Test
    fun seed_snapshotsCurrentPrefsAsWorkingAndOriginal() {
        val sdrStyle = SubtitleStyle(fontSize = 30, fontColor = SubtitleColor.RED)
        val hdrStyle = SubtitleStyle(fontSize = 40)
        val prefs = SubtitleSlice(
            subtitleStyle = sdrStyle,
            hdrSubtitleStyle = hdrStyle,
            hdrSubtitleStyleEnabled = true,
        )

        val state = SubtitleTesterStateReducer.seed(SubtitleTesterUiState(), prefs)

        // The tester forces applyCustomStyle = true on the working copies (the
        // override toggle is hidden); originals stay verbatim for dirty checks.
        assertEquals(sdrStyle.copy(applyCustomStyle = true), state.workingSdrStyle)
        assertEquals(sdrStyle, state.originalSdrStyle)
        assertEquals(hdrStyle.copy(applyCustomStyle = true), state.workingHdrStyle)
        assertEquals(hdrStyle, state.originalHdrStyle)
        assertTrue(state.hdrSubtitleEnabled)
        assertFalse(state.isDirty)
    }

    @Test
    fun seed_isNotDirtyEvenWhenPrefsDisableApplyCustomStyle() {
        // A saved pref with applyCustomStyle off must not read as dirty: the
        // tester always forces it on (the toggle is hidden), so only the
        // forced flag differs between original and working copy.
        val style = SubtitleStyle(applyCustomStyle = false, fontSize = 32)
        val state = SubtitleTesterStateReducer.seed(
            SubtitleTesterUiState(),
            SubtitleSlice(subtitleStyle = style, hdrSubtitleStyle = style),
        )
        assertTrue(state.workingSdrStyle.applyCustomStyle)
        assertFalse(state.isDirty)
    }

    @Test
    fun applyStyleUpdate_inSdrMode_updatesSdrWorkingCopy() {
        var state = SubtitleTesterUiState(mode = SubtitleStyleMode.SDR)
        state = SubtitleTesterStateReducer.seed(
            state,
            SubtitleSlice(subtitleStyle = SubtitleStyle(fontSize = 28), hdrSubtitleStyle = SubtitleStyle(fontSize = 28)),
        )
        val hdrBefore = state.workingHdrStyle

        state = SubtitleTesterStateReducer.applyStyleUpdate(state, state.workingSdrStyle.copy(fontSize = 36))

        assertEquals(36, state.workingSdrStyle.fontSize)
        // HDR untouched.
        assertEquals(hdrBefore, state.workingHdrStyle)
        assertTrue(state.isDirty)
    }

    @Test
    fun applyStyleUpdate_inHdrMode_updatesHdrWorkingCopy() {
        var state = SubtitleTesterUiState(mode = SubtitleStyleMode.HDR)
        state = SubtitleTesterStateReducer.seed(
            state,
            SubtitleSlice(subtitleStyle = SubtitleStyle(fontSize = 28), hdrSubtitleStyle = SubtitleStyle(fontSize = 28)),
        )
        val sdrBefore = state.workingSdrStyle

        state = SubtitleTesterStateReducer.applyStyleUpdate(state, state.workingHdrStyle.copy(fontSize = 44))

        assertEquals(44, state.workingHdrStyle.fontSize)
        // SDR untouched.
        assertEquals(sdrBefore, state.workingSdrStyle)
        assertTrue(state.isDirty)
    }

    @Test
    fun restoreOriginals_restoresBothCopiesAndClearsDirty() {
        var state = SubtitleTesterStateReducer.seed(
            SubtitleTesterUiState(),
            SubtitleSlice(
                subtitleStyle = SubtitleStyle(fontSize = 30),
                hdrSubtitleStyle = SubtitleStyle(fontSize = 40),
            ),
        )
        state = SubtitleTesterStateReducer.applyStyleUpdate(state, state.workingSdrStyle.copy(fontSize = 50))
        assertTrue(state.isDirty)

        state = SubtitleTesterStateReducer.restoreOriginals(state)

        assertEquals(state.originalSdrStyle, state.workingSdrStyle)
        assertEquals(state.originalHdrStyle, state.workingHdrStyle)
        assertFalse(state.isDirty)
    }

    @Test
    fun applyInstalledFont_stampsActiveModeStyleWithInstalledFont() {
        var state = SubtitleTesterStateReducer.seed(
            SubtitleTesterUiState(mode = SubtitleStyleMode.SDR),
            SubtitleSlice(subtitleStyle = SubtitleStyle(), hdrSubtitleStyle = SubtitleStyle()),
        )

        state = SubtitleTesterStateReducer.applyInstalledFont(
            state,
            fontFamilyPath = "/data/cache/subtitle-fonts/MyFont.ttf",
            fontFamilyName = "MyFont",
        )

        val style = state.workingSdrStyle
        assertEquals("/data/cache/subtitle-fonts/MyFont.ttf", style.fontFamilyPath)
        assertEquals("MyFont", style.fontFamilyName)
        // HDR copy untouched.
        assertNull(state.workingHdrStyle.fontFamilyPath)
    }

    @Test
    fun applyInstalledFont_inHdrMode_stampsHdrCopyOnly() {
        var state = SubtitleTesterStateReducer.seed(
            SubtitleTesterUiState(mode = SubtitleStyleMode.HDR),
            SubtitleSlice(subtitleStyle = SubtitleStyle(), hdrSubtitleStyle = SubtitleStyle()),
        )

        state = SubtitleTesterStateReducer.applyInstalledFont(
            state,
            fontFamilyPath = "/data/cache/subtitle-fonts/Other.ttf",
            fontFamilyName = "Other",
        )

        assertEquals("Other", state.workingHdrStyle.fontFamilyName)
        assertNull(state.workingSdrStyle.fontFamilyName)
    }
}
