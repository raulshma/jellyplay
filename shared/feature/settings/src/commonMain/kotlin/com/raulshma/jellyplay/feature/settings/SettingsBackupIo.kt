package com.raulshma.jellyplay.feature.settings

import java.io.InputStream
import java.io.OutputStream

/**
 * Platform IO seam for [SettingsViewModel]'s backup/restore + cache-size
 * plumbing (V3 settings conveyor). Android bridges the SAF contentResolver
 * streams (create-document output sink, open-document input source) and walks
 * the internal/external cache dirs; desktop v1 has no file pickers, so the
 * stream actuals return null — the VM then runs the identical
 * "Cannot open … stream" [java.io.IOException] error path it used for a
 * missing SAF stream — and the cache-size actual reports 0.
 *
 * Streams are returned open; callers own closing them (`.use`).
 * [uri] handles are opaque strings (Android SAF uris serialised by the screen).
 */
interface SettingsBackupIo {
    /** Output stream for a create-document SAF target, or null if it can't be opened. */
    suspend fun openExportSink(uri: String): OutputStream?

    /** Input stream for an open-document SAF source, or null if it can't be opened. */
    suspend fun openImportSource(uri: String): InputStream?

    /**
     * Combined byte size of the internal + external cache directories
     * (0 on desktop — no cache walk until the desktop storage layout lands).
     */
    suspend fun estimateCacheSizeBytes(): Long
}
