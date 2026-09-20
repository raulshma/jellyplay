package com.raulshma.jellyplay.feature.player.audio.di

import org.koin.core.module.Module
import org.koin.dsl.module

// Empty since the download-actions seam consolidation: the sleep-timer
// binding stays in dataJvmModule (the interface is aliased onto the
// SleepTimerManager single there), and the VM's download window —
// TrackDownloadStatusWindow, which replaced the former feature-local
// AudioTrackDownloads seam — is core:data's own binding in dataJvmModule
// (since the promoted-interface pass, over DownloadRepositoryImpl itself,
// which implements the window directly). This fragment stays so commonMain's
// includes(platformPlayerAudioModule()) keeps its jvmShared counterpart.
internal actual fun platformPlayerAudioModule(): Module = module {
}
