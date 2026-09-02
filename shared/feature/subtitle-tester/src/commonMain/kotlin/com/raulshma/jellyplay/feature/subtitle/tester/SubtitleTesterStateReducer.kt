package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * Pure state reductions for the subtitle tester, extracted from the (Android-
 * only) [SubtitleTesterViewModel] so the tester's state machine is unit-
 * testable on the JVM (subtitle-tester conveyor, feature seventeen: the VM
 * itself lives in this module's androidMain — its engine factory, font
 * provider and raw-asset dependencies are Android/Hilt types — and the AGP 9
 * KMP library plugin exposes no androidMain unit-test compilation, so the
 * portable half of the legacy VM test suite pins these functions instead).
 *
 * Every function is a total, side-effect-free rewrite of the corresponding
 * `_uiState.update { ... }` body the ViewModel ran at HEAD; the VM delegates
 * to them one-for-one.
 */
internal object SubtitleTesterStateReducer {

    /**
     * Seeds the working copies from the current subtitle prefs (VM init,
     * first emission only). Forces `applyCustomStyle = true` on the working
     * copies — the tester hides the override toggle, so every edit must take
     * effect on the preview — while the originals stay verbatim for accurate
     * dirty checks (see [SubtitleTesterUiState.isDirty]).
     */
    fun seed(state: SubtitleTesterUiState, prefs: SubtitleSlice): SubtitleTesterUiState =
        state.copy(
            workingSdrStyle = prefs.subtitleStyle.copy(applyCustomStyle = true),
            originalSdrStyle = prefs.subtitleStyle,
            workingHdrStyle = prefs.hdrSubtitleStyle.copy(applyCustomStyle = true),
            originalHdrStyle = prefs.hdrSubtitleStyle,
            hdrSubtitleEnabled = prefs.hdrSubtitleStyleEnabled,
        )

    /**
     * Routes a style edit into the ACTIVE mode's working copy (VM
     * `updateStyle`): SDR edits never touch the HDR copy and vice versa.
     */
    fun applyStyleUpdate(state: SubtitleTesterUiState, style: SubtitleStyle): SubtitleTesterUiState =
        if (state.mode == SubtitleStyleMode.HDR) {
            state.copy(workingHdrStyle = style)
        } else {
            state.copy(workingSdrStyle = style)
        }

    /**
     * Stamps an installed user font (resolved file path + family name) onto
     * the active mode's working style (VM `installUserFont`). Mirrors the
     * player's flow: the SAF uri is not persisted (only the copied local
     * file survives). The other mode's copy is untouched.
     */
    fun applyInstalledFont(
        state: SubtitleTesterUiState,
        fontFamilyPath: String,
        fontFamilyName: String,
    ): SubtitleTesterUiState =
        applyStyleUpdate(
            state,
            state.activeWorkingStyle.copy(
                fontFamilyPath = fontFamilyPath,
                fontFamilyName = fontFamilyName,
            ),
        )

    /**
     * Restores both working copies to their seeded originals (VM `reset`).
     * Deliberately does NOT persist anything — cancellation semantics.
     */
    fun restoreOriginals(state: SubtitleTesterUiState): SubtitleTesterUiState =
        state.copy(
            workingSdrStyle = state.originalSdrStyle,
            workingHdrStyle = state.originalHdrStyle,
        )
}
