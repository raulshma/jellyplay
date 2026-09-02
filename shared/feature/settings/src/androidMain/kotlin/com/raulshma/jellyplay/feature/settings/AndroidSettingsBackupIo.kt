package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Android actual of the [SettingsBackupIo] seam: SAF contentResolver streams
 * plus the concurrent internal/external cache-dir walk, moved verbatim from
 * the pre-migration SettingsViewModel bodies.
 */
internal class AndroidSettingsBackupIo(
    private val context: Context,
) : SettingsBackupIo {

    override suspend fun openExportSink(uri: String): OutputStream? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(Uri.parse(uri))
        }

    override suspend fun openImportSource(uri: String): InputStream? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(uri))
        }

    override suspend fun estimateCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        // Two independent recursive FS walks — run the walks concurrently
        // rather than one after another. Each walk can take seconds on large
        // directories.
        coroutineScope {
            val cacheAsync = async { directorySizeBytes(context.cacheDir) }
            val extAsync = async { context.externalCacheDir?.let { directorySizeBytes(it) } ?: 0L }
            cacheAsync.await() + extAsync.await()
        }
    }
}
