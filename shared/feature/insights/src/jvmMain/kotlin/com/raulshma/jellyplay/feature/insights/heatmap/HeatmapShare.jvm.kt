package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Composable

// Desktop v1 has no share sheet and no window-capture API (settings
// BackupFilePicker / editor EditorFilePicker precedent): the actual returns
// null, so the screen's share IconButton stays hidden and the one-shot share
// effect can never fire. Every other heatmap affordance (grid, day detail,
// filters, year navigation) is platform-free and fully usable.
@Composable
internal actual fun rememberHeatmapShare(chooserTitle: String): HeatmapShare? = null
