package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
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

import com.raulshma.jellyplay.feature.onboarding.di.onboardingModule

import com.raulshma.jellyplay.feature.arrqueue.di.arrqueueModule


import org.koin.core.context.startKoin

fun main() {
    val paths = DesktopPaths.resolve()
    java.io.File(paths.dataDir.toString()).mkdirs()
    java.io.File(paths.configDir.toString()).mkdirs()

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
            // …music, third conveyor item — PARTIAL, nav v1 omits the whole
            // feature: the Albums/Artists/MusicBrowse/Genres/Playlists VMs
            // resolve, but the instant-mix cluster needs AudioQueueFacade,
            // which has no desktop definition yet (rides the desktop player
            // slice). The message-bus actual stays registered for the day it
            // lands.
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
            // AppUpdateRepository resolves from desktopDataModule).
            settingsModule,
            desktopSettingsPlatformModule(),
            // …admin, eighth conveyor item — LIVE since the same flip:
            // AdminRepository + AdminStatisticsRepository are Koin singles in
            // dataJvmModule on both platforms, and nav v1+ renders
            // adminSection in the rail (gated by the desktop admin-status
            // state in DesktopAppRoot). The Android-only plugin-config
            // WebView ViewModel lives in androidAdminModule and is never
            // registered here (the shared PluginConfigHost desktop actual
            // renders its "not available" fallback).
            adminModule,


            // …editor, ninth conveyor item — documented-latent, syncplay
            // pattern: the StreamingSubtitleStore dep (Hilt interop on
            // Android) has no desktop definition yet, but resolution is
            // lazy so boot stays safe, and the desktop shell has no editor
            // nav entry (grep confirms no route/screen reference), so the
            // module is registered but never instantiated. The desktop
            // file-picker actuals return null, so even a future editor
            // screen would degrade to URL-only uploads, not crash.
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
            // null actual keeps the share button hidden.
            insightsModule,





            // …onboarding, conveyor feature — fully live registration
            // (calendar/requests/shortcuts class): all four wizard VM deps
            // resolve on desktop (PreferenceProjections/
            // SeerrPreferencesStore/PreferencesEditor from datastoreCommon
            // Module, SeerrSecureCredentialsStore from desktopDatastore
            // Module). Nav v1 registers onboardingSection (reachable from
            // Shortcuts); a desktop first-run gate is still future work —
            // the wizard is auto-gated by the Android app and TV
            // auto-completes.
            onboardingModule,

            // …arrqueue, conveyor feature — LIVE and wired in nav v1
            // (arrQueueSection in the rail): ArrRepository from
            // dataJvmModule and ExperimentalStore from
            // datastoreCommonModule resolve on desktop.
            arrqueueModule,




            // …subtitle-tester, the FINAL conveyor feature, deliberately has
            // NO registration here: the entire feature (ViewModel, screen,
            // preview engine host, raw-asset factory) lives in the shared
            // module's androidMain — its engine factory and font provider are
            // Android/Hilt types with no desktop halves — so there is no
            // commonMain Koin module to register. The shared settings-search
            // row for Route.SubtitleTester stays unreachable on desktop
            // (LanguageSettings' push is intercepted by the guard); desktop's
            // navigateFilter intercepts the un-registered routes a shared
            // screen pushes TODAY — the guard list is hand-enumerated
            // (isDesktopDeadEndRoute in DesktopAppRoot) and must be kept
            // in sync when features gain or change pushed routes.

        )
    }

    // V3 downloads conveyor: the desktop download engine — in-process
    // supervisor observing PENDING rows (resume + reconnect edge handled
    // inside), plus the auto-download loop. Construction is side-effect free;
    // start() launches the loops on the application scope and is idempotent.
    koinApp.koin.get<DesktopDownloadManager>().start()
    koinApp.koin.get<DesktopAutoDownloadScheduler>().start()

    // Desktop image engine: the OkHttp network fetcher self-registers via
    // ServiceLoader from the coil-network-okhttp dependency; crossfade is the
    // only tweak needed on top.
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it).crossfade(true).build()
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val showAbout = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        Window(
            state = windowState,
            title = "JellyPlay",
            onCloseRequest = ::exitApplication,
        ) {
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
                DesktopAppRoot(showAbout = showAbout.value, onDismissAbout = { showAbout.value = false })
            }
        }
    }
}
