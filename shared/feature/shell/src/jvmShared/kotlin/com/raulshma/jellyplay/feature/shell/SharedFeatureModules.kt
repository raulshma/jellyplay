package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.feature.admin.di.adminModule
import com.raulshma.jellyplay.feature.arrqueue.di.arrqueueModule
import com.raulshma.jellyplay.feature.auth.di.authModule
import com.raulshma.jellyplay.feature.calendar.di.calendarModule
import com.raulshma.jellyplay.feature.details.detailsModule
import com.raulshma.jellyplay.feature.downloads.di.downloadsModule
import com.raulshma.jellyplay.feature.editor.di.editorModule
import com.raulshma.jellyplay.feature.home.di.homeModule
import com.raulshma.jellyplay.feature.insights.di.insightsModule
import com.raulshma.jellyplay.feature.library.di.libraryModule
import com.raulshma.jellyplay.feature.livetv.di.liveTvModule
import com.raulshma.jellyplay.feature.music.di.musicModule
import com.raulshma.jellyplay.feature.newsletter.di.newsletterModule
import com.raulshma.jellyplay.feature.onboarding.di.onboardingModule
import com.raulshma.jellyplay.feature.player.audio.di.playerAudioModule
import com.raulshma.jellyplay.feature.book.di.playerBookModule
import com.raulshma.jellyplay.feature.player.live.di.playerLiveModule
import com.raulshma.jellyplay.feature.requests.di.requestsModule
import com.raulshma.jellyplay.feature.search.di.searchModule
import com.raulshma.jellyplay.feature.settings.di.settingsModule
import com.raulshma.jellyplay.feature.shortcuts.di.shortcutsModule
import com.raulshma.jellyplay.feature.syncplay.di.syncPlayModule
import org.koin.core.module.Module

/**
 * The shared FEATURE Koin modules both JVM shells register, exactly — the
 * single declaration both startKoin blocks spread
 * (`*sharedFeatureModules`) and KoinModuleRegistrationGuardTest derives its
 * expected set from (set-equality in BOTH directions against the feature
 * modules discovered under `shared/feature/<module>/src/{commonMain,jvmShared}`; the
 * test reads this list out of this file's source text — the webFeatureModules
 * precedent). When a new feature lands a commonMain/jvmShared Module, add it
 * here; the guard test follows automatically. Forgetting this line is the
 * arrqueue/shortcuts lesson — compile gates are BLIND to Koin registration.
 *
 * Deliberately NOT in this list:
 *  - per-shell platform modules (the `android*Module`/`desktop*Module`
 *    actuals) — they stay inline in each shell's own list;
 *  - core modules (`coreUiMessageModule`, the datastore/network/database/data
 *    graphs) — core, not feature;
 *  - player-video's defs (its platform modules androidPlayerVideoModule/
 *    desktopPlayerVideoModule) and subtitle-tester (androidMain-only, no
 *    commonMain Module val) contribute no entries.
 *
 * Order follows the Android shell's historical list; Koin registration order
 * is inert here (definitions are keyed, no overrides among features) — the
 * only order-sensitive registration in either shell, desktop's
 * desktopAppUpdateModule override, stays LAST in the desktop shell's own
 * list per docs/adr/desktop-auto-update.md.
 *
 * Per-module notes below are the MERGED conveyor history of both shells'
 * former inline lists (registration is graph-shaped, routing is per-screen).
 */
val sharedFeatureModules: List<Module> = listOf(
    // V3 feature conveyor: search — LIVE since the desktop nav v1
    // (DesktopAppRoot renders searchSection as a rail destination; Home is
    // the start tab); all VM deps resolve since the MediaRepository cluster
    // flip.
    searchModule,
    // …library, second conveyor item — LIVE like search: the VM deps
    // resolved with the cluster flip, and desktopPhotoExport (desktop's
    // inline list) supplies the photo-export actual (unsupported=no-op).
    libraryModule,
    // …music, third conveyor item — LIVE since (browse) and fully playable
    // since: desktopPlayerModule provides the real desktop audio core
    // (DesktopAudioQueueManager over an audio-only MpvDesktopEngine +
    // DefaultAudioQueueFacade), so play/enqueue/instant-mix drive real
    // playback and track clicks navigate to the now-live Route.AudioPlayer.
    // Since the message-bus actual feeds a relay the desktop shell's
    // snackbar host collects (DesktopAppRoot) — error messages surface
    // instead of dropping.
    musicModule,
    // …livetv, fourth conveyor item — LIVE since the cluster flip
    // (mediaRepository and friends are Koin singles in dataJvmModule now);
    // nav v1 renders liveTvSection in the rail.
    liveTvModule,
    // …downloads, fifth conveyor item — fully live since the cluster flip:
    // single-item AND series downloads resolve (MediaRepository/
    // UserDataMutator are Koin-owned), the in-process DesktopDownloadManager
    // and the 6 h auto-download loop start at desktop boot (DesktopStartup),
    // and nav v1 renders downloadsSection.
    downloadsModule,
    // …syncplay, sixth conveyor item — LIVE since the cluster flip: the
    // former mediaRepository edge resolves from dataJvmModule, and nav v1
    // renders syncPlaySection in the rail.
    syncPlayModule,
    // V3 settings conveyor: the shared settings ViewModels (LIVE since the
    // admin repositories' Koin flip — the last Hilt-only edges,
    // SettingsViewModel/AboutViewModel's AdminRepository, resolve from
    // dataJvmModule; nav v1+ renders settingsSection in the rail). Each
    // shell's platform pick of the seams (Android: SAF backup IO,
    // LocaleManager, storage walkers, About/Licenses sources; desktop: its
    // own actuals, whose update-check row went live with the AppUpdate split
    // — AppUpdateRepository resolves from desktopDataModule — and whose
    // storage actuals went REAL: downloads + http-cache walked/cleared,
    // Coil's disk cache cleared through the injected image-cache handle)
    // stays in the per-shell lists. Android's four seams (auto-download
    // sync, notification reschedule, TV watch-next, audio cache clear) wrap
    // the legacy schedulers, resolved straight from the core Koin graph.
    settingsModule,
    // V3 admin conveyor (eighth feature): the shared admin ViewModels —
    // LIVE since the same flip: AdminRepository + AdminStatisticsRepository
    // are Koin singles in dataJvmModule on both platforms, and nav v1+
    // renders adminSection in the rail (gated by the desktop admin-status
    // state in DesktopAppRoot). The Android-only plugin-config WebView
    // ViewModel lives in androidAdminModule (the Android shell's list) and
    // is never registered on desktop (the shared PluginConfigHost desktop
    // actual renders its "not available" fallback).
    adminModule,

    // V3 editor conveyor (ninth feature): the shared metadata editor
    // ViewModel. MetadataEditorRepository / AuthRepository /
    // SubtitleProviderRepository are Koin-native; the StreamingSubtitleStore
    // dep resolves from the core Koin graph (the legacy :core:data
    // remainder). LIVE on desktop since the store promotion:
    // StreamingSubtitleStoreImpl moved to jvmShared and desktopDataModule
    // binds the real file-backed store (appdata streaming-subtitles
    // subtree), so the EditorViewModel ctor graph fully resolves and
    // DesktopAppRoot renders editorSection (the details screen's edit push,
    // admin-gated like Android). The desktop upload sheets use native AWT
    // file pickers (DesktopEditorFilePicker), so upload-from-file works
    // alongside URL image upload and remote/provider subtitle search.
    editorModule,

    // V3 calendar conveyor: all three ctor deps (ArrRepository,
    // SeerrRepository, ExperimentalStore) are already Koin-native in the
    // shared graph (dataJvmModule/datastoreCommonModule on both platforms) —
    // the first conveyor module with zero Hilt-interop edges. LIVE and wired
    // in nav v1 (calendarSection in the rail).
    calendarModule,

    // V3 requests conveyor (eleventh feature): all three ctor deps
    // (SeerrRepository, ArrRepository, ExperimentalStore) were already
    // Koin-owned, so — like calendar, and unlike the nine features above —
    // this registration involves no Hilt interop at all. LIVE and wired in
    // nav v1 (requestsSection in the rail).
    requestsModule,

    // V3 shortcuts conveyor: sole ctor dep AuthRepository is Koin-native —
    // zero Hilt interop (calendar/requests class), LIVE and wired in nav v1
    // (shortcutsSection in the rail). Missed at the feature's landing;
    // caught by a registration check (NoDefinitionFound on Route.Shortcuts)
    // — the lesson the guard test ratchets.
    shortcutsModule,

    // V3 newsletter conveyor: imageUrlProvider/notificationStore/
    // authRepository/mediaRepository were all already Koin-native — no
    // interop definitions were needed for this feature. LIVE since the
    // cluster flip (the former mediaRepository edge now resolves from
    // dataJvmModule) and wired in nav v1 (newsletterSection in the rail).
    newsletterModule,

    // V3 insights conveyor: WatchHistoryRepository, PlaybackRepository and
    // MediaRepository are all Koin-native (dataJvmModule) — LIVE since the
    // cluster flip: all three heatmap-VM ctor deps resolve on both
    // platforms, and nav v1 renders insightsSection in the rail. The share
    // seam's desktop actual writes a tmpdir PNG and hands it to the system
    // viewer, so the share button is visible.
    insightsModule,

    // V3 onboarding conveyor: all four wizard VM deps (PreferenceProjections,
    // SeerrPreferencesStore, SeerrSecureCredentialsStore, PreferencesEditor)
    // were already Koin-owned in the shared datastore graph — zero Hilt
    // interop, calendar/requests class. Fully live registration on desktop:
    // Nav v1 registers onboardingSection (reachable from Shortcuts), and a
    // later pass added the first-run gate: the shell pushes Route.Onboarding
    // once per authenticated session while the persisted
    // onboarding_completed flag is unset (same pref the Android app gates
    // on; completion through the shared wizard writes it, so the gate never
    // re-fires).
    onboardingModule,

    // V3 arrqueue conveyor: ArrRepository (dataJvmModule) and
    // ExperimentalStore (datastoreCommonModule) were already Koin-owned —
    // zero Hilt interop; LIVE and wired in nav v1 (arrQueueSection in the
    // rail). Message feedback flows through the shared UserMessageBus (the
    // ArrQueueMessenger seam is retired — screens read LocalUserMessageBus
    // directly).
    arrqueueModule,

    // Home conveyor (desktop landing screen): 26 of HomeViewModel's 30 ctor
    // deps are Koin-native in the shared graph; the remaining four
    // (PlaybackSyncScheduler, TvWatchNextScheduler from the core data graph,
    // and ContinueWatchingBroadcaster + LibrarySyncHook — Android resolves
    // them from androidAppModule, desktop from desktopDataModule's no-op
    // defs) resolve from Koin too. SettingsSearchProvider resolves from
    // settingsModule. DesktopAppRoot wires homeSection in the rail
    // (HomeLifecycleSeam's jvm actual stays a no-op, so sections refresh on
    // their own flows rather than a process start/stop signal).
    homeModule,

    //  auth cutover (feature-conveyor transform from the legacy
    // :feature:auth): both VM ctor deps (AuthRepository,
    // ServerDiscoveryRepository) were already Koin-owned in dataJvmModule —
    // zero Hilt interop (calendar/requests/shortcuts class). The
    // LocalNetworkStatus gate is per-shell: Android bridges the legacy
    // :core:ui LocalNetworkAccess object with the application context
    // (androidAdminModule pattern, androidAuthModule); desktop registers its
    // own non-blaming pick from the shared module's jvmMain
    // (desktopAuthPlatformModule) — the auth seam's fun-interface probe in
    // feature/auth (blames a connect failure on the Android 17+
    // local-network permission), NOT core/ui's same-named composition local.
    // Unified sign-in on the desktop screens: the signed-out gate
    // (DesktopSignedOutAuthHost) and the signed-in settings drill-ins
    // (DesktopAppRoot's authSection entries) both instantiate these
    // ViewModels; the legacy DesktopSignInPane pane is retired.
    authModule,

    // Details conveyor (cutover — legacy :feature:details was the largest
    // never-conveyor module): the shared details ViewModels + helpers,
    // registration fully live — every data-layer ctor dep is Koin-native
    // (dataJvmModule/datastoreCommonModule). Android's two media3 playback
    // seams (per-item audio play, ambient theme music) resolve through the
    // androidAppInteropAdaptersModule adapters (its shell list); desktop's
    // AudioQueueFacade comes from desktopPlayerModule's DefaultAudioQueueFacade
    // binding and the module-local platform seams supply no-op audio/theme
    // playback + the appdata storage probe. DesktopAppRoot wires
    // detailsSection behind every shared screen that pushes a detail route
    // (search results, requests/calendar → SeerrDetail, person rows), so
    // detail VMs instantiate for real.
    detailsModule,
    // Audio player conveyor (legacy :feature:player:audio deleted): the
    // shared player ViewModels. Queue/effects/transport deps resolve from
    // the core Koin graph (AudioPlaybackManager implements both shared
    // playback contracts — Android; desktopPlayerModule provides the four
    // playback/cast ctor deps over the shared DesktopAudioQueueManager
    // single plus the never-connected DesktopAudioPlayerCast). DesktopAppRoot
    // registers audioPlayerSection and music track clicks navigate to
    // Route.AudioPlayer for real; Android's engine/cast seams are the
    // androidAppInteropAdaptersModule adapters over the same single +
    // CastManager.
    playerAudioModule,
    // Book reader conveyor: the CBZ/PDF reader ViewModel over
    // Route.BookReader (details Read button + offline downloads). Reader
    // engine seams are per-shell platform modules (androidBookPlayerModule /
    // desktopBookPlayerModule, the latter rooted at the same appdata dir the
    // download storage uses — desktopDataModule pattern).
    playerBookModule,

    // Player-live conveyor: the shared live-player ViewModel (Koin-native
    // deps + the per-shell platform seams) replacing the legacy
    // :feature:player:live module. On desktop, documented-latent: the
    // shared ViewModel + LastChannelStore resolve (data/datastore graph),
    // but the three platform seams (engine factory, audio,
    // transcode-reasons renderer) are Android-only definitions and the
    // player screen lives in the module's androidMain —
    // Route.LiveTvChannelPlayer stays guarded in DesktopAppRoot, so nothing
    // instantiates the VM on desktop (same latent class as the
    // player-adjacent features). Android's seams: the engine factory
    // resolves the shared NetworkQualifiers.streamingHttpClient; the audio
    // seam wraps the legacy PlayerAudioLifecycle; the transcode-reasons
    // renderer delegates to the legacy core:ui formatter.
    playerLiveModule,
)
