package com.raulshma.jellyplay

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import coil3.size.Size
import com.raulshma.jellyplay.core.data.di.CoreDataWorkerFactory
import com.raulshma.jellyplay.core.data.di.androidCoreDataModule
import com.raulshma.jellyplay.core.data.di.androidDataModule
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.di.androidDatastoreModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.database.di.androidDatabaseModule
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.model.ImageCache
import com.raulshma.jellyplay.core.network.di.androidNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.notification.di.NotificationWorkerFactory
import com.raulshma.jellyplay.core.notification.di.androidNotificationModule
import com.raulshma.jellyplay.core.ui.di.androidCoreUiModule
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.di.androidAdminSeamsModule
import com.raulshma.jellyplay.di.androidAppInteropAdaptersModule
import com.raulshma.jellyplay.di.androidAppModule
import com.raulshma.jellyplay.di.androidAppViewModelsModule
import com.raulshma.jellyplay.di.androidDownloadSeamsModule
import com.raulshma.jellyplay.di.androidSettingsSeamsModule
import com.raulshma.jellyplay.feature.search.di.searchModule
import com.raulshma.jellyplay.feature.library.di.androidPhotoExportModule
import com.raulshma.jellyplay.feature.library.di.libraryModule
import com.raulshma.jellyplay.feature.music.di.musicModule
import com.raulshma.jellyplay.feature.livetv.di.liveTvModule
import com.raulshma.jellyplay.feature.downloads.di.downloadsModule
import com.raulshma.jellyplay.feature.syncplay.di.syncPlayModule
import com.raulshma.jellyplay.feature.settings.di.settingsModule
import com.raulshma.jellyplay.feature.settings.di.androidSettingsPlatformModule
import com.raulshma.jellyplay.feature.admin.di.adminModule
import com.raulshma.jellyplay.feature.admin.di.androidAdminModule
import com.raulshma.jellyplay.feature.subtitle.tester.di.androidSubtitleTesterModule
import com.raulshma.jellyplay.feature.player.live.di.androidPlayerLiveModule
import com.raulshma.jellyplay.feature.player.live.di.playerLiveModule
import com.raulshma.jellyplay.feature.player.video.di.androidPlayerVideoModule


import com.raulshma.jellyplay.feature.editor.di.editorModule

import com.raulshma.jellyplay.feature.calendar.di.calendarModule


import com.raulshma.jellyplay.feature.requests.di.requestsModule

import com.raulshma.jellyplay.feature.shortcuts.di.shortcutsModule


import com.raulshma.jellyplay.feature.newsletter.di.newsletterModule

import com.raulshma.jellyplay.feature.insights.di.insightsModule
import com.raulshma.jellyplay.core.ui.di.coreUiMessageModule
import com.raulshma.jellyplay.feature.home.di.homeModule
import com.raulshma.jellyplay.feature.arrqueue.di.arrqueueModule
import com.raulshma.jellyplay.feature.auth.di.androidAuthModule
import com.raulshma.jellyplay.feature.auth.di.authModule

import com.raulshma.jellyplay.feature.details.androidDetailsModule
import com.raulshma.jellyplay.feature.details.detailsModule
import com.raulshma.jellyplay.feature.player.audio.di.playerAudioModule
import com.raulshma.jellyplay.feature.onboarding.di.onboardingModule


import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.raulshma.jellyplay.widget.AppWidgetWorkerFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/** Bound on the STA-3 device-id prewarm await; see the launch in [onCreate]. */
private const val DEVICE_ID_PREWARM_TIMEOUT_MS = 5_000L

class JellyPlayApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    // App widget recommendation workers (plain CoroutineWorker
                    // ctors — see AppWidgetWorkerFactory).
                    addFactory(AppWidgetWorkerFactory())
                    // Core-data legacy workers (plain CoroutineWorker ctors —
                    // see CoreDataWorkerFactory/NotificationWorkerFactory).
                    addFactory(CoreDataWorkerFactory())
                    addFactory(NotificationWorkerFactory())
                },
            )
            .build()

    // Deferred single access (wave 8B — Hilt removal): the former
    // javax.inject.Provider fields deferred Hilt construction off the
    // cold-start path; kotlin `by lazy` over the Koin container preserves that
    // exactly (definitions are lazy, and each resolved single is the same
    // memoized instance the rest of the graph sees).
    private val okHttpClient: OkHttpClient by lazyFromKoin()
    private val networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore by lazyFromKoin()
    // STA-3 (2026-09 perf audit): resolved ONLY inside the IO prewarm block
    // below (same deferred-construction pattern as networkOfflineStore above).
    // ServerIdentityStore.identity is stateIn(Eagerly) on Dispatchers.Default,
    // so on a first-launch cold start the network single's `identity.value
    // .deviceId ?: runBlocking { ensureDeviceId() }` fallback
    // (AndroidNetworkModule) could win the race against that eager DataStore
    // read and block MainViewModel construction on main. The prewarm calls the
    // idempotent ensureDeviceId() itself and awaits the published id, so the
    // runBlocking branch never fires — same UUID either way, and DataStore
    // serializes the (first-launch-only) write.
    private val serverIdentityStore: com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore by lazyFromKoin()
    // (Wave 7C: the FontProvider/VideoStreamCache prewarm Providers that used
    // to live here left with the player-video migration — both impls are
    // Koin-owned in shared/feature/player-video's androidPlayerVideoModule, so
    // no bridged binding remains; the IO block below resolves them from the
    // container directly, same deferred construction timing.)
    // Lazy defers construction of AudioPlaybackManager (and its transitive
    // 14-dep graph: AudioLibraryBrowser, AudioProgressReporter,
    // AudioCrossfader, QueueUndoStack, LruCache(25), …) off the main thread
    // until the IO launch block below actually resolves it. The start() body
    // already offloads its real work to Dispatchers.IO, so behavior is
    // unchanged; only the construction cost moves off the cold-start
    // critical path.
    private val audioPlaybackManager: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager by lazyFromKoin()
    // Lazy defers construction of NowPlayingWidgetUpdater, whose constructor
    // pulls in AudioPlaybackManager — the same 14-dep graph above. A direct
    // single access re-pulls that whole graph before onCreate returns;
    // resolving it inside the IO launch block below keeps it off the
    // cold-start critical path. start() only launches observers on its own
    // scope, so behavior is unchanged.
    private val nowPlayingWidgetUpdater: com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater by lazyFromKoin()
    // The remaining schedulers/listeners are likewise deferred: resolving one
    // constructs it (and its transitive graph — several pull Room, OkHttp,
    // repository singletons) inside the IO launch blocks, not during
    // onCreate before the first frame. Each .start()/.enqueue…()/sync() body
    // already runs on its own scope or dispatches to IO, so construction is
    // the only cost on the critical path.
    private val notificationScheduler: NotificationScheduler by lazyFromKoin()
    private val autoDownloadScheduler: com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler by lazyFromKoin()
    private val userDataSyncScheduler: com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler by lazyFromKoin()
    private val playbackSyncScheduler: com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler by lazyFromKoin()
    private val playbackSyncReconnectListener: com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener by lazyFromKoin()
    private val downloadReconnectListener: com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener by lazyFromKoin()
    private val notificationReconnectListener: com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener by lazyFromKoin()
    private val widgetWorkScheduler: com.raulshma.jellyplay.widget.WidgetWorkScheduler by lazyFromKoin()
    private val downloadRecoveryInitializer: com.raulshma.jellyplay.startup.DownloadRecoveryInitializer by lazyFromKoin()
    // Lazy defers construction of AppUpdateRepository (which pulls GitHub +
    // OkHttp) off the cold-start path. cleanupDownloadedUpdate is a cheap file
    // delete, but construction is the cost worth deferring.
    private val appUpdateRepository: com.raulshma.jellyplay.core.data.update.AppUpdateRepository by lazyFromKoin()

    private val applicationScope: CoroutineScope
        by lazy { KoinPlatform.getKoin()!!.get(DatastoreQualifiers.applicationScope) }

    /**
     * Memoizing deferred access into the Koin container — the kotlin-lazy
     * twin of the former javax.inject.Provider fields (a @Singleton-backed
     * Hilt Provider returned the same instance per get(), which a memoizing
     * lazy matches).
     */
    private inline fun <reified T : Any> lazyFromKoin() =
        lazy { KoinPlatform.getKoin()!!.get<T>() }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest hook in app startup — before ContentProviders and onCreate,
        // so debug StrictMode sees the whole init path. The debug source set
        // backs installDebugStrictMode with real policies; the release source
        // set ships a no-op, so release builds compile none of it.
        installDebugStrictMode()
    }

    override fun onCreate() {
        // Koin owns construction for every module (plan §Phase C4 / wave 8B —
        // Hilt is fully gone from :app). MUST run before anything resolves a
        // dependency: the lazy fields and every Activity/Service/widget entry
        // point reach into this container, and definitions are lazy, so this
        // adds no cold-start construction cost.
        startKoin {
            modules(
                datastoreCommonModule,
                androidDatastoreModule(this@JellyPlayApplication),
                databaseDaosModule,
                androidDatabaseModule(this@JellyPlayApplication),
                networkJvmModule,
                androidNetworkModule(this@JellyPlayApplication),
                dataJvmModule,
                androidDataModule(this@JellyPlayApplication),
                // Legacy core:data remainder (wave 8A: Hilt-extinct — media3
                // audio stack, cast, schedulers, remote control, workers) +
                // core:notification and core:ui's UserMessageBus.
                androidCoreDataModule(this@JellyPlayApplication),
                androidNotificationModule(this@JellyPlayApplication),
                androidCoreUiModule,
                // V3 downloads conveyor: Android actuals of the portable
                // download engine's seams (WorkManager enqueue/coordinator,
                // Context/StatFs storage layout, notification summary, Coil
                // preload). Koin owns these legacy-side impls so the
                // DownloadRepository single in dataJvmModule resolves.
                androidDownloadSeamsModule(this@JellyPlayApplication),
                // Dev v0.10.7 quick-action download-outcome bridge lives in
                // androidAppInteropAdaptersModule below (DownloadOutcomeMessenger
                // -> core:ui UserMessageBus).
                // Admin flip (Wave wB): Android actual of the admin-statistics
                // label seam — legacy core:data R.string over the Koin-owned
                // AdminStatisticsRepositoryImpl (dataJvmModule), byte-identical
                // to the pre-move context.getString calls.
                androidAdminSeamsModule(this@JellyPlayApplication),
                // App Koin graph (wave 8B): the former Hilt-owned :app classes
                // (shell coordinators, startup initializers, widget schedulers/
                // updaters, DeepLinkHandler, FloatingPlayerState), the three
                // former WidgetModule @Binds pairs, and the shared-feature seam
                // adapters the deleted HiltInteropModule used to bridge
                // (MusicMessageBus / DetailAudioPlayback / DetailThemeMusic /
                // AudioPlayerEngine / AudioPlayerCast — direct Koin resolution
                // now, no EntryPoint).
                androidAppModule(this@JellyPlayApplication),
                androidAppInteropAdaptersModule(this@JellyPlayApplication),
                // App-shell ViewModels (Main/PlayOn/WidgetConfig): resolved
                // through the AndroidX ViewModelStore via KoinViewModelFactory,
                // so activity-scoped instance-sharing semantics are unchanged.
                androidAppViewModelsModule,
                searchModule,
                libraryModule,
                musicModule,
                liveTvModule,
                downloadsModule,
                syncPlayModule,
                // V3 settings conveyor: the shared settings ViewModels plus the
                // Android platform pick of their seams (SAF backup IO,
                // LocaleManager, storage walkers, About/Licenses sources). The
                // four seams (auto-download sync, notification reschedule, TV
                // watch-next, audio cache clear) wrap the legacy schedulers,
                // resolved straight from the core Koin graph.
                settingsModule,
                androidSettingsPlatformModule(this@JellyPlayApplication),
                androidSettingsSeamsModule(),
                // MediaStore/FileProvider photo-export actual for the library
                // feature's PhotoExport seam (androidDataModule pattern).
                androidPhotoExportModule(this@JellyPlayApplication),
                // V3 admin conveyor (eighth feature): the shared admin
                // ViewModels plus the Android-only plugin-config WebView
                // ViewModel (Context ctor param). AdminRepository and
                // AdminStatisticsRepository resolve from dataJvmModule
                // (Koin-owned since the Wave wB admin flip).
                adminModule,
                androidAdminModule(this@JellyPlayApplication),


                // V3 editor conveyor (ninth feature): the shared metadata
                // editor ViewModel. MetadataEditorRepository / AuthRepository /
                // SubtitleProviderRepository are Koin-native; the
                // StreamingSubtitleStore dep resolves from the core Koin graph
                // (the legacy :core:data remainder, wave 8A).
                editorModule,

                // V3 calendar conveyor: all three ctor deps (ArrRepository,
                // SeerrRepository, ExperimentalStore) are already Koin-native
                // in the shared graph — the first conveyor module with zero
                // Hilt-interop edges.
                calendarModule,


                // V3 requests conveyor (eleventh feature): all three ctor deps
                // (SeerrRepository, ArrRepository, ExperimentalStore) were
                // already Koin-owned, so — like calendar, and unlike the nine
                // features above — this registration involves no Hilt interop
                // at all.
                requestsModule,

                // V3 shortcuts conveyor: sole ctor dep AuthRepository is
                // Koin-native — zero Hilt interop (calendar/requests class).
                // Missed at the feature's landing; caught by the Phase X
                // Koin-registration audit (NoDefinitionFound on Route.Shortcuts).
                shortcutsModule,


                // V3 newsletter conveyor: imageUrlProvider/notificationStore/
                // authRepository/mediaRepository were all already Koin-native —
                // no interop definitions were needed for this feature.
                newsletterModule,

                // V3 insights conveyor: WatchHistoryRepository,
                // PlaybackRepository and MediaRepository are all Koin-native
                // (dataJvmModule).
                insightsModule,


                // V3 onboarding conveyor: all four wizard VM deps
                // (PreferenceProjections, SeerrPreferencesStore,
                // SeerrSecureCredentialsStore, PreferencesEditor) were already
                // Koin-owned in the shared datastore graph — zero Hilt interop,
                // calendar/requests class.
                onboardingModule,

                // V3 arrqueue conveyor: ArrRepository (dataJvmModule) and
                // ExperimentalStore (datastoreCommonModule) were already
                // Koin-owned — zero Hilt interop; message feedback flows
                // through the ArrQueueMessenger seam instead of the legacy
                // UserMessageBus ctor dep.
                arrqueueModule,

                // Home conveyor (Phase X cutover; desktop landing screen):
                // 26 of HomeViewModel's 30 ctor deps are Koin-native in the
                // shared graph; the remaining four (PlaybackSyncScheduler,
                // TvWatchNextScheduler from the core data graph, and
                // ContinueWatchingBroadcaster + LibrarySyncHook from
                // androidAppModule above) resolve from Koin too.
                // SettingsSearchProvider resolves from settingsModule.
                coreUiMessageModule,
                homeModule,

                // V3 subtitle-tester conveyor (final feature): the whole
                // feature is Android-only (androidMain-heavy module — the
                // preview engines, surface host, SAF font picker and raw-asset
                // factory have no desktop halves), so this is the only
                // registration. PlayerEngineFactory and FontProvider are
                // Koin-owned by androidPlayerVideoModule below; the
                // PlaybackRequestFactory single is constructed with the
                // application context here.
                androidSubtitleTesterModule(this@JellyPlayApplication),

                // Player-video conveyor (wave 7C): the migrated video player
                // (:feature:player:video + the absorbed :feature:player:core
                // remains). Koin owns the engine stack, the font/cache/
                // preview singletons and the VideoPlayerViewModel; the six
                // legacy playback deps resolve from the core Koin graph
                // (the legacy :core:data remainder, wave 8A). Sole entry
                // point stays PlayerActivity — no desktop registration
                // (latent feature, subtitle-tester precedent).
                androidPlayerVideoModule(this@JellyPlayApplication),

                // Phase X auth cutover (feature-conveyor transform): both VM
                // ctor deps (AuthRepository, ServerDiscoveryRepository) were
                // already Koin-owned in dataJvmModule — zero Hilt interop
                // (calendar/requests/shortcuts class). The LocalNetworkStatus
                // gate is Android-only here: it bridges the legacy :core:ui
                // LocalNetworkAccess object with the application context
                // (androidAdminModule pattern); desktop registers its own
                // non-blaming pick from the shared module's jvmMain.
                authModule,
                androidAuthModule(this@JellyPlayApplication),
                // Details conveyor (Phase X cutover wave): the shared details
                // ViewModels + helpers. Data-layer deps are all Koin-native
                // (dataJvmModule/datastoreCommonModule); the two media3
                // playback seams (per-item audio play, ambient theme music)
                // resolve through the androidAppInteropAdaptersModule adapters
                // above; the storage probe is the StatFs androidMain actual
                // below.
                detailsModule,
                androidDetailsModule(this@JellyPlayApplication),
                // Audio player conveyor (wave 7A, legacy :feature:player:audio
                // deleted): the shared player ViewModels. Queue/effects/transport
                // deps resolve from the core Koin graph (AudioPlaybackManager
                // implements both shared playback contracts), and the engine/
                // cast seams are the androidAppInteropAdaptersModule adapters
                // above over the same single + CastManager.
                playerAudioModule,

                // Player-live conveyor (wave 7B): the shared live-player
                // ViewModel (Koin-native deps + the three platform seams
                // below) replacing the legacy :feature:player:live module.
                // The engine factory resolves the shared
                // NetworkQualifiers.streamingHttpClient; the audio seam wraps
                // the legacy PlayerAudioLifecycle; the transcode-reasons
                // renderer delegates to the legacy core:ui formatter.
                playerLiveModule,
                androidPlayerLiveModule(this@JellyPlayApplication),

            )
        }
        super.onCreate()
        // Critical-path prewarms: (a) the network-offline DataStore slice —
        // the real persisted read happens in the store's eager stateIn
        // upstream, this keeps the flow warm for the imageLoader's
        // lazily-sized DiskCache — (b) the identity slice (STA-3, see the
        // field comment above), and (c) the subtitle font byte cache and
        // video stream cache so the first ASS playback doesn't read fonts
        // on Main.
        //
        // STA-5 (2026-09 perf audit): this block used to await, strictly in
        // order, offline read → font prewarm → stream-cache prewarm → audio
        // start → widget start — off-main but serialized. The two cache
        // prewarms now run concurrently (no shared state between the
        // font byte cache and the video stream cache), and the audio+widget
        // group moved to its own sibling launch below (this block's own
        // original comment: "no dependency on the groups below"). The DataStore
        // prewarms stay FIRST in this coroutine: the Coil DiskCache sizing in
        // newImageLoader reads networkOffline.value lazily on the first
        // networked image write, and the documented lazily-sized-DiskCache race
        // needs this read to win that — both slices launch ahead of the async
        // prewarms, preserving exactly the head start they had before.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { networkOfflineStore.networkOffline.first() }
            // STA-3: NOT the audit's literal `identity.first()` — a
            // stateIn(Eagerly) StateFlow always has its initial value
            // available, so a plain first() returns the all-null placeholder
            // instantly during the DataStore-read window and closes nothing;
            // and a bare `first { deviceId != null }` would hang forever on a
            // fresh install (nothing writes DEVICE_ID until ensureDeviceId
            // runs). Calling the idempotent ensureDeviceId() here — the same
            // call the network single's runBlocking fallback would make —
            // reads-or-persists the UUID off main, and awaiting the non-null
            // publication guarantees every later `.value.deviceId` read in
            // AndroidNetworkModule resolves the fast path.
            runCatching {
                serverIdentityStore.ensureDeviceId()
                // Bounded wait: a wedged DataStore (corruption, upstream
                // error stranding the Eagerly-started identity flow on its
                // all-null placeholder) would suspend a bare first{}
                // forever and gate the font/stream prewarms below. On
                // timeout they proceed anyway; the network module readers
                // keep their own runBlocking ensureDeviceId() fallback.
                withTimeoutOrNull(DEVICE_ID_PREWARM_TIMEOUT_MS) {
                    serverIdentityStore.identity.first { !it.deviceId.isNullOrEmpty() }
                }
            }
            // Wave 7C: Koin-owned since the player-video migration (the Hilt
            // javax.inject.Provider fields died with the module flip); still
            // resolved here, inside the IO launcher, so the font-asset copy +
            // cache-index open stay off the cold-start critical path.
            val koin = org.koin.mp.KoinPlatform.getKoin()
            coroutineScope {
                // runCatching stays INSIDE each async so a failed prewarm
                // neither cancels the sibling nor fails the outer launch —
                // the same per-call swallow the sequential runCatching chain
                // had.
                async { runCatching { koin?.get<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>()?.prewarm() } }
                async { runCatching { koin?.get<com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache>()?.prewarm() } }
            }
        }
        // STA-5: the audio + widget updaters — launched as a sibling of the
        // prewarm block above instead of serializing behind it. Resolves the
        // same lazy fields as before (AudioPlaybackManager's 14-dep graph +
        // Room queue restore, then NowPlayingWidgetUpdater), in the same
        // order, just no longer gated on the DataStore reads and cache
        // prewarms finishing first.
        applicationScope.launch(Dispatchers.IO) {
            audioPlaybackManager.start()
            nowPlayingWidgetUpdater.start()
        }
        // Background schedulers — independent enqueue calls, all KEEP-safe.
        // Run concurrently with the critical path so cold start isn't gated on
        // audio init. Each lazy resolution constructs its scheduler here (off
        // the main thread) instead of during onCreate.
        applicationScope.launch(Dispatchers.IO) {
            widgetWorkScheduler.enqueuePeriodic()
            userDataSyncScheduler.enqueuePeriodic()
            playbackSyncScheduler.enqueuePeriodic()
            playbackSyncReconnectListener.start()
            downloadReconnectListener.start()
            notificationReconnectListener.start()
            autoDownloadScheduler.sync()
            notificationScheduler.scheduleOrUpdate()
        }
        // Best-effort download recovery — independent of the above groups.
        applicationScope.launch(Dispatchers.IO) {
            downloadRecoveryInitializer.recover()
        }
        // Sweep any APK left by a prior self-update. A successful install
        // restarts the process (so this runs in the new version) and leaves the
        // old APK orphaned; a cancelled/failed install also leaves it behind.
        // Safe at startup: onCreate precedes any new download.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { appUpdateRepository.cleanupDownloadedUpdate() }
        }
    }

    private val imageClient by lazy {
        okHttpClient.newBuilder()
            // Drop the inherited OkHttp http_cache so image bytes aren't written
            // to disk twice. The base client's cache (`cacheDir/http_cache`,
            // sized for API JSON) is shared via newBuilder(); without this Coil
            // would also write every fetched image to its own `image_cache`
            // DiskCache (256 MB default), doubling disk writes for every poster
            // and bloating http_cache with binary data competing with the small
            // JSON responses it was sized for. `.cache(null)` leaves Coil's
            // DiskCache as the sole owner of image bytes — functionally
            // identical to the previous behavior for image rendering.
            .cache(null)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val imageLoader by lazy {
        // Tier the memory-cache budget on device RAM class, mirroring the
        // EngineDeviceProfile.isLowRamDevice gate already used for trickplay.
        // On a 1 GB TV stick the default 20% would over-reserve a small heap
        // competing with MPV/ExoPlayer native buffers.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRamDevice = am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
        val memoryCachePercent = if (isLowRamDevice) 0.12 else 0.20

        ImageLoader.Builder(this)
            // The explicit OkHttp fetcher registered below always wins, so
            // Coil's ServiceLoader discovery of its default fetcher is dead
            // work — skip it, mirroring the desktop builder in Main.kt.
            .serviceLoaderEnabled(false)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@JellyPlayApplication, memoryCachePercent)
                    .build()
            }
            .diskCache {
                // Sized inside the builder lambda: Coil builds the DiskCache
                // lazily on its FIRST access (the first networked image write),
                // not at ImageLoader construction — by then the `networkOffline`
                // pre-warm in onCreate has long since published the persisted
                // slice, so `.value` reads the user-configured size instead of
                // the default. A persisted size > 0 wins; anything else falls
                // back to 256 MB.
                val cacheMb = networkOfflineStore.networkOffline.value.maxCacheSizeMb
                val cacheSize = if (cacheMb > 0) cacheMb * 1024L * 1024L else 256L * 1024 * 1024
                DiskCache.Builder()
                    .directory(cacheDir.resolve(ImageCache.DIR).absolutePath.toPath())
                    .maxSizeBytes(cacheSize)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}
