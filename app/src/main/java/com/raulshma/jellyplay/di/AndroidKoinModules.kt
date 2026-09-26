package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.di.androidCoreDataModule
import com.raulshma.jellyplay.core.data.di.androidDataModule
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.datastore.di.androidDatastoreModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.database.di.androidDatabaseModule
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.network.di.androidNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.notification.di.androidNotificationModule
import com.raulshma.jellyplay.core.ui.di.androidCoreUiModule
import com.raulshma.jellyplay.core.ui.di.coreUiMessageModule
import com.raulshma.jellyplay.feature.admin.di.androidAdminModule
import com.raulshma.jellyplay.feature.auth.di.androidAuthModule
import com.raulshma.jellyplay.feature.book.di.androidBookPlayerModule
import com.raulshma.jellyplay.feature.details.androidDetailsModule
import com.raulshma.jellyplay.feature.photos.di.androidPhotoExportModule
import com.raulshma.jellyplay.feature.player.live.di.androidPlayerLiveModule
import com.raulshma.jellyplay.feature.player.video.di.androidPlayerVideoModule
import com.raulshma.jellyplay.feature.settings.di.androidSettingsPlatformModule
import com.raulshma.jellyplay.feature.shell.sharedFeatureModules
import com.raulshma.jellyplay.feature.subtitle.tester.di.androidSubtitleTesterModule
import org.koin.core.module.Module

/**
 * The Android shell's startKoin module list (extracted from
 * JellyPlayApplication.onCreate): the shared core graph, this shell's
 * platform actuals, and the ONE spread of [sharedFeatureModules] (the
 * shared feature declaration in shared/feature/shell — both JVM shells
 * consume it; the per-module conveyor history rides that declaration).
 *
 * JellyPlayApplication.onCreate is the only consumer:
 * `startKoin { modules(androidKoinModules(this)) }` — mirroring desktop's
 * Main.kt → DesktopKoinModules' `desktopKoinModules(…)` fold. Every
 * per-registration comment moved here verbatim with its registration; the
 * list order is unchanged, and [Application] is the application context
 * the former `this@JellyPlayApplication` call sites passed.
 */
fun androidKoinModules(app: Application): List<Module> = listOf(
    datastoreCommonModule,
    androidDatastoreModule(app),
    databaseDaosModule,
    androidDatabaseModule(app),
    networkJvmModule,
    androidNetworkModule(app),
    dataJvmModule,
    androidDataModule(app),
    // Legacy core:data remainder (Hilt-extinct — media3
    // audio stack, cast, schedulers, remote control, workers) +
    // core:notification and core:ui's UserMessageBus.
    androidCoreDataModule(app),
    androidNotificationModule(app),
    androidCoreUiModule,
    // V3 downloads conveyor: Android actuals of the portable
    // download engine's seams (WorkManager enqueue/coordinator,
    // Context/StatFs storage layout, notification summary, Coil
    // preload). Koin owns these legacy-side impls so the
    // DownloadRepository single in dataJvmModule resolves.
    androidDownloadSeamsModule(app),
    // Dev v0.10.7 quick-action download-outcome bridge lives in
    // androidAppInteropAdaptersModule below (DownloadOutcomeMessenger
    // -> core:ui UserMessageBus).
    // Admin flip: Android actual of the admin-statistics
    // label seam — legacy core:data R.string over the Koin-owned
    // AdminStatisticsRepositoryImpl (dataJvmModule), byte-identical
    // to the pre-move context.getString calls.
    androidAdminSeamsModule(app),
    // App Koin graph: the former Hilt-owned :app classes
    // (shell coordinators, startup initializers, widget schedulers/
    // updaters, DeepLinkHandler, FloatingPlayerState), the three
    // former WidgetModule @Binds pairs, and the shared-feature seam
    // adapters the deleted HiltInteropModule used to bridge
    // (MusicMessageBus / DetailThemeMusic /
    // AudioPlayerCast — direct Koin resolution
    // now, no EntryPoint; AudioPlayerEngine moved into core/data
    // and androidCoreDataModule aliases it onto the manager).
    androidAppModule(app),
    androidAppInteropAdaptersModule(app),
    // App-shell ViewModels (Main/PlayOn/WidgetConfig): resolved
    // through the AndroidX ViewModelStore via KoinViewModelFactory,
    // so activity-scoped instance-sharing semantics are unchanged.
    androidAppViewModelsModule,
    // The shared commonMain feature Koin modules both JVM shells
    // register, declared ONCE in shared/feature/shell
    // (sharedFeatureModules);
    // the per-module conveyor history rides that declaration.
    // Registration order is inert in Koin (definitions are keyed);
    // only Android's platform actuals below are order-sensitive,
    // and they stay inline in THIS list.
    *sharedFeatureModules.toTypedArray(),
    // core:ui's UserMessageBus module — core, not feature, so it
    // stays inline here rather than in sharedFeatureModules (the
    // home/settings ViewModels post their feedback through it).
    coreUiMessageModule,
    // V3 settings conveyor (Android platform pick): the Android
    // actuals of the shared settings ViewModels' seams (SAF backup
    // IO, LocaleManager, storage walkers, About/Licenses sources).
    // The four seams (auto-download sync, notification reschedule,
    // TV watch-next, audio cache clear) wrap the legacy schedulers,
    // resolved straight from the core Koin graph.
    androidSettingsPlatformModule(app),
    androidSettingsSeamsModule(),
    // MediaStore/FileProvider photo-export actual for the photos
    // feature's PhotoExport seam (androidDataModule pattern).
    androidPhotoExportModule(app),
    // V3 admin conveyor (Android half): the Android-only
    // plugin-config WebView ViewModel (Context ctor param);
    // AdminRepository and AdminStatisticsRepository resolve from
    // dataJvmModule (Koin-owned since the admin flip).
    androidAdminModule(app),

    // V3 subtitle-tester conveyor (final feature): the whole
    // feature is Android-only (androidMain-heavy module — the
    // preview engines, surface host, SAF font picker and raw-asset
    // factory have no desktop halves), so this is the only
    // registration. PlayerEngineFactory and FontProvider are
    // Koin-owned by androidPlayerVideoModule below; the
    // PlaybackRequestFactory single is constructed with the
    // application context here.
    androidSubtitleTesterModule(app),

    // Player-video conveyor: the migrated video player
    // (:feature:player:video + the absorbed :feature:player:core
    // remains). Koin owns the engine stack, the font/cache/
    // preview singletons and the VideoPlayerViewModel; the six
    // legacy playback deps resolve from the core Koin graph
    // (the legacy :core:data remainder). Sole entry
    // point stays PlayerActivity — no desktop registration
    // (latent feature, subtitle-tester precedent).
    androidPlayerVideoModule(app),

    //  auth cutover (Android platform half): the
    // LocalNetworkStatus gate is Android-only here — it bridges
    // the legacy :core:ui LocalNetworkAccess object with the
    // application context (androidAdminModule pattern); desktop
    // registers its own non-blaming pick from the shared module's
    // jvmMain.
    androidAuthModule(app),
    // Details conveyor (Android platform half): the two media3
    // playback seams (per-item audio play, ambient theme music)
    // resolve through the androidAppInteropAdaptersModule adapters
    // above; the storage probe is the StatFs androidMain actual
    // below.
    androidDetailsModule(app),
    // Book reader conveyor (Android platform half): the reader
    // engine seams are the module's android platform module.
    androidBookPlayerModule(app),

    // Player-live conveyor (Android platform half): the three
    // platform seams replacing the legacy :feature:player:live
    // module. The engine factory resolves the shared
    // NetworkQualifiers.streamingHttpClient; the audio seam wraps
    // the legacy PlayerAudioLifecycle; the transcode-reasons
    // renderer delegates to the legacy core:ui formatter.
    androidPlayerLiveModule(app),

)
