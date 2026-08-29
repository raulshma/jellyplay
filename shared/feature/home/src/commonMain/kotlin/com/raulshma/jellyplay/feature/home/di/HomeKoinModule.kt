package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.feature.home.HomeViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the home feature (V3 conveyor transform — one
 * framework per type: the Hilt ViewModel annotations were stripped at
 * the move, Koin is the single constructor owner).
 *
 * Dep resolution:
 *  - 26 of the 30 ctor deps are Koin-native on BOTH platforms — the
 *    dataJvmModule/datastoreCommonModule singles (incl. the cluster-flipped
 *    MediaRepository/UserDataMutator/MediaSearchEngine) plus the platform
 *    data modules (ImageUrlProvider, OfflineModeManager) and the settings
 *    feature's SettingsSearchProvider single.
 *  - the other 4 (PlaybackSyncScheduler, TvWatchNextScheduler — WorkManager
 *    workers; ContinueWatchingBroadcaster, LibrarySyncHook — app widget
 *    broadcast receivers) are Android-shaped: Koin singles in
 *    androidCoreDataModule / the app module on Android, honest no-op defs in
 *    desktopDataModule on desktop. All 30 deps resolve on BOTH platforms —
 *    desktop renders homeSection in the rail (live since the wave 8B
 *    desktop wiring).
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
