package com.raulshma.jellyplay.feature.subtitle.tester

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilityMatrix

data class SubtitleTesterUiState(
    val mode: SubtitleStyleMode = SubtitleStyleMode.SDR,
    val previewEngine: PlayerType = PlayerType.EXO_PLAYER,
    val samplePresetId: String = SampleSubtitlePresets.DEFAULT.id,
    val workingSdrStyle: SubtitleStyle = SubtitleStyle(),
    val workingHdrStyle: SubtitleStyle = SubtitleStyle(),
    val originalSdrStyle: SubtitleStyle = SubtitleStyle(),
    val originalHdrStyle: SubtitleStyle = SubtitleStyle(),
    val hdrSubtitleEnabled: Boolean = false,
    val isApplying: Boolean = false,
) {
    val engineCapabilities: EngineCapabilities
        get() = EngineCapabilityMatrix.forType(previewEngine)

    /** True if either working copy diverges from its snapshot. */
    val isDirty: Boolean
        get() = workingSdrStyle != originalSdrStyle || workingHdrStyle != originalHdrStyle

    /** The working copy for the active mode. */
    val activeWorkingStyle: SubtitleStyle
        get() = if (mode == SubtitleStyleMode.HDR) workingHdrStyle else workingSdrStyle
}
