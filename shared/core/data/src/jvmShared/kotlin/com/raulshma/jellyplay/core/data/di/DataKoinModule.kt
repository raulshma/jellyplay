package com.raulshma.jellyplay.core.data.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the platform-independent data layer
 * (docs/kmp-migration-plan.md part 2). Batch 1 moved the portable
 * leaf types; batch 2 moved the Koin-constructible repository layer; batch 3
 * the session / playback / sync / syncplay / worker cluster. Each definition
 * is explicit (no reflection) and matches the constructor verbatim.
 *
 * Construction-owner rule: Koin owns every type defined here — one framework
 * per type. During the Hilt era these singles were reached from Hilt through
 * the legacy DataModule's `koin().get()` bridges; the impl classes'
 * `@Inject`/`@Singleton` annotations were stripped at the move, and the whole
 * bridge layer left with the Hilt extinction — Koin only.
 *
 *  MediaRepository cluster flip: the last Hilt-owned data-layer
 * cluster moved here — `MediaRepositoryImpl` (+ its PlayedStateSync /
 * MediaRepositoryCacheInvalidation / LyricsRepository views),
 * `PlayedStateSyncImpl`, `UserDataMutatorImpl`, `MediaSearchEngineImpl`,
 * `UnifiedMediaDetailProviderImpl`, `OfflineFirstItemResolverImpl` and
 * `OfflinePlaybackFacade` (see the definitions below). The legacy DataModule
 * constructs nothing from the cluster anymore (the whole legacy DataModule
 * left with the Hilt extinction); Koin builds the cluster natively. `PlaybackSourceResolver` left
 * that latent-on-desktop state with the playback flips: its impl moved
 * here Uri-free (`File.toURI()` instead of `android.net.Uri.fromFile`), so
 * UnifiedMediaDetailProviderImpl's ctor dep resolves from this module on
 * BOTH platforms and MediaDetailProvider is live on desktop too. The app's
 * HiltInteropModule reverse single for it was deleted (one framework per
 * type).
 *
 * The V3 downloads conveyor (C4-part-2 notes, fifth conveyor item) moved the
 * download engine here — `DownloadRepositoryImpl`, `DownloadDelegate` and the
 * transfer machinery — with its Android-only surfaces (WorkManager
 * enqueue/cancel, notification summary, Coil preloading, storage layout)
 * behind the `DownloadEnqueueCoordinator` / `DownloadProgressNotifier` /
 * `OfflineImagePreloader` / `DownloadStorageLayoutContract` seams, whose
 * Android actuals the app composition root registers
 * (androidDownloadSeamsModule) and desktop actuals live in
 * [desktopDataModule]. With the cluster flip above, the desktop
 * `MediaRepositoryAccess` actual became real — desktop series downloads and
 * the auto-download scheduler are live.
 *
 * `DefaultAudioQueueFacade` is the one playback-graph type NOT defined here:
 * its AudioQueueManager ctor dep is the media3 AudioPlaybackManager, so its
 * Koin single lives in this module's androidMain AndroidCoreDataKoinModule (
 * there since then; desktopPlayerModule binds the desktop twin).
 * `AudioLyricsManager` left that Android-only set when its sole dep (the
 * LyricsRepository view of MediaRepository) became the single below;
 * `OfflineSyncManager` flipped into this module with the V3 downloads
 * conveyor.
 *
 * The admin flip moved the last two Hilt-owned repositories here:
 * `AdminRepositoryImpl` (verbatim — no platform surface) and
 * `AdminStatisticsRepositoryImpl`, whose Android surfaces became seams: the
 * former `context.getString(R.string.data_*)` labels now flow through
 * [AdminStatisticsLabelProvider] (Android def in the app composition root
 * over legacy core:data resources; desktop def = base-locale English
 * literals), `android.util.Log` became the core.data.log facade, and the
 * `@ApplicationScope` scope is the DatastoreQualifiers single. The desktop
 * settings + admin sections went live with this flip.
 *
 * Family split: the former single 900-line module is now sibling family
 * modules in this package (the file's old comment-section boundaries — one
 * Koin module per family), and this aggregate keeps the `dataJvmModule`
 * name alive via `includes` so consumers and the Koin smoke test are
 * unchanged. Every binding body moved verbatim — no reordering, no renames.
 *
 * Platform-bound definitions (Context / dataDir-shaped picks) live in
 * [androidDataModule] / [desktopDataModule].
 */
val dataJvmModule: Module = module {
    includes(
        dataCoreLeafModule,
        dataRepositoriesModule,
        dataSessionPlaybackModule,
        dataMediaRepositoryModule,
        dataDownloadsConveyorModule,
        dataDownloadActionsModule,
        dataPlaybackFamilyModule,
        dataSubtitleProviderModule,
        dataSeerrArrModule,
        dataAdminModule,
    )
}
