package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * Platform seam for the heatmap's share action (insights conveyor; admin
 * StatisticsExport + editor EditorFilePicker shapes). Android keeps the
 * share body in the androidMain actual: the HeatmapGrid subtree is recorded
 * into [captureLayer] by the grid's draw pass (the F-23 GraphicsLayer capture
 * — a subtree snapshot, not the whole-window `View.drawToBitmap` the legacy
 * body used) and handed to the cacheDir PNG / FileProvider / ACTION_SEND
 * chooser handoff. Desktop (wave 20C) snapshots the same recorded subtree
 * (`toImageBitmap` is platform-common) and hands the PNG to the system image
 * viewer — a tmpdir file + AWT Desktop.open, the StatisticsExport desktop
 * precedent, since there is no ACTION_SEND equivalent.
 *
 * [rememberHeatmapShare] takes the chooser title pre-resolved in composition
 * — the legacy body read it from `Context.getString` at share time; resolving
 * it at composition keeps the actual Context-free for the title and preserves
 * the locale of the composition that launched the share (desktop has no
 * chooser to title and ignores it). [captureLayer] is the grid-owned layer
 * the screen threads down; a null layer keeps the handle null on every
 * platform, hiding the share IconButton until the grid has drawn.
 */
internal interface HeatmapShare {

    /**
     * Captures the recorded heatmap grid and hands the PNG to the platform
     * share surface (Android: IO-dispatched, runCatching-guarded capture +
     * FileProvider ACTION_SEND chooser; desktop: tmpdir PNG opened in the
     * system viewer; a capture failure is swallowed and the share silently
     * does nothing, as before).
     */
    suspend fun shareHeatmapImage()
}

/** Composition-scoped [HeatmapShare] pick for the current platform. */
@Composable
internal expect fun rememberHeatmapShare(
    chooserTitle: String,
    captureLayer: GraphicsLayer?,
): HeatmapShare?
