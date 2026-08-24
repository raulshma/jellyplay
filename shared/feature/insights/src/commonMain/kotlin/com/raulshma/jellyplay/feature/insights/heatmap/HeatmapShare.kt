package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Composable

/**
 * Platform seam for the heatmap's share action (insights conveyor; admin
 * StatisticsExport + editor EditorFilePicker shapes). Android keeps the
 * pre-migration capture + share bodies verbatim in the androidMain actual:
 * the whole-window `View.drawToBitmap` snapshot plus the cacheDir PNG /
 * FileProvider / ACTION_SEND chooser handoff. Desktop has no share sheet and
 * no capture API: the actual returns null and the screen hides the share
 * IconButton (editor picker gating pattern), so the one-shot share effect can
 * never fire there.
 *
 * [rememberHeatmapShare] takes the chooser title pre-resolved in composition
 * — the legacy body read it from `Context.getString` at share time; resolving
 * it at composition keeps the actual Context-free for the title and preserves
 * the locale of the composition that launched the share.
 */
internal interface HeatmapShare {

    /**
     * Captures the heatmap screen and hands the PNG to the platform share
     * sheet (Android: IO-dispatched, runCatching-guarded capture of the
     * window + FileProvider ACTION_SEND chooser).
     */
    suspend fun shareHeatmapImage()
}

/** Composition-scoped [HeatmapShare] pick for the current platform. */
@Composable
internal expect fun rememberHeatmapShare(chooserTitle: String): HeatmapShare?
