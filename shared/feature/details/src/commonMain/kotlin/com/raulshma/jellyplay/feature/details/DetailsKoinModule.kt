package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the details feature (V3/Phase X conveyor move; one
 * framework per type — every Hilt annotation was stripped at the move).
 *
 * Data-layer ctor deps resolve from the shared core graph on BOTH platforms
 * (dataJvmModule + datastoreCommonModule); the three module-local platform
 * seams are registered per platform:
 * - Android: `androidDetailsModule(context)` (storage probe) +
 *   app-side HiltInterop lazy singles for [DetailAudioPlayback] /
 *   [DetailThemeMusic] (they wrap the Hilt-owned legacy
 *   AudioPlaybackManager / ThemeMusicPlayer singletons).
 * - Desktop: `desktopDetailsPlatformModule(dataDir)` (no-op audio/theme +
 *   appdata storage probe) — the desktop registration is fully
 *   live-resolvable, dormant only for lack of nav wiring.
 */
val detailsModule: Module = module {
    single<DetailStrings> { detailStrings() }
    single { DetailStores(get(), get(), get(), get(), get()) }
    single { RemoteDiscoveryClients(get(), get(), get(), get()) }
    single { DownloadLifecycleActions.Factory(get(), get(), get(), get()) }
    single { ResyncActions.Factory(get(), get()) }
    single { PlaylistActions.Factory(get(), get()) }
    single { WatchPartyActions.Factory(get(), get()) }
    single { DetailActionFactories(get(), get(), get(), get()) }

    viewModel {
        DetailViewModel(
            storageProbe = get(),
            strings = get(),
            mediaRepository = get(),
            userDataMutator = get(),
            mediaDetailProvider = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            offlineRepository = get(),
            stores = get(),
            remoteDiscovery = get(),
            audioPlaybackManager = get(),
            audioQueueFacade = get(),
            themeMusicPlayer = get(),
            actionFactories = get(),
        )
    }
    viewModel {
        CollectionDetailViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        CastAndCrewViewModel(
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        ManageSeriesViewModel(
            strings = get(),
            mediaRepository = get(),
            arrRepository = get(),
        )
    }
    viewModel {
        PersonDetailViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
        )
    }
    viewModel {
        MediaInfoViewModel(
            mediaRepository = get(),
        )
    }
    viewModel {
        SeerrDetailViewModel(
            seerrRepository = get(),
            seerrRequestDelegate = get(),
            projections = get(),
            seerrPreferencesStore = get(),
            mediaRepository = get(),
        )
    }
}
