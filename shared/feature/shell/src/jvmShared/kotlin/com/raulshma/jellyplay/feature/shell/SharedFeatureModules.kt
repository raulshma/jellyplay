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
import com.raulshma.jellyplay.feature.photos.di.photosModule
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
 * test reads this list out of this file's source text). When a new feature
 * adds a commonMain/jvmShared Module, add it
 * here; the guard test follows automatically. Forgetting this line is
 * silent — compile gates are BLIND to Koin registration.
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
 * Order is arbitrary; Koin registration order
 * is inert here (definitions are keyed, no overrides among features).
 *
 * Per-module notes below cover both shells' registrations (registration is
 * graph-shaped, routing is per-screen).
 */
val sharedFeatureModules: List<Module> = listOf(
    // Search: DesktopAppRoot renders searchSection as a rail destination
    // (Home is the start tab); all VM deps resolve from the shared graph
    // (MediaRepository cluster).
    searchModule,
    // …library: VM deps resolve from the shared graph, and
    // desktopPhotoExport (desktop's inline list) supplies the photo-export
    // actual (unsupported=no-op).
    libraryModule,
    // …photos: the PhotoAlbum/PhotoViewer ViewModels. Both VM ctor deps
    // (MediaRepository, ImageUrlProvider) are Koin-native in the shared
    // graph; the PhotoExport seam's actuals are the per-shell platform
    // modules (androidPhotoExportModule / desktopPhotoExportModule, the
    // shells' inline lists). Route.PhotoAlbum/Route.PhotoViewer stay core:ui
    // keys, registered by photosSection (AppSections) beside librarySection.
    photosModule,
    // …music: desktopPlayerModule provides the real desktop audio core
    // (DesktopAudioQueueManager over an audio-only MpvDesktopEngine +
    // DefaultAudioQueueFacade), so play/enqueue/instant-mix drive real
    // playback and track clicks navigate to Route.AudioPlayer. The
    // message-bus actual feeds a relay the desktop shell's snackbar host
    // collects (DesktopAppRoot) — error messages surface instead of dropping.
    musicModule,
    // …livetv: mediaRepository and friends are Koin singles in
    // dataJvmModule; the rail renders liveTvSection.
    liveTvModule,
    // …downloads: single-item AND series downloads resolve
    // (MediaRepository/UserDataMutator are Koin-owned), the in-process
    // DesktopDownloadManager and the 6 h auto-download loop start at desktop
    // boot (DesktopStartup), and the rail renders downloadsSection.
    downloadsModule,
    // …syncplay: the mediaRepository edge resolves from dataJvmModule, and
    // the rail renders syncPlaySection.
    syncPlayModule,
    // Settings: the shared settings ViewModels
    // (SettingsViewModel/AboutViewModel's AdminRepository resolves from
    // dataJvmModule; the rail renders settingsSection). Each shell's platform
    // pick of the seams (Android: SAF backup IO, LocaleManager, storage
    // walkers, About/Licenses sources; desktop: its own actuals, whose
    // update-check row rides AppUpdateRepository from desktopDataModule and
    // whose storage actuals are real: downloads + http-cache walked/cleared,
    // Coil's disk cache cleared through the injected image-cache handle)
    // stays in the per-shell lists. Android's four seams (auto-download
    // sync, notification reschedule, TV watch-next, audio cache clear) wrap
    // the schedulers, resolved straight from the core Koin graph.
    settingsModule,
    // Admin: the shared admin ViewModels — AdminRepository +
    // AdminStatisticsRepository are Koin singles in dataJvmModule on both
    // platforms, and the rail renders adminSection (gated by the desktop
    // admin-status state in DesktopAppRoot). The Android-only plugin-config
    // WebView ViewModel lives in androidAdminModule (the Android shell's
    // list) and is never registered on desktop (the shared PluginConfigHost
    // desktop actual renders its "not available" fallback).
    adminModule,

    // Editor: the shared metadata editor ViewModel.
    // MetadataEditorRepository / AuthRepository / SubtitleProviderRepository
    // are Koin-native; the StreamingSubtitleStore dep resolves from the core
    // Koin graph. StreamingSubtitleStoreImpl lives in jvmShared and
    // desktopDataModule binds the real file-backed store (appdata
    // streaming-subtitles subtree), so the EditorViewModel ctor graph fully
    // resolves and DesktopAppRoot renders editorSection (the details
    // screen's edit push, admin-gated like Android). The desktop upload
    // sheets use native AWT file pickers (DesktopEditorFilePicker), so
    // upload-from-file works alongside URL image upload and
    // remote/provider subtitle search.
    editorModule,

    // Calendar: all three ctor deps (ArrRepository, SeerrRepository,
    // ExperimentalStore) are Koin-native in the shared graph
    // (dataJvmModule/datastoreCommonModule on both platforms) — zero
    // Hilt-interop edges. Wired in nav (calendarSection in the rail).
    calendarModule,

    // Requests: all three ctor deps (SeerrRepository, ArrRepository,
    // ExperimentalStore) are Koin-owned, so — like calendar — this
    // registration involves no Hilt interop at all. Wired in nav
    // (requestsSection in the rail).
    requestsModule,

    // Shortcuts: sole ctor dep AuthRepository is Koin-native — zero Hilt
    // interop (calendar/requests class), wired in nav (shortcutsSection in
    // the rail). Omitting this line fails at runtime (NoDefinitionFound on
    // Route.Shortcuts) — the guard test pins the list.
    shortcutsModule,

    // Newsletter: imageUrlProvider/notificationStore/authRepository/
    // mediaRepository are all Koin-native — no interop definitions needed.
    // Wired in nav (newsletterSection in the rail).
    newsletterModule,

    // Insights: WatchHistoryRepository, PlaybackRepository and
    // MediaRepository are all Koin-native (dataJvmModule) — all three
    // heatmap-VM ctor deps resolve on both platforms, and the rail renders
    // insightsSection. The share seam's desktop actual writes a tmpdir PNG
    // and hands it to the system viewer, so the share button is visible.
    insightsModule,

    // Onboarding: all four wizard VM deps (PreferenceProjections,
    // SeerrPreferencesStore, SeerrSecureCredentialsStore, PreferencesEditor)
    // are Koin-owned in the shared datastore graph — zero Hilt interop
    // (calendar/requests class). Nav registers onboardingSection (reachable
    // from Shortcuts), and the shell pushes Route.Onboarding once per
    // authenticated session while the persisted onboarding_completed flag is
    // unset (same pref the Android app gates on; completion through the
    // shared wizard writes it, so the gate never re-fires).
    onboardingModule,

    // Arrqueue: ArrRepository (dataJvmModule) and ExperimentalStore
    // (datastoreCommonModule) are Koin-owned — zero Hilt interop; wired in
    // nav (arrQueueSection in the rail). Message feedback flows through the
    // shared UserMessageBus (screens read LocalUserMessageBus directly).
    arrqueueModule,

    // Home (desktop landing screen): 26 of HomeViewModel's 30 ctor
    // deps are Koin-native in the shared graph; the remaining four
    // (PlaybackSyncScheduler, TvWatchNextScheduler from the core data graph,
    // and ContinueWatchingBroadcaster + LibrarySyncHook — Android resolves
    // them from androidAppModule, desktop from desktopDataModule's no-op
    // defs) resolve from Koin too. SettingsSearchProvider resolves from
    // settingsModule. DesktopAppRoot wires homeSection in the rail
    // (HomeLifecycleSeam's jvm actual stays a no-op, so sections refresh on
    // their own flows rather than a process start/stop signal).
    homeModule,

    // Auth: both VM ctor deps (AuthRepository,
    // ServerDiscoveryRepository) are Koin-owned in dataJvmModule —
    // zero Hilt interop (calendar/requests/shortcuts class). The
    // LocalNetworkStatus gate is per-shell: Android bridges the
    // :core:ui LocalNetworkAccess object with the application context
    // (androidAdminModule pattern, androidAuthModule); desktop registers its
    // own non-blaming pick from the shared module's jvmMain
    // (desktopAuthPlatformModule) — the auth seam's fun-interface probe in
    // feature/auth (blames a connect failure on the Android 17+
    // local-network permission), NOT core/ui's same-named composition local.
    // Unified sign-in on the desktop screens: the signed-out gate (the
    // shared SignedOutAuthHost) and the signed-in settings drill-ins
    // (DesktopAppRoot's authSection entries) both instantiate these
    // ViewModels.
    authModule,

    // Details: the shared details ViewModels + helpers —
    // every data-layer ctor dep is Koin-native
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
    // Audio player: the
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
    // Book reader: the CBZ/PDF reader ViewModel over
    // Route.BookReader (details Read button + offline downloads). Reader
    // engine seams are per-shell platform modules (androidBookPlayerModule /
    // desktopBookPlayerModule, the latter rooted at the same appdata dir the
    // download storage uses — desktopDataModule pattern).
    playerBookModule,

    // Player-live: the shared live-player ViewModel (Koin-native
    // deps + the per-shell platform seams). On desktop it is latent: the
    // shared ViewModel + LastChannelStore resolve (data/datastore graph),
    // but the three platform seams (engine factory, audio,
    // transcode-reasons renderer) are Android-only definitions and the
    // player screen lives in the module's androidMain —
    // Route.LiveTvChannelPlayer stays guarded in DesktopAppRoot, so nothing
    // instantiates the VM on desktop. Android's seams: the engine factory
    // resolves the shared NetworkQualifiers.streamingHttpClient; the audio
    // seam binds Media3LivePlayerAudio, the VIDEO-family focus surface;
    // the transcode-reasons renderer delegates to the core:ui formatter.
    playerLiveModule,
)
