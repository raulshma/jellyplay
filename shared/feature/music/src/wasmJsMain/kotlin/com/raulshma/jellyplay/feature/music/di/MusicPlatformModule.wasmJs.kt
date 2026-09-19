package com.raulshma.jellyplay.feature.music.di

import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.WasmMusicQueuePlayer
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMusicModule(): Module = module {
    single<MusicQueuePlayer> { WasmMusicQueuePlayer }
    // The former MusicTrackDownloads no-op binding moved out with the
    // download-actions seam consolidation: core:data binds its own honest
    // web actuals (WasmTrackDownloadStatusWindow / WasmActiveDownloadCount)
    // in dataWasmModule.
}
