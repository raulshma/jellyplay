package com.raulshma.jellyplay.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Centralised Coil → Bitmap pipeline used by every widget factory.
 *
 * The pipeline is intentionally small:
 *   1. Decode at most [WIDGET_IMAGE_TARGET] (px on the long edge) so the
 *      widget never holds > 250 KB per cell — well inside Android's
 *      per-process bitmap budget.
 *   2. Force `allowHardware(false)` because `RemoteViews.setImageViewBitmap`
 *      does not accept hardware bitmaps on older Android versions.
 *   3. Apply a rounded-corner mask so the cell matches the dark Material
 *      surfaces used elsewhere in the widget.
 */
object WidgetImageLoader {

    private const val WIDGET_IMAGE_TARGET = 480

    // Cap any single poster load so a slow Jellyfin server can't pin the
    // widget's binder thread indefinitely. Returning `null` makes the factory
    // fall back to its placeholder drawable — preferable to a frozen cell.
    private const val WIDGET_LOAD_TIMEOUT_MS = 2_000L

    suspend fun loadPoster(context: Context, url: String?, cornerRadiusDp: Float = 10f): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withTimeoutOrNull(WIDGET_LOAD_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(WIDGET_IMAGE_TARGET, WIDGET_IMAGE_TARGET)
                        .allowHardware(false)
                        // The widget needs its own private bitmap instance: the
                        // default `toBitmap()` unwrap returns the *shared* Bitmap
                        // held in Coil's memory cache (and handed out to Compose
                        // `AsyncImage` consumers). Recycling that shared bitmap
                        // in [applyRoundedCorners] then crashed any Compose
                        // painter still drawing the same URL with
                        // "Canvas: trying to use a recycled bitmap". Disabling
                        // the memory cache for the widget request guarantees the
                        // returned bitmap is a fresh decode owned only by us, so
                        // recycling it after rounding is safe.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                    val image = context.imageLoader.execute(request).image
                    image?.toBitmap()?.let { applyRoundedCorners(context, it, cornerRadiusDp) }
                }.getOrNull()
            }
        }
    }

    /**
     * Pre-fetches every poster concurrently so `RemoteViewsFactory.getViewAt`
     * can read from the resulting cache instead of doing per-cell network I/O
     * on the binder thread. A single slow URL won't blow the budget — each
     * individual load is bounded by [WIDGET_LOAD_TIMEOUT_MS] via [loadPoster].
     */
    suspend fun preloadPosters(
        context: Context,
        urls: Collection<String>,
        cornerRadiusDp: Float = 10f,
    ): Map<String, Bitmap?> = coroutineScope {
        urls.filter { it.isNotBlank() }
            .distinct()
            .map { url -> async { url to loadPoster(context, url, cornerRadiusDp) } }
            .awaitAll()
            .toMap()
    }

    private fun applyRoundedCorners(context: Context, bitmap: Bitmap, cornerRadiusDp: Float = 10f): Bitmap {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = cornerRadiusDp * density
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            val paint = Paint().apply {
                isAntiAlias = true
                color = 0xff424242.toInt()
            }
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
        } finally {
            // The input bitmap is no longer referenced once it has been drawn
            // into `output`; recycle it promptly to avoid N pairs of bitmaps
            // briefly coexisting during concurrent `preloadPosters` fetches.
            bitmap.recycle()
        }
        return output
    }
}
