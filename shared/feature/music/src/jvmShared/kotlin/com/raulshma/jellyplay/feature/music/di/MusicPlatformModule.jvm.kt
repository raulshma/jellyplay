package com.raulshma.jellyplay.feature.music.di

import com.raulshma.jellyplay.feature.music.JvmMusicQueuePlayer
import com.raulshma.jellyplay.feature.music.JvmMusicTrackDownloads
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.MusicTrackDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMusicModule(): Module = module {
    // The VMs resolve both seams via get() — the adapters delegate to the
    // process-wide AudioQueueFacade / DownloadRepository singles.
    single<MusicQueuePlayer> { JvmMusicQueuePlayer(get()) }
    single<MusicTrackDownloads> { JvmMusicTrackDownloads(get()) }
}
