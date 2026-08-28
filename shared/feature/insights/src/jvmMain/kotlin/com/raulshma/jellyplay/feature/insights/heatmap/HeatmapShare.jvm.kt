package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.layer.GraphicsLayer

// Desktop v1 has no share sheet and no capture API (settings
// BackupFilePicker / editor EditorFilePicker precedent): the actual returns
// null, so the screen's share IconButton stays hidden and the one-shot share
// effect can never fire. Every other heatmap affordance (grid, day detail,
// filters, year navigation) is platform-free and fully usable. The recorded
// [captureLayer] is simply ignored — the screen still re-records it per draw
// pass, which is draw-phase passthrough with no visual effect.
@Composable
internal actual fun rememberHeatmapShare(
    chooserTitle: String,
    captureLayer: GraphicsLayer?,
): HeatmapShare? = null
