package com.raulshma.jellyplay.feature.music.di

import com.raulshma.jellyplay.feature.music.JvmMusicQueuePlayer
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMusicModule(): Module = module {
    // The VMs resolve the queue-player seam via get() — the adapter delegates
    // to the process-wide AudioQueueFacade single. The former
    // MusicTrackDownloads binding moved out with the download-actions seam
    // consolidation: the music screens' download reads resolve from
    // core:data's own seams (TrackDownloadStatusWindow for the album rows,
    // ActiveDownloadCount for the home badge — both bound in dataJvmModule).
    single<MusicQueuePlayer> { JvmMusicQueuePlayer(get()) }
}
