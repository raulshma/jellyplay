package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.core.view.drawToBitmap
import java.io.OutputStream

/**
 * Captures the current video frame to an image file in `Pictures/JellyPlay/`.
 *
 * The capture path is deliberately surface-view centric and **backend agnostic**:
 * all three engines (ExoPlayer, mpv, libVLC) render to a real `SurfaceView`, and
 * [PixelCopy] is the only API that can read GPU-composited SurfaceView content
 * synchronously into a [Bitmap]. (`View.drawToBitmap` returns black for a
 * SurfaceView because its pixels live in a separate surface layer.)
 *
 * [PixelCopy.request] requires API 24+ (JellyPlay's minSdk is 28), so there is
 * no version guard. We copy from the host [Window] clipped to the surface view's
 * global visible rect, so letterboxing/zoom is preserved exactly as rendered.
 */
object ScreenshotSaver {

    private const val TAG = "ScreenshotSaver"
    private const val FOLDER = "JellyPlay"

    sealed interface Result {
        data class Saved(val uri: Uri, val width: Int, val height: Int) : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Captures [surfaceView] into a bitmap via [PixelCopy] and persists it.
     * Must be called when the view is attached to a window and has non-zero size.
     * Runs the PixelCopy on the view's post callback, then performs file I/O on a
     * background thread and invokes [onComplete] on the calling (main) thread.
     */
    fun capture(
        surfaceView: View,
        titleHint: String,
        onComplete: (Result) -> Unit,
    ) {
        val window = surfaceView.findWindow() ?: run {
            onComplete(Result.Failed("No window attached"))
            return
        }

        // The surface view's visible rect in window coordinates.
        val rect = Rect().also { surfaceView.getGlobalVisibleRect(it) }
        if (rect.width() <= 0 || rect.height() <= 0) {
            onComplete(Result.Failed("Surface has no size"))
            return
        }

        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(
                window,
                rect,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        persistAsync(surfaceView.context, bitmap, titleHint, onComplete)
                    } else {
                        // Fallback: drawToBitmap won't capture SurfaceView pixels, but it is
                        // better than nothing on the rare device where PixelCopy is denied.
                        val drawn = tryBitmapFallback(surfaceView, rect.width(), rect.height())
                        if (drawn != null) {
                            persistAsync(surfaceView.context, drawn, titleHint, onComplete)
                        } else {
                            onComplete(Result.Failed("PixelCopy failed: $copyResult"))
                        }
                    }
                },
                surfaceView.handler ?: android.os.Handler(android.os.Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            Log.w(TAG, "PixelCopy request threw", e)
            onComplete(Result.Failed(e.message ?: "PixelCopy exception"))
        }
    }

    /** Returns the [Window] the view is attached to, if any. */
    private fun View.findWindow(): Window? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx.window
            ctx = ctx.baseContext
        }
        return null
    }

    private fun tryBitmapFallback(view: View, w: Int, h: Int): Bitmap? = try {
        view.drawToBitmap().let {
            // drawToBitmap of a SurfaceView is usually black; only return if non-trivial.
            if (it.width > 0 && it.height > 0) Bitmap.createScaledBitmap(it, w, h, true) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun persistAsync(
        context: Context,
        bitmap: Bitmap,
        titleHint: String,
        onComplete: (Result) -> Unit,
    ) {
        Thread {
            try {
                val uri = saveToMediaStore(context, bitmap, titleHint)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete(Result.Saved(uri, bitmap.width, bitmap.height))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save screenshot", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete(Result.Failed(e.message ?: "Save failed"))
                }
            }
        }.also { it.isDaemon = true; it.name = "screenshot-saver" }.start()
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, titleHint: String): Uri {
        val timestamp = System.currentTimeMillis()
        val safeTitle = sanitizeTitle(titleHint)
        val filename = "JellyPlay_${safeTitle}_$timestamp.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert returned null")

        resolver.openOutputStream(uri)?.use { out: OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: throw IllegalStateException("Could not open output stream for $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    /** Builds a share intent for a saved screenshot URI. */
    fun buildShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Sanitizes a user supplied title into a safe, filesystem friendly name. */
    internal fun sanitizeTitle(titleHint: String): String =
        titleHint.ifBlank { "frame" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "frame" }
}
