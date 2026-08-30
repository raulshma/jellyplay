package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.data.di.desktopDataModule
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.database.di.desktopDatabaseModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.desktopDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.di.desktopNetworkModule
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.desktop.player.desktopPlayerModule
import com.raulshma.jellyplay.feature.search.di.searchModule
import com.raulshma.jellyplay.feature.library.di.desktopPhotoExportModule
import com.raulshma.jellyplay.feature.library.di.libraryModule
import com.raulshma.jellyplay.feature.music.di.musicModule
import com.raulshma.jellyplay.feature.music.feedback.desktopMusicMessageBusModule
import com.raulshma.jellyplay.feature.livetv.di.liveTvModule
import com.raulshma.jellyplay.feature.downloads.di.downloadsModule
import com.raulshma.jellyplay.feature.syncplay.di.syncPlayModule
import com.raulshma.jellyplay.feature.settings.di.settingsModule
import com.raulshma.jellyplay.feature.settings.di.desktopSettingsPlatformModule
import com.raulshma.jellyplay.feature.admin.di.adminModule


import com.raulshma.jellyplay.feature.editor.di.editorModule

import com.raulshma.jellyplay.feature.calendar.di.calendarModule


import com.raulshma.jellyplay.feature.requests.di.requestsModule
import com.raulshma.jellyplay.feature.shortcuts.di.shortcutsModule

import com.raulshma.jellyplay.feature.newsletter.di.newsletterModule

import com.raulshma.jellyplay.feature.insights.di.insightsModule

import com.raulshma.jellyplay.feature.details.desktopDetailsPlatformModule
import com.raulshma.jellyplay.feature.details.detailsModule
import com.raulshma.jellyplay.feature.player.audio.di.playerAudioModule
import com.raulshma.jellyplay.feature.onboarding.di.onboardingModule

import com.raulshma.jellyplay.core.ui.di.coreUiMessageModule
import com.raulshma.jellyplay.feature.home.di.homeModule
import com.raulshma.jellyplay.feature.arrqueue.di.arrqueueModule
import com.raulshma.jellyplay.feature.auth.di.authModule
import com.raulshma.jellyplay.feature.auth.di.desktopAuthPlatformModule
import com.raulshma.jellyplay.feature.player.live.di.playerLiveModule
import com.raulshma.jellyplay.feature.player.video.di.desktopPlayerVideoModule


import org.koin.core.context.startKoin

fun main() {
    // Wave 12A startup baseline: t0 is the literal first statement so every
    // mark below measures against true process start. Marks themselves are
    // AtomicLong writes (~zero cost); everything heavier (JSON emission,
    // auto-exit timer) only arms when a jellyplay.perf.* property is set —
    // see DesktopStartupPerf.
    val bootT0Nanos = System.nanoTime()
    val paths = DesktopPaths.resolve()
    java.io.File(paths.dataDir.toString()).mkdirs()
    java.io.File(paths.configDir.toString()).mkdirs()

    // Wave 10A crash scaffold: hooks the JVM-wide uncaught-exception handler
    // BEFORE anything that can throw (Koin graph, player engines, compose
    // window), then consumes the previous session's crash marker — if the
    // last run recorded an uncaught throwable, log it here and surface a
    // one-line note + log path in the About dialog via DesktopAppRoot.
    val crashHandler = DesktopCrashHandler(logsDir = paths.logsDirNio).install()
    val previousCrash = crashHandler.consumePreviousCrashMarker()
    if (previousCrash != null) {
        println(
            "[JellyPlay] previous session ended unexpectedly; " +
                "crash log: ${previousCrash.logFile} (${previousCrash.crashedAtUtc})",
        )
    }

    val startupPerf = DesktopStartupPerf(
        logsDirNio = paths.logsDirNio,
        bootT0Nanos = bootT0Nanos,
    )
    startupPerf.scheduleMeasurementHooksIfRequested()

    val koinApp = startKoin {
        modules(
            datastoreCommonModule,
            desktopDatastoreModule(paths.dataDir),
            databaseDaosModule,
            desktopDatabaseModule(paths.databaseFile),
            networkJvmModule,
            desktopNetworkModule(paths.configDir),
            dataJvmModule,
            desktopDataModule(paths.dataDirNio),
            desktopPlayerModule,
            // Video player (wave 8C registration, wave 9A playback): the
            // VideoPlayerViewModel is commonMain and live-resolvable here, the
            // SwingPanel/HWND video surface composes inside Route.VideoPlayer,
            // and desktopPlayerModule supplies the per-session mpv
            // PlayerEngineFactory binding (this module deliberately does not —
            // MpvDesktopEngine is an app-layer type). Windows only:
            // DesktopAppRoot keeps Route.VideoPlayer dead-end-guarded on other
            // OSes where no embedded surface exists. The no-op seam bindings in
            // the module still cover those guarded OSes.
            desktopPlayerVideoModule,
            // V3 feature conveyor: search — LIVE since the Phase X desktop
            // nav v1 (DesktopAppRoot renders searchSection as the start
            // tab); all VM deps resolve since the MediaRepository cluster
            // flip.
            searchModule,
            // …library, second conveyor item — LIVE like search: the VM
            // deps resolved with the cluster flip, and desktopPhotoExport
            // supplies the photo-export actual (unsupported=no-op).
            libraryModule,
            desktopPhotoExportModule(),
            // …music, third conveyor item — LIVE since Wave wC (browse) and
            // fully playable since wave 9B: desktopPlayerModule provides the
            // real desktop audio core (DesktopAudioQueueManager over an
            // audio-only MpvDesktopEngine + DefaultAudioQueueFacade), so
            // play/enqueue/instant-mix drive real playback and track clicks
            // navigate to the now-live Route.AudioPlayer. Since wave 21B the
            // message-bus actual feeds a relay the shell's snackbar host
            // collects (DesktopAppRoot) — error messages surface instead of
            // dropping.
            musicModule,
            desktopMusicMessageBusModule(),
            // …livetv, fourth conveyor item — LIVE since the cluster flip
            // (mediaRepository and friends are Koin singles in dataJvm
            // Module now); nav v1 renders liveTvSection in the rail.
            liveTvModule,
            // …downloads, fifth conveyor item — fully live since the
            // cluster flip: single-item AND series downloads resolve
            // (MediaRepository/UserDataMutator are Koin-owned), the
            // in-process DesktopDownloadManager and the 6 h auto-download
            // loop start below, and nav v1 renders downloadsSection.
            downloadsModule,
            // …syncplay, sixth conveyor item — LIVE since the cluster flip:
            // the former mediaRepository edge resolves from dataJvmModule,
            // and nav v1 renders syncPlaySection in the rail.
            syncPlayModule,
            // …settings, seventh conveyor item — LIVE since the admin
            // repositories' Koin flip (Wave wB): the last Hilt-only edges
            // (SettingsViewModel/AboutViewModel's AdminRepository) resolve
            // from dataJvmModule, and nav v1+ renders settingsSection in the
            // rail (with the desktop platform actuals below). Desktop's
            // update-check row went live with the AppUpdate split (Wave xB;
            // AppUpdateRepository resolves from desktopDataModule), and the
            // storage actuals went REAL with wave 21B (downloads + http-cache
            // walked/cleared, Coil's temp-dir disk cache cleared through the
            // injected image-cache handle).
            settingsModule,
            desktopSettingsPlatformModule(
                dataDir = paths.dataDirNio,
                configDir = paths.configDirNio,
                imageCache = desktopCoilImageCacheOps(),
            ),
            // …admin, eighth conveyor item — LIVE since the same flip:
            // AdminRepository + AdminStatisticsRepository are Koin singles in
            // dataJvmModule on both platforms, and nav v1+ renders
            // adminSection in the rail (gated by the desktop admin-status
            // state in DesktopAppRoot). The Android-only plugin-config
            // WebView ViewModel lives in androidAdminModule and is never
            // registered here (the shared PluginConfigHost desktop actual
            // renders its "not available" fallback).
            adminModule,


            // …editor, ninth conveyor item — LIVE since the wave 18B store
            // promotion: StreamingSubtitleStoreImpl moved to jvmShared and
            // desktopDataModule binds the real file-backed store (appdata
            // streaming-subtitles subtree), so the EditorViewModel ctor graph
            // fully resolves and DesktopAppRoot renders editorSection (the
            // details screen's edit push, admin-gated like Android). Wave 20A
            // gave the upload sheets native AWT file pickers
            // (DesktopEditorFilePicker), so upload-from-file works alongside
            // URL image upload and remote/provider subtitle search.
            editorModule,

            // …calendar, conveyor feature — LIVE and wired in nav v1
            // (calendarSection in the rail): ArrRepository + SeerrRepository
            // (dataJvmModule) and ExperimentalStore (datastoreCommonModule)
            // all resolve on desktop.
            calendarModule,


            // …requests, eleventh conveyor item — LIVE and wired in nav v1
            // (requestsSection in the rail): the entire ctor graph is
            // Koin-native on BOTH platforms (SeerrRepository +
            // ArrRepository from dataJvmModule, ExperimentalStore from
            // datastoreCommonModule above).
            requestsModule,




            // …shortcuts, a later conveyor item — LIVE and wired in nav v1
            // (shortcutsSection in the rail): the single ctor dep
            // (AuthRepository) resolves from dataJvmModule.
            shortcutsModule,

            // …newsletter, conveyor item after requests — LIVE since the
            // cluster flip (the former mediaRepository edge now resolves
            // from dataJvmModule) and wired in nav v1 (newsletterSection in
            // the rail).
            newsletterModule,


            // …insights, conveyor feature — LIVE since the cluster flip:
            // all three heatmap-VM ctor deps resolve on desktop
            // (WatchHistoryRepository + PlaybackRepository from
            // dataJvmModule, MediaRepository from the flipped cluster), and
            // nav v1 renders insightsSection in the rail. The share seam's
            // desktop actual (wave 20C) writes a tmpdir PNG and hands it to
            // the system viewer, so the share button is visible.
            insightsModule,





            // …onboarding, conveyor feature — fully live registration
            // (calendar/requests/shortcuts class): all four wizard VM deps
            // resolve on desktop (PreferenceProjections/
            // SeerrPreferencesStore/PreferencesEditor from datastoreCommon
            // Module, SeerrSecureCredentialsStore from desktopDatastore
            // Module). Nav v1 registers onboardingSection (reachable from
            // Shortcuts), and wave 21B added the first-run gate: the shell
            // pushes Route.Onboarding once per authenticated session while
            // the persisted onboarding_completed flag is unset (same pref
            // the Android app gates on; completion through the shared wizard
            // writes it, so the gate never re-fires).
            onboardingModule,

            // …arrqueue, conveyor feature — LIVE and wired in nav v1
            // (arrQueueSection in the rail): ArrRepository from
            // dataJvmModule and ExperimentalStore from
            // datastoreCommonModule resolve on desktop.
            arrqueueModule,

            // …auth, Phase X cutover (feature-conveyor transform from the
            // legacy :feature:auth): the entire ctor graph resolves on
            // desktop (AuthRepository + ServerDiscoveryRepository from
            // dataJvmModule, LocalNetworkStatus — the auth seam's fun-interface
            // probe in feature/auth (blames a connect failure on the Android
            // 17+ local-network permission), NOT core/ui's same-named
            // composition local — from the jvmMain platform pick below), so
            // this registration is live-resolvable — NOT
            // dormant-for-missing-deps. Wave 19A unified sign-in
            // on these shared screens: the signed-out gate
            // (DesktopSignedOutAuthHost) and the signed-in settings drill-ins
            // (DesktopAppRoot's authSection entries) both instantiate these
            // ViewModels; the legacy DesktopSignInPane pane is retired.
            authModule,
            desktopAuthPlatformModule,

            // …player-live, conveyor feature (wave 7B): documented-latent.
            // The shared live-player ViewModel + LastChannelStore resolve
            // (data/datastore graph), but the three platform seams (engine
            // factory, audio, transcode-reasons renderer) are
            // Android-only definitions and the player screen lives in the
            // module's androidMain — Route.LiveTvChannelPlayer stays
            // guarded in DesktopAppRoot, so nothing instantiates the VM on
            // desktop (same latent class as the player-adjacent features).
            playerLiveModule,



            // …details, Phase X cutover wave (legacy :feature:details was the
            // largest never-conveyor module): registration fully
            // live — every data-layer ctor dep is Koin-native
            // (dataJvmModule/datastoreCommonModule), AudioQueueFacade comes
            // from desktopPlayerModule's DefaultAudioQueueFacade binding
            // (wave 9B), and the module-local platform seams below supply
            // no-op audio/theme playback + the appdata storage probe.
            // DesktopAppRoot wires detailsSection behind every shared
            // screen that pushes a detail route (search results, requests/
            // calendar → SeerrDetail, person rows), so detail VMs
            // instantiate for real.
            detailsModule,
            desktopDetailsPlatformModule(paths.dataDirNio),
            // Home conveyor: LIVE since the wave 8B desktop wiring — the
            // four WorkManager/widget-backed HomeViewModel ctor deps
            // (PlaybackSyncScheduler, TvWatchNextScheduler,
            // ContinueWatchingBroadcaster, LibrarySyncHook) resolve to the
            // no-op desktop defs in desktopDataModule, and DesktopAppRoot
            // wires homeSection in the rail (HomeLifecycleSeam's jvm actual
            // stays a no-op, so sections refresh on their own flows rather
            // than a process start/stop signal).
            coreUiMessageModule,
            homeModule,
            // …player-audio, wave 7A conveyor (legacy :feature:player:audio
            // deleted): LIVE since wave 9B — desktopPlayerModule provides the
            // four playback/cast ctor deps (AudioQueueManager/
            // AudioEffectsManager/AudioPlayerEngine over the shared
            // DesktopAudioQueueManager single, plus the never-connected
            // DesktopAudioPlayerCast), DesktopAppRoot registers
            // audioPlayerSection and music track clicks navigate to
            // Route.AudioPlayer for real.
            playerAudioModule,




            // …subtitle-tester, the FINAL conveyor feature, deliberately has
            // NO registration here: the entire feature (ViewModel, screen,
            // preview engine host, raw-asset factory) lives in the shared
            // module's androidMain — its engine factory and font provider
            // actuals are Android-only (Koin-owned since wave 8) with no
            // desktop halves — so there is no
            // commonMain Koin module to register. The shared settings-search
            // row for Route.SubtitleTester stays unreachable on desktop
            // (LanguageSettings' push is intercepted by the guard); desktop's
            // navigateFilter intercepts the un-registered routes a shared
            // screen pushes TODAY — the guard list is hand-enumerated
            // (isDesktopDeadEndRoute in DesktopAppRoot) and must be kept
            // in sync when features gain or change pushed routes.

        )
    }

    // Wave 12A startup mark: Koin graph construction is the first heavy
    // milestone of boot (module list above is untouched — no reordering).
    startupPerf.markKoinStarted()

    // V3 downloads conveyor: the desktop download engine — in-process
    // supervisor observing PENDING rows (resume + reconnect edge handled
    // inside), plus the auto-download loop. Construction is side-effect free;
    // start() launches the loops on the application scope and is idempotent.
    koinApp.koin.get<DesktopDownloadManager>().start()
    koinApp.koin.get<DesktopAutoDownloadScheduler>().start()

    // Wave 9B real audio: the desktop audio core's app-lifetime kickoff —
    // restore the persisted queue/state and observe queue changes for Room
    // persistence (the Android Application.onCreate `manager.start()` twin;
    // equally safe off the cold-start critical path).
    koinApp.koin.get<com.raulshma.jellyplay.desktop.player.DesktopAudioQueueManager>().start()

    // Desktop image engine: an explicit OkHttp fetcher over the Koin-owned
    // base STREAMING client (same seam as the Android app's imageClient) —
    // the streaming client derives from the base client via newBuilder(), so
    // it shares the base sslSocketFactory/hostnameVerifier and the SAME
    // dynamic self-signed trust layer (grants read at handshake time). The
    // previous shape let coil-network-okhttp self-register via ServiceLoader
    // with its OWN default OkHttpClient, which would have kept failing the
    // TLS handshake against a self-signed server the user had granted —
    // every other surface would connect while artwork stayed broken. The
    // explicit registration below wins either way (first match loses to it),
    // but serviceLoaderEnabled(false) makes the exclusivity STRUCTURAL
    // instead of registration-order luck: the ServiceLoader factory can
    // never resurrect its default-client fetcher behind our back (wave-21
    // review round — it was dormant-first-match-loser; keep it that way by
    // construction). The lambda defers the Koin resolution to the first
    // image load (well after startKoin); crossfade stays the only other
    // tweak.
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it)
            .serviceLoaderEnabled(false)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            koinApp.koin.get<okhttp3.OkHttpClient>(NetworkQualifiers.streamingHttpClient)
                        },
                    ),
                )
            }
            .crossfade(true)
            .build()
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val showAbout = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        // Wave 12A: runtime icon for the title bar + tray (packaging icons are
        // NOT on the runtime classpath — see DesktopAppIcon). Null means the
        // resource was unreadable; the window then simply stays icon-less.
        val appIcon = remember { desktopAppIconOrNull() }
        // AWT-side window handle so the tray's Show action can restore/focus
        // the ComposeWindow from outside the Window content lambda.
        val windowRef = remember {
            java.util.concurrent.atomic.AtomicReference<ComposeWindow?>(null)
        }

        // Startup marks, wave 12A. windowShownNanos is the AWT-authoritative
        // visibility event. firstFrameNanos resumes when the frame clock
        // delivers the first frame after this root content applies its initial
        // composition — DesktopAppRoot composes inside this same pass, so it is
        // the same frame boundary without threading a callback through
        // DesktopAppRoot (≤1-frame slop vs a true "painted" hook; documented).
        LaunchedEffect(startupPerf) {
            withFrameNanos { /* resume at first produced frame */ }
            startupPerf.markFirstFrame(System.nanoTime())
        }

        Window(
            state = windowState,
            title = "JellyPlay",
            icon = appIcon,
            onCloseRequest = ::exitApplication,
        ) {
            DisposableEffect(startupPerf) {
                val composeWindow = window
                windowRef.set(composeWindow)
                val shownListener = object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(e: java.awt.event.WindowEvent?) {
                        startupPerf.markWindowShown()
                        composeWindow.removeWindowListener(this)
                    }
                }
                composeWindow.addWindowListener(shownListener)
                onDispose {
                    composeWindow.removeWindowListener(shownListener)
                    windowRef.compareAndSet(composeWindow, null)
                }
            }

            MenuBar {
                Menu("File") {
                    Item("Refresh", shortcut = KeyShortcut(Key.R, ctrl = true)) {
                        // Wired to home refresh when the V1c slice lands.
                    }
                    Separator()
                    Item("Exit", shortcut = KeyShortcut(Key.Q, ctrl = true)) {
                        exitApplication()
                    }
                }
                Menu("View") {
                    Item(
                        if (windowState.placement == WindowPlacement.Fullscreen) "Exit Fullscreen" else "Fullscreen",
                        shortcut = KeyShortcut(Key.F11),
                    ) {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                            else WindowPlacement.Fullscreen
                    }
                }
                Menu("Help") {
                    Item("About JellyPlay") { showAbout.value = true }
                }
            }

            JellyPlayTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                DesktopAppRoot(
                    showAbout = showAbout.value,
                    onDismissAbout = { showAbout.value = false },
                    previousCrashLogPath = previousCrash?.logFile?.toString(),
                    // Wave 13B session harness only (screenshots + key
                    // injection); unused on every normal boot path.
                    windowRef = windowRef,
                )
            }
        }

        // Wave 12A tray affordance. STRICTLY ADDITIVE semantics: closing the
        // window still quits (onCloseRequest above is unchanged) — there is no
        // hide-to-tray behavior here. Skipped entirely when the runtime icon
        // failed to load or AWT exposes no system tray (headless/locked-down
        // sessions; CMP's isTraySupported() is metadata-internal at 1.11.1, so
        // availability is probed via systemTrayAvailable(), see DesktopAppIcon).
        if (appIcon != null && systemTrayAvailable()) {
            Tray(
                icon = appIcon,
                tooltip = "JellyPlay",
                menu = {
                    Item("Show JellyPlay") {
                        // Tray item clicks come back through AWT menu
                        // machinery; compose desktop's own composition runs on
                        // that same AWT event thread, and this hop costs one
                        // loop turn while guaranteeing every future listener
                        // variant stays on-thread.
                        // Window restore/focus itself lives in DesktopTrayActions
                        // (wave 13A extraction — null path unit-covered; the
                        // visual restore still needs a one-time manual eyeball,
                        // see docs/perf notes + gate report).
                        java.awt.EventQueue.invokeLater {
                            windowState.isMinimized = false
                            DesktopTrayActions.showMainWindow(windowRef.get())
                        }
                    }
                    Item("Quit") { DesktopTrayActions.quit { exitApplication() } }
                },
            )
        }
    }
}
