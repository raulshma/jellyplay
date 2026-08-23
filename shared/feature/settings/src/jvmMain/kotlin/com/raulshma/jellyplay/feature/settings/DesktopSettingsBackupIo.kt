package com.raulshma.jellyplay.feature.settings

import java.io.InputStream
import java.io.OutputStream

/**
 * Desktop actual of the [SettingsBackupIo] seam: desktop v1 has no file
 * pickers, so both stream openers return null (the VM then surfaces the same
 * "Cannot open … stream" failure it shows for a missing SAF target) and the
 * cache walk reports 0 bytes.
 */
internal class DesktopSettingsBackupIo : SettingsBackupIo {

    override suspend fun openExportSink(uri: String): OutputStream? = null

    override suspend fun openImportSource(uri: String): InputStream? = null

    override suspend fun estimateCacheSizeBytes(): Long = 0L
}
