package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager
import com.raulshma.jellyplay.feature.player.audio.WasmAudioSleepTimerManager
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformPlayerAudioModule(): Module = module {
    single<AudioSleepTimerManager> { WasmAudioSleepTimerManager() }
    // The VM's download window (TrackDownloadStatusWindow) needs no binding
    // here — core:data's dataWasmModule binds its honest web no-op actual
    // (WasmTrackDownloadStatusWindow) since the download-actions seam
    // consolidation replaced the former feature-local AudioTrackDownloads
    // seam and its fragment binding.
}
