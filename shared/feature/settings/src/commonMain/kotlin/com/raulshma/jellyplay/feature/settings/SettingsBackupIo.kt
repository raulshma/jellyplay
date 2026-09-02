package com.raulshma.jellyplay.feature.settings

import java.io.InputStream
import java.io.OutputStream

/**
 * Platform IO seam for [SettingsViewModel]'s backup/restore + cache-size
 * plumbing (V3 settings conveyor). Android bridges the SAF contentResolver
 * streams (create-document output sink, open-document input source) and walks
 * the internal/external cache dirs; desktop (wave 20C) maps the picker's
 * `file:` URIs to plain JDK file streams — the cache-size actual still
 * reports 0 (no desktop storage layout yet).
 *
 * Streams are returned open; callers own closing them (`.use`).
 * [uri] handles are opaque strings (Android SAF / desktop file uris
 * serialised by the picker seam).
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
