package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The download-intake / subtitle-storage / shortcuts family of the
 * androidCoreDataModule split (see [androidCoreDataModule] for the
 * construction-owner rules). Binding bodies moved verbatim from the
 * pre-split single-module layout.
 */
internal fun androidIntakeStorageModule(context: Context): Module = module {
    // ── Download intake / subtitle storage / shortcuts ──────────────────
    single {
        DownloadIntakeImpl(
            context = context,
            delegate = get(),
            downloadRepository = get(),
            mediaRepository = get(),
            downloadsStore = get(),
        )
    }
    single<DownloadIntake> { get<DownloadIntakeImpl>() }

    single {
        StreamingSubtitleStoreImpl(
            baseDir = context.filesDir,
            json = get(),
        )
    }
    single<StreamingSubtitleStore> { get<StreamingSubtitleStoreImpl>() }

    // The old ctor took dagger.Lazy<AudioPlaybackManager>; the kotlin Lazy
    // wrapper preserves the deferred construction (the media3 graph is only
    // touched on the first shortcut observation).
    single {
        AppShortcutManager(
            context = context,
            audioPlaybackManagerLazy = lazy { get<AudioPlaybackManager>() },
        )
    }
}
