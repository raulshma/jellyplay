package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Web seam over core:data's jvmShared `MediaDownloadActions`
 * (the unified quick-action download/remove delegate every host surface
 * shares) — its constructor closure reaches the JVM download pipeline, so
 * commonMain cannot name the class. Same treatment as the editor's
 * EditorSubtitleStore seam: the interface carries exactly the host-facing
 * surface, the jvmShared actual delegates to the process-wide
 * `MediaDownloadActions` single (same messenger routing, same shared
 * downloadedIds flow — android/desktop behavior unchanged), and the wasmJs
 * actual is an honest no-op.
 *
 * Web behavior: the browser has no local download pipeline, so the wasm
 * actual reports [isSupported] = false — web hosts should hide/disable the
 * download CTAs — while `downloadedIds` stays empty and download/remove calls
 * are inert (download resolves to [DownloadRequestResult.Failed]).
 */
internal interface QuickDownloadActions {

    /** Whether this platform has a download pipeline; gates download CTAs. */
    val isSupported: Boolean

    /**
     * Ids whose quick actions flip to "Remove download" — completed downloads
     * ∪ series ids (the union contract of MediaDownloadActions.downloadedIds).
     */
    val downloadedIds: StateFlow<Set<String>>

    /** Start a download for [item] (see [MediaDownloadActions.download]). */
    suspend fun download(item: MediaItem): DownloadRequestResult

    /**
     * [download] plus the shared outcome handling: Started/Failed surface via
     * the platform messenger, both navigation outcomes route to [onOpenDetail].
     */
    suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit)

    /** Remove [item]'s local download (artifacts + offline rows). Fire-and-forget. */
    fun removeDownload(item: MediaItem)
}
