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
 * Wave 16C wasmJs split: everything whose dependency closure reaches the
 * jvmShared halves of core:data (AudioQueueFacade, DownloadIntake,
 * OfflineSyncManager, SyncPlayManager — Room/downloads/sync) moved OUT of
 * commonMain together with [DetailViewModel] and its action-factory bundle;
 * those defs now live in the per-platform registration modules
 * (`androidDetailsModule(context)` in androidMain /
 * `desktopDetailsPlatformModule(dataDir)` in jvmMain — the
 * player-video androidPlayerVideoModule/desktopPlayerVideoModule precedent).
 * This common module holds exactly the defs whose closure is wasm-clean;
 * every def here is LATENT on web except [SeerrDetailViewModel], whose ctor
 * deps (SeerrRepository + SeerrRequestDelegate from dataWasmModule,
 * PreferenceProjections + SeerrPreferencesStore from datastoreCommonModule,
 * the web shell's narrow MediaRepository) all resolve on web — that is the
 * one path wave 16C puts on the browser.
 *
 * Data-layer ctor deps resolve from the shared core graph on BOTH platforms
 * (dataJvmModule + datastoreCommonModule); the platform seams are registered
 * per platform:
 * - Android: `androidDetailsModule(context)` (storage probe + the jvm-only
 *   VM/factory defs) + the app composition root's Koin seam adapters for
 *   [DetailAudioPlayback] / [DetailThemeMusic] (androidAppInteropAdapters
 *   Module, over the core-data Koin singles).
 * - Desktop: `desktopDetailsPlatformModule(dataDir)` (no-op audio/theme +
 *   appdata storage probe + the same jvm-only VM/factory defs) — the desktop
 *   registration is fully live: DesktopAppRoot wires detailsSection behind
 *   every shared screen that pushes a detail route.
 */
val detailsModule: Module = module {
    single<DetailStrings> { detailStrings() }
    single { DetailStores(get(), get(), get(), get(), get()) }
    single { RemoteDiscoveryClients(get(), get(), get(), get()) }
    single { PlaylistActions.Factory(get(), get()) }

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
