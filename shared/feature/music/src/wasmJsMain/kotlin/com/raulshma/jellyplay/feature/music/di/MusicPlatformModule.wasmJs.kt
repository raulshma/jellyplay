package com.raulshma.jellyplay.feature.music.di

import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.MusicTrackDownloads
import com.raulshma.jellyplay.feature.music.WasmMusicQueuePlayer
import com.raulshma.jellyplay.feature.music.WasmMusicTrackDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMusicModule(): Module = module {
    single<MusicQueuePlayer> { WasmMusicQueuePlayer }
    single<MusicTrackDownloads> { WasmMusicTrackDownloads }
}
