package com.raulshma.jellyplay.feature.library

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Android [PhotoExport]. The Coil private-decode + MediaStore/FileProvider
 * bodies moved here verbatim from the legacy PhotoViewerViewModel at the V3
 * library conveyor move — only the share path starts from the application
 * context now (the screen's activity context is not reachable from Koin), so
 * the chooser additionally gets FLAG_ACTIVITY_NEW_TASK.
 */
internal class AndroidPhotoExport(
    private val appContext: Context,
) : PhotoExport {

    override val isSupported: Boolean = true

    override suspend fun saveToGallery(imageUrl: String, displayName: String) {
        val imageLoader = coil3.SingletonImageLoader.get(appContext)
        val request = ImageRequest.Builder(appContext)
            .data(imageUrl)
            .allowHardware(false)
            // Decode a private Bitmap (not the shared cache instance)
            // before compressing it to the gallery. Without this,
            // toBitmap() returns Coil's shared Bitmap, which the
            // BitmapPool can recycle mid-compress if the photo grid
            // evicts the ORIGINAL cache entry during the IO write.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()

        val result = imageLoader.execute(request)
        val bitmap = result.image?.toBitmap()

        if (bitmap != null) {
            saveBitmapToMediaStore(appContext, bitmap, displayName)
        } else {
            error("Failed to download image")
        }
    }

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, displayName: String) {
        val filename = "${displayName}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JellyPlay")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver: ContentResolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    override suspend fun sharePhoto(imageUrl: String, displayName: String) {
        val imageLoader = coil3.SingletonImageLoader.get(appContext)
        val request = ImageRequest.Builder(appContext)
            .data(imageUrl)
            .allowHardware(false)
            // Private decode — see saveToGallery for rationale.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()

        val result = imageLoader.execute(request)
        val bitmap = result.image?.toBitmap()

        if (bitmap == null) {
            error("Failed to download image")
        }

        val cachePath = File(appContext.cacheDir, "shared_images")
        cachePath.mkdirs()
        val file = File(cachePath, "${displayName.replace(" ", "_")}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Launched from the application context (Koin-held), not the
            // activity the legacy code received — required by Android there.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(Intent.createChooser(shareIntent, "Share photo"))
    }
}
