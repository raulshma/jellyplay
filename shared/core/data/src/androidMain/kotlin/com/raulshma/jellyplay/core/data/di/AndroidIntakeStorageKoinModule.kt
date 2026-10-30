package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.playback.NowPlayingSurface
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import org.koin.core.module.Module
import org.koin.dsl.module
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers

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
            policyStore = get(),
            detailFeed = get(),
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
    // touched on the first shortcut observation). The type is the audio
    // family's NowPlayingSurface face (the Audio shell seam's role-split) —
    // the shortcut observation reads exactly its four flows; the Koin alias
    // binds the same manager single.
    single {
        AppShortcutManager(
            context = context,
            nowPlaying = lazy { get<NowPlayingSurface>() },
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
