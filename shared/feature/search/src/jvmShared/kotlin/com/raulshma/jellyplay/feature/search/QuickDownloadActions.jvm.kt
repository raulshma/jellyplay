package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.StateFlow

/**
 * The JVM adapter over core:data's `MediaDownloadActions` single — the
 * quick-action behavior (messenger-routed outcome toasts, the shared
 * Eagerly-started downloadedIds flow, series-vs-item delete routing) is
 * consumed verbatim; the adapter only bridges the wasm-safe seam types.
 */
internal class JvmQuickDownloadActions(
    private val actions: MediaDownloadActions,
) : QuickDownloadActions {
    override val isSupported: Boolean = true
    override val downloadedIds: StateFlow<Set<String>> = actions.downloadedIds
    override suspend fun download(item: MediaItem): DownloadRequestResult = actions.download(item)
    override suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) =
        actions.downloadAndReport(item, onOpenDetail)
    override fun removeDownload(item: MediaItem) = actions.removeDownload(item)
}
