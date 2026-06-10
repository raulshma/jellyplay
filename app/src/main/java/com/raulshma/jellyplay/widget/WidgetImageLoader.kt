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
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun loadPoster(context: Context, url: String?, cornerRadiusDp: Float = 10f): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(WIDGET_IMAGE_TARGET, WIDGET_IMAGE_TARGET)
                    .allowHardware(false)
                    .build()
                val image = context.imageLoader.execute(request).image
                image?.toBitmap()?.let { applyRoundedCorners(context, it, cornerRadiusDp) }
            }.getOrNull()
        }
    }

    private fun applyRoundedCorners(context: Context, bitmap: Bitmap, cornerRadiusDp: Float = 10f): Bitmap {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = cornerRadiusDp * density
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
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
        return output
    }
}
