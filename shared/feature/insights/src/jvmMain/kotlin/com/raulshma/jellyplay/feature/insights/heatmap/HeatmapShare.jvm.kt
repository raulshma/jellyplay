package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.layer.GraphicsLayer
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop actual of the [HeatmapShare] seam (wave 20C): the F-23 GraphicsLayer
 * capture is platform-common — the grid's recorded subtree snapshots through
 * the same `toImageBitmap()` the Android actual uses — so the desktop body
 * only swaps the handoff: desktop has no ACTION_SEND chooser, so the PNG lands
 * under `java.io.tmpdir` and is opened in the system image viewer via AWT
 * [Desktop.open] (StatisticsExport desktop precedent; runCatching-guarded so
 * a headless/minimal host degrades to "file written, nothing opened"). A
 * capture or write failure is swallowed exactly like the Android body — the
 * share silently does nothing, never crashes.
 *
 * [chooserTitle] is unused here: there is no chooser to title on desktop.
 */
internal class DesktopHeatmapShare(
    private val captureLayer: GraphicsLayer,
) : HeatmapShare {

    override suspend fun shareHeatmapImage() {
        // Snapshot on the calling (UI) thread — the layer is owned by the
        // composition — then hand the immutable pixels to IO for encode+write,
        // mirroring the Android split (capture + file IO inside IO context).
        val bitmap = runCatching { captureLayer.toImageBitmap() }.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writeHeatmapPng(bitmap.toAwtImage())
                openInSystemViewer(file)
            }
        }
    }
}

/**
 * Encodes the captured heatmap as PNG under [directory] and returns the file.
 * Extracted (and directory-injectable) so jvmTest can verify the bytes land
 * on disk headlessly. The timestamped name keeps consecutive shares from
 * colliding and stops an already-open viewer from serving a stale cache.
 */
internal fun writeHeatmapPng(
    image: BufferedImage,
    directory: File = File(System.getProperty("java.io.tmpdir")),
): File {
    val file = File(directory, "watch_progress_heatmap_${System.currentTimeMillis()}.png")
    ImageIO.write(image, "png", file)
    return file
}

/** Opens [file] with the system's default viewer; a no-op on hosts without AWT desktop support. */
internal fun openInSystemViewer(file: File) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        }
    }
}

@Composable
internal actual fun rememberHeatmapShare(
    chooserTitle: String,
    captureLayer: GraphicsLayer?,
): HeatmapShare? {
    // Same gating as Android: no layer (grid never drew) means no share
    // handle, so the screen keeps the share IconButton hidden until the
    // heatmap has recorded at least one frame.
    if (captureLayer == null) return null
    return remember(captureLayer) { DesktopHeatmapShare(captureLayer) }
}
