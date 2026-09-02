package com.raulshma.jellyplay.feature.settings

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop actual of the [SettingsBackupIo] seam (wave 20C): the picker seam
 * now delivers native `file:` URI strings (see [rememberBackupFilePicker]),
 * so the stream openers map the URI back to a [File] and open plain
 * JDK streams on [Dispatchers.IO] — the same IO-dispatch + open-stream
 * contract the Android actual gives its SAF URIs. A bad URI or an unopenable
 * file throws from the stream constructor, which the ViewModel's existing
 * runCatching surfaces as "Export/Import failed: …" exactly like a failing
 * contentResolver stream on Android.
 *
 * The cache estimate (wave 21B) walks the desktop's one persistent cache
 * root — `<configDir>/http-cache`, the OkHttp response cache — mirroring the
 * Android actual's cacheDir walk over the roots the platform actually owns
 * (see DesktopStorageAreas for the full desktop storage layout).
 */
internal class DesktopSettingsBackupIo(
    private val httpCacheRoot: File,
) : SettingsBackupIo {

    override suspend fun openExportSink(uri: String): OutputStream? =
        withContext(Dispatchers.IO) {
            FileOutputStream(backupFileFor(uri))
        }

    override suspend fun openImportSource(uri: String): InputStream? =
        withContext(Dispatchers.IO) {
            FileInputStream(backupFileFor(uri))
        }

    override suspend fun estimateCacheSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            directorySizeBytes(httpCacheRoot)
        }
}

/**
 * Resolves a picker-delivered `file:` URI string to its [File]. Extracted so
 * the URI→File mapping is unit-testable without a dialog. Throws for URIs
 * that are not hierarchical file URIs (the picker never produces those, but
 * the VM's error path handles a thrown resolver the same way Android handles
 * an unopenable SAF stream).
 */
internal fun backupFileFor(uri: String): File = File(java.net.URI(uri))
