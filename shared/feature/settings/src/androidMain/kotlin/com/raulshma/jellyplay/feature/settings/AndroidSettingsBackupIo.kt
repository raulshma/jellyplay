package com.raulshma.jellyplay.feature.settings

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Android actual of the [SettingsBackupIo] seam: SAF contentResolver IO plus
 * the concurrent internal/external cache-dir walk, moved verbatim from the
 * pre-migration SettingsViewModel bodies. The web seam narrowing (raw
 * streams → text-level payload) moved the former caller-side
 * `stream.writer().use { it.write(json) }` / `stream.reader().readText()`
 * bodies here — same stream openers, same UTF-8 writer/reader chain, so the
 * bytes on disk and the failure mapping are unchanged.
 */
internal class AndroidSettingsBackupIo(
    private val context: Context,
) : SettingsBackupIo {

    override suspend fun writeExportPayload(uri: String, payload: String): Boolean =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(Uri.parse(uri))?.use { stream ->
                stream.writer().use { it.write(payload) }
            } != null
        }

    override suspend fun readImportPayload(uri: String): String? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream: InputStream ->
                stream.reader().readText()
            }
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
