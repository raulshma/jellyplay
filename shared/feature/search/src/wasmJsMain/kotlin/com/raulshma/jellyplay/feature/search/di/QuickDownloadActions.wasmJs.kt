package com.raulshma.jellyplay.feature.search.di

import com.raulshma.jellyplay.feature.search.QuickDownloadActions
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The wasmJs actual of the quick-download seam: an honest no-op. The browser
 * has no local download pipeline, so [isSupported] is false — web hosts should
 * hide/disable the download CTAs — while `downloadedIds` stays empty,
 * download resolves to [DownloadRequestResult.Failed] and remove is inert.
 * See [QuickDownloadActions]' KDoc.
 */
internal object WasmQuickDownloadActions : QuickDownloadActions {
    override val isSupported: Boolean = false
    override val downloadedIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun download(item: MediaItem): DownloadRequestResult =
        DownloadRequestResult.Failed("Downloads are unavailable on the web")
    override suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) = Unit
    override fun removeDownload(item: MediaItem) = Unit
}

internal actual fun platformSearchModule(): Module = module {
    single<QuickDownloadActions> { WasmQuickDownloadActions }
}
