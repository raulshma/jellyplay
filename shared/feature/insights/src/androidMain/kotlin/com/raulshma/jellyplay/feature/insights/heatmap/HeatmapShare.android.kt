package com.raulshma.jellyplay.feature.insights.heatmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android actual of the [HeatmapShare] seam. The legacy body captured the
 * whole window via `View.drawToBitmap`; the F-23 revisit replaced that with a
 * GraphicsLayer subtree capture — the grid records itself into
 * [captureLayer] on every draw pass (see [WatchProgressHeatmapScreen]'s
 * HeatmapGrid), and `toImageBitmap()` snapshots exactly that subtree, so the
 * shared PNG carries the heatmap alone (no app bar, no pull-to-refresh
 * chrome). The chooser title arrives pre-resolved from composition (the
 * legacy body called Context.getString at share time).
 */
internal class AndroidHeatmapShare(
    private val context: Context,
    private val captureLayer: GraphicsLayer,
    private val chooserTitle: String,
) : HeatmapShare {

    override suspend fun shareHeatmapImage() {
        withContext(Dispatchers.IO) {
            runCatching {
                // Same runCatching-silent-swallow as the legacy body: a failed
                // capture (or a layer never recorded — the share button only
                // exists once the grid has drawn) does nothing, never crashes.
                val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                shareHeatmapImage(context, bitmap)
            }
        }
    }

    private fun shareHeatmapImage(context: Context, bitmap: Bitmap) {
        val file = File(context.cacheDir, "watch_progress_heatmap.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}

@Composable
internal actual fun rememberHeatmapShare(
    chooserTitle: String,
    captureLayer: GraphicsLayer?,
): HeatmapShare? {
    val context = LocalContext.current
    if (captureLayer == null) return null
    return remember(context, captureLayer, chooserTitle) {
        AndroidHeatmapShare(context, captureLayer, chooserTitle)
    }
}
