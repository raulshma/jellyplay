package com.raulshma.jellyplay.feature.settings

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop actual of the [SettingsBackupIo] seam: the picker seam
 * now delivers native `file:` URI strings (see [rememberBackupFilePicker]),
 * so the payload read/write map the URI back to a [File] and run plain JDK
 * file IO on [Dispatchers.IO] — the same IO-dispatch + open-stream contract
 * the Android actual gives its SAF URIs. A bad URI or an unopenable file
 * throws from the stream constructor, which the ViewModel's existing
 * runCatching surfaces as "Export/Import failed: …" exactly like a failing
 * contentResolver stream on Android. The web seam narrowing (raw streams
 * → text-level payload) moved the former caller-side
 * `stream.writer().use { it.write(json) }` / `stream.reader().readText()`
 * bodies here — same streams, same bytes, same failure mapping.
 *
 * The cache estimate walks the desktop's one persistent cache
 * root — `<configDir>/http-cache`, the OkHttp response cache — mirroring the
 * Android actual's cacheDir walk over the roots the platform actually owns
 * (see DesktopStorageAreas for the full desktop storage layout).
 */
internal class DesktopSettingsBackupIo(
    private val httpCacheRoot: File,
) : SettingsBackupIo {

    override suspend fun writeExportPayload(uri: String, payload: String): Boolean =
        withContext(Dispatchers.IO) {
            FileOutputStream(backupFileFor(uri)).use { stream ->
                stream.writer().use { it.write(payload) }
            }
            true
        }

    override suspend fun readImportPayload(uri: String): String? =
        withContext(Dispatchers.IO) {
            FileInputStream(backupFileFor(uri)).use { stream ->
                stream.reader().readText()
            }
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
