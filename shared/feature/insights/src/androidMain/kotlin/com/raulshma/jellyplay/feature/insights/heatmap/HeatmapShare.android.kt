package com.raulshma.jellyplay.feature.insights.heatmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android actual of the [HeatmapShare] seam — the pre-migration
 * WatchProgressHeatmapScreen capture + share bodies, verbatim, just funnelled
 * through one object reached from the composable's LocalContext/LocalView.
 * The chooser title arrives pre-resolved from composition (the legacy body
 * called Context.getString at share time).
 */
internal class AndroidHeatmapShare(
    private val context: Context,
    private val view: View,
    private val chooserTitle: String,
) : HeatmapShare {

    override suspend fun shareHeatmapImage() {
        withContext(Dispatchers.IO) {
            runCatching {
                // TODO(F-23): capture only the HeatmapGrid subtree once
                // rememberGraphicsLayer is available in the Compose BOM.
                val bitmap = view.drawToBitmap()
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
internal actual fun rememberHeatmapShare(chooserTitle: String): HeatmapShare? {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, chooserTitle) {
        AndroidHeatmapShare(context, view, chooserTitle)
    }
}
