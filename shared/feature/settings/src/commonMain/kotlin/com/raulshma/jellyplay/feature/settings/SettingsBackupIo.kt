package com.raulshma.jellyplay.feature.settings

/**
 * Platform IO seam for [SettingsViewModel]'s backup/restore + cache-size
 * plumbing. Android bridges the SAF contentResolver (create-document output
 * sink, open-document input source) and walks the internal/external cache
 * dirs; desktop maps the picker's `file:` URIs to plain JDK file IO; web
 * degrades honestly (no filesystem — the null [BackupFilePicker] actual keeps
 * the export/import rows hidden, so the payload calls are unreachable there).
 *
 * web breadth: the seam narrowed from raw `java.io.InputStream` /
 * `OutputStream` handles to a TEXT-LEVEL payload contract — the backup payload
 * is one JSON document, so the former `stream.writer().use { it.write(json) }`
 * and `stream.reader().readText()` bodies moved INSIDE the platform actuals
 * (byte-identical semantics: same stream openers, same UTF-8 writer/reader,
 * same failure mapping through the ViewModels' runCatching). That keeps
 * `java.io` out of commonMain so the wasmJs target can compile the module.
 *
 * [uri] handles are opaque strings (Android SAF / desktop file uris
 * serialised by the picker seam).
 */
interface SettingsBackupIo {

    /**
     * Writes [payload] (the exported JSON document) to the create-document
     * SAF target identified by [uri], truncating any existing content. False
     * when the target cannot be opened (the caller surfaces its own
     * "Cannot open output stream" failure); a write failure propagates.
     */
    suspend fun writeExportPayload(uri: String, payload: String): Boolean

    /**
     * Reads the whole backup document from the open-document source
     * identified by [uri]. Null when the source cannot be opened (the caller
     * surfaces its own "Cannot open backup file" failure).
     */
    suspend fun readImportPayload(uri: String): String?

    /**
     * Combined byte size of the internal + external cache directories
     * (0 on desktop — no cache walk until the desktop storage layout lands).
     */
    suspend fun estimateCacheSizeBytes(): Long
}
