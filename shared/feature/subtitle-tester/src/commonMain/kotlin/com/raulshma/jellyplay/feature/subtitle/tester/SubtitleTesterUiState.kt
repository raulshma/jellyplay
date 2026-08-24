package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilityMatrix

data class SubtitleTesterUiState(
    val mode: SubtitleStyleMode = SubtitleStyleMode.SDR,
    val previewEngine: PlayerType = PlayerType.EXO_PLAYER,
    // The registry itself is androidMain (@RawRes ids); the shared id
    // constant keeps this default tied to SampleSubtitlePresets.DEFAULT.
    val samplePresetId: String = SampleSubtitlePresetIds.DIALOGUE,
    val workingSdrStyle: SubtitleStyle = SubtitleStyle(),
    val workingHdrStyle: SubtitleStyle = SubtitleStyle(),
    val originalSdrStyle: SubtitleStyle = SubtitleStyle(),
    val originalHdrStyle: SubtitleStyle = SubtitleStyle(),
    val hdrSubtitleEnabled: Boolean = false,
    val isApplying: Boolean = false,
) {
    val engineCapabilities: EngineCapabilities
        get() = EngineCapabilityMatrix.forType(previewEngine)

    /**
     * True if either working copy diverges from its snapshot. Compares
     * `applyCustomStyle`-agnostically: the tester always forces it on (the
     * toggle is hidden), so a saved pref with it off shouldn't read as dirty.
     */
    val isDirty: Boolean
        get() = !styleEqualsIgnoringOverride(workingSdrStyle, originalSdrStyle) ||
            !styleEqualsIgnoringOverride(workingHdrStyle, originalHdrStyle)

    private fun styleEqualsIgnoringOverride(a: SubtitleStyle, b: SubtitleStyle): Boolean =
        a.copy(applyCustomStyle = false) == b.copy(applyCustomStyle = false)

    /** The working copy for the active mode. */
    val activeWorkingStyle: SubtitleStyle
        get() = if (mode == SubtitleStyleMode.HDR) workingHdrStyle else workingSdrStyle
}
