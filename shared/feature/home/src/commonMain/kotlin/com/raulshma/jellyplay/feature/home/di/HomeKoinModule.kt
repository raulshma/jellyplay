package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolderFactory
import com.raulshma.jellyplay.feature.home.HomePrefsProviders
import com.raulshma.jellyplay.feature.home.HomeRefresherFactory
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
 *  - the VM's ctor deps are Koin-native singles from the
 *    dataJvmModule/datastoreCommonModule modules (incl. the cluster-flipped
 *    MediaRepository/UserDataMutator/MediaSearchEngine) plus the platform
 *    data modules (ImageUrlProvider, OfflineModeManager) and the settings
 *    feature's SettingsSearchProvider single.
 *  - the refresher's pure-DI collaborators and the sync holder's factory
 *    (PlaybackSyncScheduler — WorkManager worker; ContinueWatchingBroadcaster,
 *    LibrarySyncHook — app widget broadcast receivers) are Android-shaped:
 *    Koin singles in androidCoreDataModule / the app module on Android,
 *    honest no-op defs in desktopDataModule on desktop. All resolve on BOTH
 *    platforms — desktop renders homeSection in the rail (live since the
 *    wave 8B desktop wiring).
 */
val homeModule: Module = module {
    single {
        HomeRefresherFactory(
            timeSource = get(),
            mediaRepository = get(),
            seerrRepository = get(),
            arrRepository = get(),
            orderHomeSections = get(),
            widgetDataStore = get(),
            continueWatchingBroadcaster = get(),
            tvWatchNextScheduler = get(),
            librarySyncHook = get(),
        )
    }
    single {
        SyncStatusStateHolderFactory(
            playbackOutboxRepository = get(),
            playbackSyncScheduler = get(),
            offlineFirstItemResolver = get(),
        )
    }
    viewModel {
        HomeViewModel(
            episodeCatalogue = get(),
            userDataMutator = get(),
            mediaSearchEngine = get(),
            mediaRepository = get(),
            imageUrlProvider = get(),
            photoFolderPrefetcher = get(),
            downloadRepository = get(),
            downloadIntake = get(),
            mediaDownloadActions = get(),
            offlineRepository = get(),
            offlineModeManager = get(),
            newsletterTriggerManager = get(),
            prefs = HomePrefsProviders(
                homeDiscovery = get(),
                appearance = get(),
                experimental = get(),
                playback = get(),
            ),
            preferencesEditor = get(),
            seerrRequestDelegate = get(),
            seerrPreferencesStore = get(),
            authRepository = get(),
            homeSession = get(),
            userMessageBus = get(),
            settingsSearchProvider = get(),
            homeRefresherFactory = get(),
            syncStatusStateHolderFactory = get(),
        )
    }
}
