package com.raulshma.jellyplay.feature.player.audio.di

import com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager
import com.raulshma.jellyplay.feature.player.audio.AudioTrackDownloads
import com.raulshma.jellyplay.feature.player.audio.WasmAudioSleepTimerManager
import com.raulshma.jellyplay.feature.player.audio.WasmAudioTrackDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformPlayerAudioModule(): Module = module {
    single<AudioSleepTimerManager> { WasmAudioSleepTimerManager() }
    single<AudioTrackDownloads> { WasmAudioTrackDownloads }
}
