package com.raulshma.jellyplay.core.data.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The wasmJs actual of the active-download count: an honest zero. The browser
 * has no local download pipeline, so no transfer is ever in flight — the
 * music-home badge never renders a fabricated count. Same stub string the
 * former feature-local WasmMusicTrackDownloads carried; bound in
 * dataWasmModule.
 */
internal object WasmActiveDownloadCount : ActiveDownloadCount {
    override fun activeDownloadCount(): Flow<Int> = flowOf(0)
}
