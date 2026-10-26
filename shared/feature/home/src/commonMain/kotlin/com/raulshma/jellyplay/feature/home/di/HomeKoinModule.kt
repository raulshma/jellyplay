package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.feature.home.HomeGenreRailCache
import com.raulshma.jellyplay.feature.home.HomeHeroVideoLoader
import com.raulshma.jellyplay.feature.home.HomeClock
import com.raulshma.jellyplay.feature.home.HomePresenceChipsController
import com.raulshma.jellyplay.feature.home.HomeStatsCardLoader
import com.raulshma.jellyplay.feature.home.HomeStores
import com.raulshma.jellyplay.feature.home.HomeRefresherFactory
import com.raulshma.jellyplay.feature.home.HomeSheetsFactory
import com.raulshma.jellyplay.feature.home.HomeViewModel
import com.raulshma.jellyplay.feature.home.MyMediaHiddenViewModel
import com.raulshma.jellyplay.feature.home.NextUpExcludedViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the home feature (conveyor transform — one
 * framework per type: Koin is the single constructor owner).
 *
 * Dep resolution:
 *  - the VM's ctor deps are Koin-native singles from the
 *    dataJvmModule/datastoreCommonModule modules (incl. the cluster-flipped
 *    MediaRepository/UserDataMutator/MediaSearchEngine) plus the platform
 *    data modules (ImageUrlProvider, OfflineModeManager) and the settings
 *    feature's SettingsSearchProvider single;
 *  - the platform seams (HomeClock, HomeSyncStatusFactory, HomeNewsletterGate)
 *    resolve from [platformHomeModule] — jvmShared adapters over the
 *    core:data jvmShared singles — while the download reads
 *    (QuickDownloadActions, SeriesEpisodeDownloads) resolve from core:data's
 *    own seams (dataJvmModule);
 *  - the refresher's pure-DI collaborators
 *    (PlaybackSyncScheduler — WorkManager worker; ContinueWatchingBroadcaster,
 *    LibrarySyncHook — app widget broadcast receivers) are Android-shaped:
 *    Koin singles in androidCoreDataModule / the app module on Android,
 *    honest no-op defs in desktopDataModule on desktop. All resolve on BOTH
 *    platforms — desktop renders homeSection in the rail (live since the
 *     desktop wiring).
 */
val homeModule: Module = module {
    includes(platformHomeModule())

    single {
        HomeRefresherFactory(
            clock = get(),
            homeFeed = get(),
            userDataChanges = get(),
            seerrRepository = get(),
            arrRepository = get(),
            orderHomeSections = get(),
            widgetDataStore = get(),
            continueWatchingBroadcaster = get(),
            tvWatchNextScheduler = get(),
            librarySyncHook = get(),
            bookTocCacheRepository = get(),
            recentLocalWrites = get(),
        )
    }
    single {
        HomeSheetsFactory(
            episodeCatalogue = get(),
            seriesDownloads = get(),
            downloadIntake = get(),
            userMessageBus = get(),
            offlineRepository = get(),
        )
    }
    // Stats card: the non-admin-safe primitives live on the user-scoped
    // MediaInfoApiClient single; the richer admin+plugin upgrade reads the
    // core:data AdminStatisticsRepository single (consumed as-is).
    single {
        HomeStatsCardLoader(
            mediaInfoApiClient = get(),
            adminStatisticsRepository = get(),
        )
    }
    // Genre rail: the VM-scoped TTL cache over the user-scoped
    // LibraryApiClient single, stamped by the same HomeClock bean the
    // refresher's throttle/TTL math reads. A FACTORY (not a single) so each
    // HomeViewModel instance owns its cache — a shared singleton would serve
    // a previous user's genre list across user switches within the TTL.
    factory {
        HomeGenreRailCache(
            libraryApiClient = get(),
            clock = get(),
        )
    }
    // Hero video billboard: the TTL-memoised clip probe (ThemeVideo →
    // trailer-embed fallback) behind the hero's ambient video. A SINGLE
    // (unlike the genre cache) because the hero composable has no VM identity
    // to key a factory on — the user-switch staleness the factory avoids is
    // handled inside the loader instead (the memo holds metadata only; stream
    // URLs are re-shaped against the active session at serve time).
    single {
        HomeHeroVideoLoader(
            libraryApiClient = get(),
            playbackRepository = get(),
            mediaRepository = get(),
            seerrRepository = get(),
            clock = { get<HomeClock>().nowEpochMillis() },
        )
    }
    // Presence chips: the VM-scoped controller (probe-once /Sessions
    // verdict + per-chip dismiss memory — see its KDoc), FACTORY for the
    // same reason as HomeGenreRailCache above: the memory must die with the
    // home session, never leak across user switches. Consumes the core:data
    // AdminRepository single (getSessions) and the SyncPlayManager single
    // (the join seam) as-is.
    factory {
        HomePresenceChipsController(
            adminRepository = get(),
            syncPlayManager = get(),
        )
    }
    viewModel {
        HomeViewModel(
            episodeCatalogue = get(),
            userDataMutator = get(),
            mediaSearchEngine = get(),
            homeFeed = get(),
            imageUrlProvider = get(),
            artworkUrls = get(),
            photoFolderPrefetcher = get(),
            downloadIntake = get(),
            quickDownloadActions = get(),
            offlineRepository = get(),
            bookTocCacheRepository = get(),
            offlineModeManager = get(),
            newsletterTriggerManager = get(),
            clock = get(),
            prefs = HomeStores(
                homeDiscovery = get(),
                appearance = get(),
                experimental = get(),
                playback = get(),
                seerrPreferences = get(),
            ),
            preferencesEditor = get(),
            seerrRequestDelegate = get(),
            authRepository = get(),
            homeSession = get(),
            userMessageBus = get(),
            settingsSearchProvider = get(),
            homeRefresherFactory = get(),
            syncStatusStateHolderFactory = get(),
            homeSheetsFactory = get(),
            statsCardLoader = get(),
            genreRailCache = get(),
            presenceChipsController = get(),
        )
    }
    viewModel {
        NextUpExcludedViewModel(
            homeDiscoveryStore = get(),
            editor = get(),
            mediaRepository = get(),
            artworkUrls = get(),
        )
    }
    viewModel {
        MyMediaHiddenViewModel(
            homeDiscoveryStore = get(),
            editor = get(),
            libraryFeed = get(),
        )
    }
}
