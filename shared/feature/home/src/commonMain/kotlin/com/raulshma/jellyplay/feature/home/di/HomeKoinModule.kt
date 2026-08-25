package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.feature.home.HomeViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the home feature (V3 conveyor transform — one
 * framework per type: the @HiltViewModel/@Inject annotations were stripped at
 * the move, Koin is the single constructor owner).
 *
 * Dep resolution:
 *  - 30 of the 34 ctor deps are Koin-native on BOTH platforms — the
 *    dataJvmModule/datastoreCommonModule singles (incl. the cluster-flipped
 *    MediaRepository/UserDataMutator/MediaSearchEngine) plus the platform
 *    data modules (ImageUrlProvider, OfflineModeManager) and the settings
 *    feature's SettingsSearchProvider single.
 *  - 4 deps remain Hilt-owned in the legacy tree (PlaybackSyncScheduler,
 *    TvWatchNextScheduler — WorkManager workers; ContinueWatchingBroadcaster,
 *    LibrarySyncHook — app widget broadcast receivers) and reach Koin on
 *    Android through the app composition root's Hilt interop module. They
 *    have no desktop defs yet, so resolving this VM on desktop throws — the
 *    documented latent state shared with the other pre-cutover feature
 *    modules (no desktop nav wiring constructs it today).
 */
val homeModule: Module = module {
    viewModel {
        HomeViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            mediaSearchEngine = get(),
            offlineFirstItemResolver = get(),
            orderHomeSections = get(),
            imageUrlProvider = get(),
            photoFolderPrefetcher = get(),
            downloadRepository = get(),
            offlineRepository = get(),
            playbackOutboxRepository = get(),
            playbackSyncScheduler = get(),
            offlineModeManager = get(),
            newsletterTriggerManager = get(),
            homeDiscoveryStore = get(),
            appearanceStore = get(),
            experimentalStore = get(),
            playbackStore = get(),
            preferencesEditor = get(),
            widgetDataStore = get(),
            seerrRepository = get(),
            seerrRequestDelegate = get(),
            seerrPreferencesStore = get(),
            authRepository = get(),
            homeSession = get(),
            arrRepository = get(),
            tvWatchNextScheduler = get(),
            continueWatchingBroadcaster = get(),
            librarySyncHook = get(),
            timeSource = get(),
            settingsSearchProvider = get(),
        )
    }
}
