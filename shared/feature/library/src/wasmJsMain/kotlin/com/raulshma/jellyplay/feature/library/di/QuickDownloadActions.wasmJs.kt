package com.raulshma.jellyplay.feature.library.di

import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.feature.library.QuickDownloadActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The wasmJs actual of the quick-download seam: an honest no-op. The browser
 * has no local download pipeline, so [QuickDownloadActions.isSupported] is
 * false — web hosts should hide/disable the download CTAs — while
 * downloadedIds stays empty and download/remove calls are inert (download
 * resolves to [DownloadRequestResult.Failed]).
 */
internal object WasmQuickDownloadActions : QuickDownloadActions {
    override val isSupported: Boolean = false
    override val downloadedIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun download(item: MediaItem): DownloadRequestResult =
        DownloadRequestResult.Failed("Downloads are unavailable on the web")
    override suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) = Unit
    override fun removeDownload(item: MediaItem) = Unit
}

internal actual fun platformLibraryModule(): Module = module {
    single<QuickDownloadActions> { WasmQuickDownloadActions }
}
