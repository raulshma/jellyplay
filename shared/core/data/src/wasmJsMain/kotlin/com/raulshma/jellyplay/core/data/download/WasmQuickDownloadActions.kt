package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The wasmJs actual of the quick-download seam: an honest no-op. The browser
 * has no local download pipeline, so [QuickDownloadActions.isSupported] is
 * false — web hosts should hide/disable the download CTAs — while
 * downloadedIds stays empty and download/remove calls are inert (download
 * resolves to [DownloadRequestResult.Failed]). Hoisted from the byte-identical
 * twins that shipped in feature:library and feature:search; bound in
 * dataWasmModule (core:data owns the seam on both platforms — see
 * [QuickDownloadActions]' IDIOM RULE note).
 */
internal object WasmQuickDownloadActions : QuickDownloadActions {
    override val isSupported: Boolean = false
    override val downloadedIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun download(item: MediaItem): DownloadRequestResult =
        DownloadRequestResult.Failed("Downloads are unavailable on the web")
    override suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) = Unit
    override fun removeDownload(item: MediaItem) = Unit
}
